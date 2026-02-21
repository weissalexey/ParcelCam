package com.carstensen.parcelcam.data

enum class MaxResolution(val longSidePx: Int?) {
    ORIGINAL(null),
    PX_2048(2048),
    PX_1600(1600),
    PX_1280(1280);

    companion object {
        fun fromLabel(label: String): MaxResolution {
            return when (label.trim()) {
                "Original" -> ORIGINAL
                "2048" -> PX_2048
                "1600" -> PX_1600
                "1280" -> PX_1280
                else -> PX_2048
            }
        }

        fun toLabel(v: MaxResolution): String =
            when (v) {
                ORIGINAL -> "Original"
                PX_2048 -> "2048"
                PX_1600 -> "1600"
                PX_1280 -> "1280"
            }
    }
}
