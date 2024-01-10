package com.example.calendarassistant.model.mock.travel

import com.example.calendarassistant.network.dto.google.directions.internal.Steps
import com.example.calendarassistant.utilities.TimeToLeaveDisplay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow


object MockTravelInformation {

    private val _transitSteps = MutableStateFlow<List<Steps>>(listOf())
    var transitSteps: StateFlow<List<Steps>> = _transitSteps

    private var travelInformation = MutableSharedFlow<TravelInformation>()

    suspend fun setTravelInformation(
        departureTimeHHMM: TimeToLeaveDisplay,
        departureTime: String?,
        endLocation: Pair<Double?, Double?>,
        transitSteps: List<Steps>,
    ) {
        travelInformation.emit(
            TravelInformation(
                departureTime = departureTime,
                departureTimeHHMM = departureTimeHHMM,
                destinationCoordinates = endLocation
            )
        )

        _transitSteps.value = transitSteps
    }

    //TODO: funktionsnamn till "getNextEventTravelInformation" ?
    fun getNextEventTravelInformation() : SharedFlow<TravelInformation> = travelInformation.asSharedFlow()
}

data class TravelInformation (
    var departureTimeHHMM: TimeToLeaveDisplay = TimeToLeaveDisplay(),
    var departureTime: String? = "",
    var destinationCoordinates: Pair<Double?, Double?> = Pair(null, null),
)

