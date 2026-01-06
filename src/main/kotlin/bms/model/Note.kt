package bms.model

import kotlinx.serialization.Serializable

@Serializable
abstract class Note(
    var section: Double = 0.0,
    var _time: Long = 0,
    var state: Int = 0,
    var _playTime: Long = 0,
    val layeredNotes: MutableList<Note> = mutableListOf(),
) : Cloneable {
    abstract var wav: Int
    abstract var _start: Long
    abstract var duration: Long

    var microTime: Long
        get() = _time
        set(value) {
            this._time = value
        }
    val milliTime: Long
        get() = _time / 1000

    fun getTime(): Int = milliTime.toInt()

    var microStart: Long
        get() = _start
        set(value) {
            this._start = value
        }

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

    fun addLayeredNote(note: Note?) {
        note ?: return
        note.section = section
        note.microTime = microTime
        layeredNotes.add(note)
    }
}

class LongNote @JvmOverloads constructor(
    override var wav: Int,
    override var _start: Long,
    override var duration: Long,
    var type: LongNoteDef = LongNoteDef.UNDEFINED
) : Note(), Cloneable {
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

@Serializable
class NormalNote @JvmOverloads constructor(
    override var wav: Int,
    override val _start: Long = 0,
    override val duration: Long = 0,
) : Note(), Cloneable

@Serializable
class MineNote @JvmOverloads constructor(
    override var wav: Int,
    override val _start: Long = 0,
    override val duration: Long = 0,
    val damage: Double,
) : Note(), Cloneable