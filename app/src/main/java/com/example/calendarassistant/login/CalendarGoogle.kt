package com.example.calendarassistant.login
import android.accounts.Account
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException

import kotlinx.coroutines.*

const private val TAG= "CalendarGoogle"


class CalendarGoogle(private val context: Context, private val accountName: String) {
    companion object {
        private const val APPLICATION_NAME = "Google Calendar API Kotlin Android Quickstart"
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
        private val SCOPES = listOf(CalendarScopes.CALENDAR_READONLY)
    }

    private val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(
        context, SCOPES
    ).setSelectedAccount(Account(accountName, "com.example.calendarassistant"))  //note that .setSelectedAccountName(accountName) will NOT work. We need to use setSelectedAccount instead. The "type" is set to package-name.

    private val calendarService: Calendar by lazy {
        Calendar.Builder(
            AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential
        ).setApplicationName(APPLICATION_NAME)
            .build()
    }

    fun getUpcomingEvents() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = com.google.api.client.util.DateTime(System.currentTimeMillis())
                val events = calendarService.events().list("primary")
                    .setMaxResults(10)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()
                val items = events.items

                withContext(Dispatchers.Main) {
                    if (items.isEmpty()) {
                        Log.d(TAG, "no events present")
                        // Update UI to show no events
                    } else {
                        Log.d(TAG, "found events")
                        // Update UI to show events
                        for (event in items) {
                            val start = event.start.dateTime ?: event.start.date
                            // Update UI with event details
                        }
                    }
                }
            }catch (e: UserRecoverableAuthIOException) { //user needs to accept that the app will have access to users calendar. This exception-handling is absolutely necessary for retreiving events in the app.
                Log.d(TAG, "exception occured: " + e)
                // Handle the exception by starting an activity with the provided intent
                context.startActivity(e.intent) //
            }
            catch (e: Exception) {
                Log.d(TAG, "exception occured: " + e)
                // Handle the exception in the UI thread
                withContext(Dispatchers.Main) {
                    // Show error message
                }
            }
        }
    }
}
