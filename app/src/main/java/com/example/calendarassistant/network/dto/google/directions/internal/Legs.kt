package com.example.calendarassistant.network.dto.google.directions.internal

import com.google.gson.annotations.SerializedName


data class Legs (

    @SerializedName("arrival_time"        ) var arrivalTime       : ArrivalTime?      = ArrivalTime(),
    @SerializedName("departure_time"      ) var departureTime     : DepartureTime?    = DepartureTime(),
    @SerializedName("distance"            ) var distance          : Distance?         = Distance(),
    @SerializedName("duration"            ) var duration          : Duration?         = Duration(),
    @SerializedName("end_address"         ) var endAddress        : String?           = null,
    @SerializedName("end_location"        ) var endLocation       : EndLocation?      = EndLocation(),
    @SerializedName("start_address"       ) var startAddress      : String?           = null,
    @SerializedName("start_location"      ) var startLocation     : StartLocation?    = StartLocation(),
    @SerializedName("steps"               ) var steps             : ArrayList<Steps>  = arrayListOf(),
    @SerializedName("traffic_speed_entry" ) var trafficSpeedEntry : ArrayList<String> = arrayListOf(),
    @SerializedName("via_waypoint"        ) var viaWaypoint       : ArrayList<String> = arrayListOf()

)