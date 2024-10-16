package com.github.p1k0chu.mcmod.bac_tracker.data

import java.time.Instant

class AdvancementData(
    var done: Boolean = false,
    var player: String? = null,
    var doneTime: Instant? = null,
    val index: Int,
    var progress: Progress? = null
) {
    class Progress(nominator: Int, denominator: Int) {
        var nom: Int = nominator
        var den: Int = denominator

        fun doubleValue(): Double {
            return nom.toDouble() / den
        }

        override fun toString(): String {
            return "$nom / $den"
        }

        operator fun compareTo(other: Progress): Int {
            return this.doubleValue().compareTo(other.doubleValue())
        }
    }
}