package io.github.catizard.kbms.parser.bmson

import kotlinx.serialization.Serializable

@Serializable
open class BMSONObject : Comparable<BMSONObject> {
    val y: Int = 0

    override fun compareTo(other: BMSONObject): Int {
        return y - other.y
    }
}

@Serializable
data class Note(
    val x: Int,
    val l: Int,
    /**
     * Whether to resume playback from where the sound source was last played
     */
    val c: Boolean,
    val t: Int,
    val up: Boolean = false,
) : BMSONObject()

@Serializable
data class MineNote(
    val x: Int,
    val damage: Double
) : BMSONObject()

@Serializable
data class BarLine(
    val y: Int,
    val k: Int
)

@Serializable
data class BpmEvent(
    val bpm: Double
) : BMSONObject()

@Serializable
data class StopEvent(
    val duration: Long
) : BMSONObject()

@Serializable
data class ScrollEvent(
    val rate: Double = 1.0
) : BMSONObject()

@Serializable
data class SoundChannel(
    val name: String,
    val notes: Array<Note> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SoundChannel

        if (name != other.name) return false
        if (!notes.contentEquals(other.notes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + notes.contentHashCode()
        return result
    }
}

@Serializable
data class MineChannel(
    val name: String,
    val notes: Array<MineNote> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MineChannel

        if (name != other.name) return false
        if (!notes.contentEquals(other.notes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + notes.contentHashCode()
        return result
    }
}