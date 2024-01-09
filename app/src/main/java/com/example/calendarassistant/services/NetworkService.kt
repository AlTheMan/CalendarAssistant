package com.example.calendarassistant.services

import android.util.Log
import androidx.compose.material.icons.materialIcon
import com.example.calendarassistant.enums.TravelMode
import com.example.calendarassistant.model.mock.calendar.MockCalendarEvent
import com.example.calendarassistant.model.mock.calendar.MockEvent
import com.example.calendarassistant.model.mock.travel.Deviation
import com.example.calendarassistant.model.mock.travel.DeviationInformation
import com.example.calendarassistant.model.mock.travel.MockDeviationInformation
import com.example.calendarassistant.model.mock.travel.MockTravelInformation
import com.example.calendarassistant.model.mock.travel.TransitDeviationInformation
import com.example.calendarassistant.model.mock.travel.TravelInformation
import com.example.calendarassistant.network.GoogleApi
import com.example.calendarassistant.network.SlLookUpApi
import com.example.calendarassistant.network.SlRealTimeApi
import com.example.calendarassistant.network.dto.google.directions.GoogleDirectionsResponse
import com.example.calendarassistant.network.dto.google.directions.internal.DepartureTime
import com.example.calendarassistant.network.dto.google.directions.internal.EndLocation
import com.example.calendarassistant.network.dto.google.directions.internal.Steps
import com.example.calendarassistant.network.dto.sl.realtimeData.SlRealtimeDataResponse
import com.example.calendarassistant.network.dto.sl.realtimeData.internal.Deviations
import com.example.calendarassistant.network.location.LocationRepository
import com.example.calendarassistant.ui.viewmodels.UiState
import com.example.calendarassistant.utilities.DateHelpers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private const val TAG = "NetworkService"

interface INetworkService {
    class NetworkException(message: String) : Exception()

//    val transitSteps: StateFlow<List<Steps>>

    suspend fun getTravelInformation(travelMode: TravelMode)
    suspend fun getDeviationInformation()
}

class NetworkService : INetworkService {

//    private val _transitSteps = MutableStateFlow<List<Steps>>(emptyList())
//    override val transitSteps: StateFlow<List<Steps>>
//        get() = _transitSteps.asStateFlow()

    private var transitSteps: MutableList<Steps> = mutableListOf()
    private var transitStepsDeviations: MutableList<DeviationInformation> = mutableListOf()

    /**
     *  Makes api call to Google directions and updates the next event info
     */
    override suspend fun getTravelInformation(travelMode: TravelMode) {
        try {
            val lastLocationCoordinates = getLocationOrThrow()

            // Gets the next event happening from the mock calendar
            val nextEvent = getNextCalendarEvent()

            // Sets arrival time for google directions to the event start-time
            val arrivalTime = getArrivalTime(nextEvent.start)

            // Google api-call
            val response = fetchGoogleDirections(
                arrivalTime,
                lastLocationCoordinates,
                nextEvent.location,
                travelMode
            )

            processGoogleDirectionsResponse(response)

        } catch (e: Exception) {
            Log.d(TAG, e.printStackTrace().toString())
            Log.e(TAG, "Error in getTravelInformation: ${e.message}")
        }
    }

    private fun getLocationOrThrow(): Pair<String, String> {
        return LocationRepository.getLastLocation()
            ?: throw INetworkService.NetworkException("Location not found")
    }

    private fun getNextCalendarEvent(): MockCalendarEvent {
        return MockEvent.getMockEvents().first()
    }

    private fun getArrivalTime(time: String): Long {
        return ZonedDateTime.parse(time).toEpochSecond() // TODO: vad gör denna, när påverkar den tiden?
    }

    private suspend fun fetchGoogleDirections(
        arrivalTime: Long,
        origin: Pair<String, String>,
        destination: String,
        mode: TravelMode
    ): GoogleDirectionsResponse {
        val response = GoogleApi.getDirectionsByCoordinatesOriginDestinationPlace(
            arrivalTime = arrivalTime,
            origin = origin,
            destination = destination,
            mode = mode
        )
        if (!response.isSuccessful) {
            throw INetworkService.NetworkException("Unsuccessful network call to Google Maps api")
        }
        return response.body()!!
    }

