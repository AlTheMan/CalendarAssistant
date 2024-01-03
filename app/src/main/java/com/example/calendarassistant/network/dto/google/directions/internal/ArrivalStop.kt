package com.example.calendarassistant.network.dto.google.directions.internal
import com.google.gson.annotations.SerializedName


data class ArrivalStop (

  @SerializedName("location" ) var location : Location? = Location(),
  @SerializedName("name"     ) var name     : String?   = null

)