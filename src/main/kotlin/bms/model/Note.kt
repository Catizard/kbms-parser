package bms.model

abstract class Note(
    var section: Double = 0.0,
    var _time: Long = 0,
    var wav: Int = 0,
    val _start: Long = 0,
    val duration: Long = 0,
    var state: Int = 0,
    var _playTime: Long = 0,
    val layeredNotes: List<Note> = listOf(),
) : Cloneable {
    var microTime: Long
        get() = _time
        set(value) {
            this._time = value
        }
    val milliTime: Long
        get() = _time / 1000

    fun getTime(): Int = milliTime.toInt()

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

    val playTime: Int
        get() = milliPlayTime.toInt()
}

class LongNote @JvmOverloads constructor(
    wav: Int,
    start: Long,
    duration: Long,
    var type: LongNoteDef = LongNoteDef.UNDEFINED
) : Note(wav = wav, _start = start, duration = duration), Cloneable {
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

    fun isEnd(): Boolean = end

    constructor(wav: Int, type: LongNoteDef = LongNoteDef.UNDEFINED) : this(wav, 0, 0, type)
}

class NormalNote @JvmOverloads constructor(
    wav: Int,
    start: Long = 0,
    duration: Long = 0,
) : Note(
    wav = wav,
    _start = start,
    duration = duration
), Cloneable

class MineNote(
    wav: Int,
    val damage: Double,
) : Note(wav = wav), Cloneable