    private suspend fun processGoogleDirectionsResponse(response: GoogleDirectionsResponse) {
        val legs = response.routes.first().legs.first()

        Log.d(TAG, legs.toString())

        transitStepsDeviations.clear()
        transitSteps.clear()
        // Collects steps where transit
        transitSteps.addAll(extractTransitSteps(legs.steps))

        val departureInformation = legs.departureTime
        val endLocation = legs.endLocation
        updateTravelInformation(departureInformation, endLocation)
    }

    // Filters and returns a list of steps that involve transit
    private fun extractTransitSteps(steps: List<Steps>): List<Steps> =
        steps.filter { it.travelMode == "TRANSIT" }

    private suspend fun updateTravelInformation(departureTime: DepartureTime?, endLocation: EndLocation?) {
        val departureTimeHHMM = calculateDepartureTimeHHMM(departureTime)
        val departureTimeText = departureTime?.text

        // Updates set new info, which updates the ui
        MockTravelInformation.setTravelInformation(
            departureTimeHHMM,
            departureTimeText,
            Pair(endLocation?.lat, endLocation?.lng),
            transitSteps
        )
    }

    private fun calculateDepartureTimeHHMM(departureTime: DepartureTime?): String {
        // Calculate the time to leave (eg: "1h15m")
        val currentTime = ZonedDateTime.now().toEpochSecond()
        return departureTime?.value?.let {
            DateHelpers.formatSecondsToHourMinutes(it - currentTime)
        } ?: ""
    }

// TODO ########################################################################################
    // TODO: Eventuellt dela upp i två klasser?

