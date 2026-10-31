package io.github.catizard.kbms.parser.bmson

import kotlinx.serialization.Serializable


/**
 * Base class of all the bmson objects which are placed at some position `y`.
 *
 * NOTE: `y` used to be declared here as `val y: Int = 0`. kotlinx.serialization only
 * deserializes primary-constructor properties, so every subclass silently got `y = 0`
 * for *all* of its entries (notes/BPM/stop/scroll/BGA events were all stuck at position 0).
 * It must therefore live in the primary constructor of the concrete (serializable) classes.
 */
abstract class BMSONObject : Comparable<BMSONObject> {
    abstract val y: Int

    override fun compareTo(other: BMSONObject): Int {
        return y - other.y
    }
}

@Serializable
data class Note(
    override val y: Int = 0,
    val x: Int = 0,
    val l: Int = 0,
    /**
     * Whether to resume playback from where the sound source was last played
     */
    val c: Boolean = false,
    val t: Int = 0,
    val up: Boolean = false,
) : BMSONObject()

@Serializable
data class MineNote(
    override val y: Int = 0,
    val x: Int,
    val damage: Double
) : BMSONObject()

@Serializable
data class BarLine(
    val y: Int = 0,
    val k: Int = 0
)

@Serializable
data class BpmEvent(
    override val y: Int = 0,
    val bpm: Double
) : BMSONObject()

@Serializable
data class StopEvent(
    override val y: Int = 0,
    val duration: Long
) : BMSONObject()

@Serializable
data class ScrollEvent(
    override val y: Int = 0,
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