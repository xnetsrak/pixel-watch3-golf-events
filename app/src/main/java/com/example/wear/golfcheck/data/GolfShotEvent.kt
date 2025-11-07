package com.example.wear.golfcheck.data

class GolfShotEvent(
    val swingType: GolfShotSwingType
) {
    enum class GolfShotSwingType {
        UNKNOWN,
        PARTIAL,
        FULL,
        PUTT
    }
}