    /**
     *  Makes api call to SL api and updates the delay and deviation info for the next event
     */
    override suspend fun getDeviationInformation() {
        try {
            transitSteps.map { step ->
                val realTimeData = fetchRealTimeDataForStep(step)
                transitStepsDeviations.add(compareStepWithRealTimeData(step, realTimeData))
            }

            MockDeviationInformation.setTransitDeviationInformation(
                transitStepsDeviations = transitStepsDeviations
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching real-time transit data: ${e.message}")
        }
    }

    private suspend fun fetchRealTimeDataForStep(step: Steps): SlRealtimeDataResponse {
        // Fetch real-time data from SL API using the step information

        val stationName = step.transitDetails?.departureStop?.name
            ?: return SlRealtimeDataResponse()

        val siteIdResponse = SlLookUpApi.getSiteIdByStationName(stationName)
        if (!siteIdResponse.isSuccessful) {
            throw INetworkService.NetworkException("Unsuccessful network call to SL LookUp api")
        }

        val siteId = siteIdResponse.body()?.responseData?.firstOrNull()?.siteId
            ?: return SlRealtimeDataResponse()

        val realTimeDataResponse = SlRealTimeApi.getRealtimeDataBySiteId(siteId)
        if (!realTimeDataResponse.isSuccessful) {
            throw INetworkService.NetworkException("Unsuccessful network call to SL RealTime api")
        }

        return realTimeDataResponse.body() ?: SlRealtimeDataResponse()
    }

    private fun compareStepWithRealTimeData(
        step: Steps,
        realTimeData: SlRealtimeDataResponse
    ): DeviationInformation {

        val transportMode = step.transitDetails?.line?.vehicle?.type
            ?: return DeviationInformation(0, emptyList())
        val lineShortName = step.transitDetails?.line?.shortName
        val headSign = step.transitDetails?.headsign
        val scheduledDepartureTime = step.transitDetails?.departureTime?.value

        return findRealTimeData(
            transportMode,
            lineShortName,
            headSign,
            scheduledDepartureTime,
            realTimeData
        )
    }

    private fun findRealTimeData(
        transportMode: String,
        lineShortName: String?,
        headSign: String?,
        scheduledDepartureTime: Int?,
        realTimeData: SlRealtimeDataResponse
    ): DeviationInformation {

        Log.d(TAG, realTimeData.toString())

        val realDepartureTime: String?
        val deviations: List<Deviation>

        /**
         * Ensures that the line number, scheduled departure time, ... from the Google Directions API matches
         * with the line number, ..., ... in the SL Real-Time API.
         */
        when (transportMode) {
            "BUS" -> {
                val busData = realTimeData.responseData?.buses?.find {
                    ((((it.lineNumber == lineShortName) && (it.destination == headSign)
                            && (areTimesSimilar(it.timeTabledDateTime.toString(),
                        formatSecondsToDateTimeString(scheduledDepartureTime))))))
                }
                realDepartureTime = busData?.expectedDateTime
                deviations = convertDeviations(busData?.deviations)
            }
            "SUBWAY" -> {
                val metroData = realTimeData.responseData?.metros?.find {
                    ((((it.lineNumber == lineShortName) && (it.destination == headSign)
                            && (areTimesSimilar(it.timeTabledDateTime.toString(),
                                formatSecondsToDateTimeString(scheduledDepartureTime))))))
                }
                realDepartureTime = metroData?.expectedDateTime
                deviations = convertDeviations(metroData?.deviations)
            }
            "TRAIN" -> {
                val trainData = realTimeData.responseData?.trains?.find {
                    ((((it.lineNumber == lineShortName) && (it.destination == headSign)
                            && (areTimesSimilar(it.timeTabledDateTime.toString(),
                        formatSecondsToDateTimeString(scheduledDepartureTime))))))
                }
                realDepartureTime = trainData?.expectedDateTime
                deviations = convertDeviations(trainData?.deviations)
            }
            "TRAM" -> {
                val tramData = realTimeData.responseData?.trams?.find {
                    ((((it.lineNumber == lineShortName) && (it.destination == headSign)
                            && (areTimesSimilar(it.timeTabledDateTime.toString(),
                        formatSecondsToDateTimeString(scheduledDepartureTime))))))
                }
                realDepartureTime = tramData?.expectedDateTime
                deviations = convertDeviations(tramData?.deviations)
            }
            "SHIP" -> {
                val shipData = realTimeData.responseData?.ships?.find {
                    ((((it.lineNumber == lineShortName) && (it.destination == headSign)
                            && (areTimesSimilar(it.timeTabledDateTime.toString(),
                        formatSecondsToDateTimeString(scheduledDepartureTime))))))
                }
                realDepartureTime = shipData?.expectedDateTime
                deviations = convertDeviations(shipData?.deviations)
            }
            else -> {
                realDepartureTime = null
                deviations = emptyList()
            }
        }

        val delayInMinutes = calculateDelay(scheduledDepartureTime, realDepartureTime) //TODO: eller 'slScheduledDepartureTime = shipData?.timeTabledDateTime'
        return DeviationInformation(delayInMinutes, deviations)
    }

    fun areTimesSimilar(time1: String, time2: String): Boolean { // TODO: Är Googles och SLs planerade tider lika även fast vi hämtar ny data från google (som då är uppdaterad)?
        // Example using java.time API (requires API level 26 or using a backport library like ThreeTenABP)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val dateTime1 = LocalDateTime.parse(time1, formatter)
        val dateTime2 = LocalDateTime.parse(time2, formatter)

        return Duration.between(dateTime1, dateTime2).abs().toMinutes() <= 1 // for a 1-minute threshold
    }

    private fun convertDeviations(slDeviations: ArrayList<Deviations>?): List<Deviation> {
        return slDeviations?.map {
            Deviation(
                it.text ?: "",
                it.consequence ?: "",
                it.importanceLevel ?: 0
            )
        } ?: emptyList()
    }

    private fun calculateDelay(scheduledTime: Int?, actualTime: String?): Int {
        if (scheduledTime == null || actualTime == null) return 0

        // Parse the actual time (expectedDateTime) to a Unix timestamp
        val actualDateTime = formatDateTimeStringToUnix(actualTime)

        // Calculate the delay in seconds
        val delayInSeconds = (actualDateTime) - scheduledTime
        // Convert the delay to minutes
        return (delayInSeconds / 60).toInt() + 2 //TODO: TA BORT MOCK DELAY !!!!!
    }

    private fun formatSecondsToDateTimeString(seconds: Int?): String { //TODO: flytta till utilities
        val instant = seconds?.let { Instant.ofEpochSecond(it.toLong()) }
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        return localDateTime.format(formatter)
    }

    private fun formatDateTimeStringToUnix(timestamp: String?): Long { //TODO: flytta till utilities
        Log.d(TAG, "formatDateTimeStringToUnix:  $timestamp")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        return try {
            LocalDateTime.parse(timestamp, formatter).atZone(ZoneId.systemDefault()).toEpochSecond()
        } catch (e: DateTimeParseException) {
            Log.e(TAG, "Error parsing timestamp from Date Time String to Unix: ${e.message}")
            return 0
        }
    }

}