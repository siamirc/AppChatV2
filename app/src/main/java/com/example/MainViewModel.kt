package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val ircClient = IrcClient(application)
    val radioPlayer = RadioPlayerManager(application)

    init {
        // We can set default states or prepare anything if needed
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up when view model is destroyed
        ircClient.disconnect()
        radioPlayer.stop()
    }
}
