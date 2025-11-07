package com.example.wear.golfcheck

import com.example.wear.golfcheck.data.GolfShotEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class GolfExerciseService {

    private val _golfShotEventFlow = MutableStateFlow<GolfShotEvent?>(null)
    val golfShotEvents: StateFlow<GolfShotEvent?> = _golfShotEventFlow

    fun onNewGolfShotEvent(event: GolfShotEvent) {
        _golfShotEventFlow.value = event
    }

    fun markGolfShotEvent(swingType: GolfShotEvent.GolfShotSwingType) {
        val golfShotEvent = GolfShotEvent(swingType)
        onNewGolfShotEvent(golfShotEvent)
    }
}
