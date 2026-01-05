package bms.model

class Timeline(
    // section
    // Time, unit is microsecond
    private var _time: Long,
    // TODO: Upstream's setSection has different behavior
    var section: Double,
    var notes: Array<Note?>,
    var hiddenNotes: Array<Note?>,
    val bgNotes: MutableList<Note> = mutableListOf(),
    var hasSectionLine: Boolean = false,
    var bpm: Double,
    // TODO: No idea what this is
    // Unit is microsecond
    var _stop: Long = 0,
    // TODO: No idea what this is
    var scroll: Double = 1.0,
    // BGA ID
    var bgaID: Int = -1,
    var layer: Int = -1,
    // POOR layer
    var eventLayer: List<Layer> = listOf()
) {
    constructor(section: Double, time: Long, noteSize: Int, bpm: Double, scroll: Double = 1.0): this(
        section = section,
        _time = time,
        notes = arrayOfNulls<Note?>(noteSize),
        hiddenNotes = arrayOfNulls<Note?>(noteSize),
        bpm = bpm,
        scroll = scroll,
    )

    var microTime: Long
        get() = _time
        set(value) { _time = value }

    val milliTime: Long
        get() = _time / 1000

    var microStop: Long
        get() = _stop
        set(value) { _stop = value }

    /**
     * This function is identical to `milliTime` except return type. It's present to keep compatibility with upstream
     */
    fun getTime(): Int = milliTime.toInt()

    val milliStop: Long
        get() = _stop / 1000

    fun getStop(): Int = milliStop.toInt()


    fun existNote(): Boolean = notes.any { it != null }

    fun existNote(lane: Int): Boolean = notes[lane] != null

    fun getNote(lane: Int): Note? = notes[lane]

    fun setNote(lane: Int, note: Note?) {
        notes[lane] = note
        if (note != null) {
            note.section = section
            note.microTime = _time
        }
    }

    fun getHiddenNote(lane: Int): Note? = hiddenNotes[lane]

    fun existHiddenNote(): Boolean = hiddenNotes.any { it != null }

    fun existHiddenNote(lane: Int): Boolean = hiddenNotes[lane] != null

    fun setHiddenNote(lane: Int, note: Note?) {
        hiddenNotes[lane] = note
        if (note != null) {
            note.section = section
            note.microTime = _time
        }
    }

    fun addBackgroundNote(note: Note) {
        note.section = section
        note.microTime = _time
        bgNotes.add(note)
    }

    fun removeBackgroundNote(note: Note) {
        bgNotes.remove(note)
    }

    fun setLaneCount(keys: Int) {
        if (notes.size != keys) {
            val newNotes = arrayOfNulls<Note?>(keys)
            val newHiddenNotes = arrayOfNulls<Note?>(keys)
            System.arraycopy(notes, 0, newHiddenNotes, 0, notes.size)
            System.arraycopy(hiddenNotes, 0, newHiddenNotes, 0, hiddenNotes.size)
            notes = newNotes
            hiddenNotes = newHiddenNotes
        }
    }

    fun getTotalNotes(): Int = getTotalNotes(LongNoteDef.LONG_NOTE)

    fun getTotalNotes(lnType: LongNoteDef): Int {
        return notes.map { note ->
            note ?: return@map 0
            if (note is NormalNote) {
                return@map 1
            }
            if (note is LongNote) {
                if (note.type == LongNoteDef.CHARGE_NOTE || note.type == LongNoteDef.HELL_CHARGE_NOTE
                    || (note.type == LongNoteDef.UNDEFINED && lnType != LongNoteDef.LONG_NOTE)
                    || !note.isEnd()) {
                    return@map 1
                }
            }
            return@map 0
        }.sum()
    }
}