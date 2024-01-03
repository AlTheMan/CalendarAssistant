package com.example.calendarassistant.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendarassistant.enums.TravelMode
import com.example.calendarassistant.network.GoogleApi
import kotlinx.coroutines.launch

private const val TAG = "TestVm"

class TestVM() : ViewModel() {

    fun getDirections() {
        viewModelScope.launch {
            val response = GoogleApi.getDirectionsByPlace("Stockholm", "Nyköping", TravelMode.Transit)
            Log.d(TAG, response.body().toString())
        }
    }
}