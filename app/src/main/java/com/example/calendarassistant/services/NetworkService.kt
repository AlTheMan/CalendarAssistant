package com.example.calendarassistant.services

import android.util.Log
import com.example.calendarassistant.enums.TravelMode
import com.example.calendarassistant.model.mock.calendar.MockEvent
import com.example.calendarassistant.model.mock.travel.MockTravelInformation
import com.example.calendarassistant.network.GoogleApi
import com.example.calendarassistant.network.dto.google.directions.internal.Steps
import com.example.calendarassistant.network.location.LocationRepository
import com.example.calendarassistant.utilities.DateHelpers
import java.time.ZonedDateTime

private const val TAG = "NetworkService"

class NetworkService : INetworkService {


    /**
     *  Makes api call to Google directions and updates the next event info
     */
    override suspend fun getTravelInformation(travelMode: TravelMode) {
        try {
            val lastLocationCoordinates = LocationRepository.getLastLocation() ?: throw INetworkService.NetworkException("Location not found")
            // Gets the next event happening from the mock
            val nextEvent = MockEvent.getMockEvents().first()
            // Sets arrival time for google directions to the event start-time
            val arrivalTime = ZonedDateTime.parse(nextEvent.start).toEpochSecond()
            // Google api-call
            val response = GoogleApi.getDirectionsByCoordinatesOriginDestinationPlace(
                arrivalTime = arrivalTime,
                origin = lastLocationCoordinates,
                destination = nextEvent.location,
                mode = travelMode
            )
            if (!response.isSuccessful) {
                throw INetworkService.NetworkException("Unsuccessful network call to Google Maps api")
            }

            val legs = response.body()!!.routes.first().legs.first()
            val steps = legs.steps

            // Collects steps where transit
            // TODO: use this list to make api calls for traffic events (delays)
            val transitSteps: MutableList<Steps> = mutableListOf()
            for (element in steps) {
                if (element.travelMode == "TRANSIT") {
                    transitSteps.add(element)
                }
            }

            // Contains time of departure in text and unix time
            val departureInformation = legs.departureTime
            // Contains coordinates for end location (used for opening google maps from ui)
            val endLocation = legs.endLocation

            if (departureInformation != null) {
                val departureTime = departureInformation.text

                // Calculate the time to leave (eg: "1h15m")
                val currentTime = ZonedDateTime.now().toEpochSecond()
                val departureTimeHHMM = DateHelpers.formatSecondsToHourMinutes(
                    departureInformation.value?.minus(
                        currentTime
                    )
                )
                // Updates set new info, which updates the ui
                MockTravelInformation.setTravelInformation(
                    departureTimeHHMM, departureTime, Pair(endLocation?.lat, endLocation?.lng), transitSteps = transitSteps
                )


            }
        } catch (e: Exception) {
            Log.d(TAG, e.printStackTrace().toString())
        }
    }

}

interface INetworkService {
    class NetworkException(message: String) : Exception()

    suspend fun getTravelInformation(travelMode: TravelMode)

}