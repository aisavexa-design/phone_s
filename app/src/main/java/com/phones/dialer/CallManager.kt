package com.phones.dialer

import android.telecom.Call
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Suppress("DEPRECATION")
object CallManager {
    var call by mutableStateOf<Call?>(null)
        private set
    var state by mutableStateOf(Call.STATE_NEW)
        private set

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            this@CallManager.state = state
        }
    }

    fun add(c: Call) {
        call = c
        state = c.state
        c.registerCallback(callback)
    }

    fun remove(c: Call) {
        c.unregisterCallback(callback)
        if (call == c) call = null
        state = Call.STATE_DISCONNECTED
    }

    fun answer() { call?.answer(VideoProfile.STATE_AUDIO_ONLY) }

    fun hangup() {
        val c = call ?: return
        if (c.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }
}
