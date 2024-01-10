package com.example.calendarassistant.ui.viewmodels

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendarassistant.login.GoogleAuthClient
import com.example.calendarassistant.login.SignInResult
import com.example.calendarassistant.login.SignInState
import com.example.calendarassistant.services.CalendarService
import com.example.calendarassistant.utilities.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SettingsVm"

@HiltViewModel
class SettingsVM @Inject constructor(
    private val googleAuthClient: GoogleAuthClient,
    private val calendarService: CalendarService,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _signInState = MutableStateFlow(SignInState())
    val signInState = _signInState.asStateFlow()

    private val _signInIntentSender = MutableSharedFlow<IntentSender?>(replay = 1)
    val signInIntentSender: SharedFlow<IntentSender?> = _signInIntentSender.asSharedFlow()

    fun showNotification(title: String, contentText: String) {
        notificationHelper.showNotification(title, contentText)
    }

    fun signIn() {
        viewModelScope.launch {
            val intentSender = googleAuthClient.signIn()
            _signInIntentSender.emit(intentSender)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            googleAuthClient.signOut()
            calendarService.clearEvents()
            resetState()
        }
    }

    fun isUserSignedIn(): Boolean {
        return googleAuthClient.getSignedInUser() != null
    }

    fun handleSignInResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            if (resultCode == RESULT_OK && data != null) {
                val signInResult = googleAuthClient.getSignInResultFromIntent(data)
                Log.d(TAG, signInResult.data.toString())
                calendarService.getUpcomingEventsForOneWeek()
                _signInState.value = SignInState(isSignInSuccessful = true, signInError = "Successfully signed in")
            } else {
                Log.d(TAG, "Error signing in, Result code: " + resultCode.toString() + " " + data.toString())
            }
        }
    }

    fun resetState() {
        _signInState.update { SignInState() }
    }
}