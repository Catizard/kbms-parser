package bms.model.note

import bms.model.LongNoteDef

abstract class Note(
    var section: Double = 0.0,
    var _time: Long = 0,
    val wav: Int = 0,
    val _start: Long = 0,
    val duration: Long = 0,
    val state: Int = 0,
    var _playTime: Long = 0,
    val layeredNotes: List<Note> = listOf(),
) {
    var microTime: Long
        get() = _time
        set(value) {
            this._time = value
        }
    val milliTime: Long
        get() = _time / 1000

    val microStart: Long
        get() = _start

    val milliStart: Long
        get() = _start / 1000

    var microPlayTime: Long
        get() = _playTime
        set(value) {
            this._playTime = value
        }

    val milliPlayTime: Long
        get() = _playTime / 1000
}

class LongNote(
    wav: Int,
    start: Long,
    duration: Long,
    var type: LongNoteDef = LongNoteDef.UNDEFINED
) : Note(wav = wav, _start = start, duration = duration) {
    var end: Boolean = false
    var pair: LongNote? = null
        private set

    fun connectPair(other: LongNote?) {
        other ?: return
        other.pair = this
        this.pair = other

        other.end = other.section > this.section
        this.end = !other.end

        val mergingType = if (this.type != LongNoteDef.UNDEFINED) this.type else other.type
        this.type = mergingType
        other.type = mergingType
    }

    constructor(wav: Int, type: LongNoteDef = LongNoteDef.UNDEFINED) : this(wav, 0, 0, type)
}

class NormalNote(
    wav: Int,
    start: Long = 0,
    duration: Long = 0,
) : Note(
    wav = wav,
    _start = start,
    duration = duration
)

class MineNote(
    wav: Int,
    val damage: Double,
) : Note(wav = wav)