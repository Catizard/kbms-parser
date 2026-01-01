package bms.model

import bms.model.note.Note
import io.github.catizard.kbms.parser.bms.BMSParseContext
import java.nio.file.Path

data class BMSModel(
    // Player count, normally 1 or 2
    val player: Int,
    // Play/Key mode
    val mode: Mode,
    val title: String,
    val subTitle: String,
    val genre: String,
    val artist: String,
    val subArtist: String,
    val banner: String,
    val stageFile: String,
    val backBMP: String,
    val preview: String,
    val bpm: Double,
    val playLevel: String,
    val difficulty: Int,
    val judgeRank: Int,
    val judgeRankType: JudgeRankType,
    val total: Double,
    val totalType: TotalType,
    var volWAV: Int,
    val md5: String,
    val sha256: String,
    // base number 36 | 62
    var base: Int = 36,
    // Long note type(chart defined)
    val lnMode: LongNoteDef,
    val lnObj: Int = -1,
    // Whether this chart is from osu or not
    val fromOSU: Boolean = false,
    // Timeline
    val timelines: List<Timeline>,
    val info: ChartInformation,
    val values: Map<String, String>
) {
    constructor(ctx: BMSParseContext, info: ChartInformation, md5: String, sha256: String): this(
        player = ctx.player,
        mode = ctx.playMode,
        title = ctx.title,
        subTitle = ctx.subTitle,
        genre = ctx.genre,
        artist = ctx.artist,
        subArtist = ctx.subArtist,
        banner = ctx.banner,
        stageFile = ctx.stageFile,
        backBMP = ctx.backBMP,
        preview = ctx.preview,
        bpm = ctx.bpm,
        playLevel = ctx.playLevel,
        difficulty = ctx.difficulty,
        judgeRank = ctx.judgeRank,
        judgeRankType = ctx.judgeRankType,
        total = ctx.total,
        totalType = ctx.totalType,
        volWAV = ctx.volWAV,
        md5 = md5,
        sha256 = sha256,
        base = ctx.base,
        lnMode = ctx.lnMode,
        lnObj = ctx.lnObj,
        info = info,
        timelines = ctx.timelines,
        values = ctx.extraValues
    )

    // Lowest bpm in this chart
    val minBPM: Double
        get() = bpm.coerceAtMost(timelines.minOfOrNull { it.bpm } ?: bpm)

    // Highest bpm in this chart
    val maxBPM: Double
        get() = bpm.coerceAtLeast(timelines.maxOfOrNull { it.bpm } ?: bpm)
}

data class ChartInformation(
    val path: Path,
    val lnType: LongNoteDef,
    val selectedRandoms: List<Int>? = null,
)

class Timeline(
    // section
    // Time, unit is microsecond
    private val _time: Long,
    // TODO: Upstream's setSection has different behavior
    val section: Double,
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

    val microTime: Long
        get() = _time
    val milliTime: Long
        get() = _time / 1000

    var microStop: Long
        get() = _stop
        set(value) { _stop = value }

    val milliStop: Int
        get() = (_stop / 1000).toInt()

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
}

data class Layer(
    val event: Event,
    val layerSequence: List<List<LayerSequence>> = listOf()
)

data class Event(
    val type: EventType,
    val interval: Int
)

data class LayerSequence(
    val time: Long,
    val id: Int
) {
    fun isEnd(): Boolean = id == END

    companion object {
        const val END: Int = Int.MIN_VALUE

        fun endSequence(time: Long): LayerSequence = LayerSequence(time, END)
    }
}

enum class Mode(
    val id: Int,
    val hint: String,
    val player: Int,
    val key: Int,
    val scratchKey: Array<Int> = arrayOf()
) {
    BEAT_5K(5, "beat-5k", 1, 6, arrayOf(5)),
    BEAT_7K(7, "beat-7k", 1, 8, arrayOf(7)),
    BEAT_10K(11, "beat-10k", 2, 12, arrayOf(5, 11)),
    BEAT_14K(14, "beat-14k", 2, 16, arrayOf(7, 15)),
    POPN_5K(9, "popn-5k", 1, 5),
    POPN_9K(9, "popn-9k", 1, 9),
    KEYBOARD_24K(25, "keyboard-24k", 1, 26, arrayOf(24, 25)),
    KEYBOARD_24K_DOUBLE(50, "keyboard-24k-double", 2, 52, arrayOf(24, 25, 50, 51));

    fun isScratchKey(key: Int): Boolean = scratchKey.contains(key)

    companion object {
        fun fromHint(hint: String): Mode? = entries.firstOrNull { mode -> mode.hint == hint }
    }
}

enum class JudgeRankType {
    BMS_RANK, BMS_DEFEXRANK, BMSON_JUDGERANK
}

enum class TotalType {
    BMS, BMSON
}

/**
 * Chart's own long note definition
 */
enum class LongNoteDef {
    UNDEFINED, // Chart hasn't defined it
    LONG_NOTE, // ln
    CHARGE_NOTE, // cn
    HELL_CHARGE_NOTE; // hcn

    companion object {
        fun fromLNMode(value: Int): LongNoteDef? {
            return when(value) {
                1 -> LONG_NOTE
                2 -> CHARGE_NOTE
                3 -> HELL_CHARGE_NOTE
                else -> null
            }
        }
    }
}

enum class EventType {
    ALWAYS, PLAY, MISS
}