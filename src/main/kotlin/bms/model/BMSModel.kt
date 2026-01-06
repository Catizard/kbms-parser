package bms.model

import io.github.catizard.kbms.parser.ParseContext
import kotlinx.serialization.Serializable
import java.nio.file.Path

data class BMSModel(
    // Player count, normally 1 or 2
    val player: Int,
    // Play/Key mode
    var mode: Mode,
    val title: String,
    val subTitle: String,
    val genre: String,
    val artist: String,
    val subArtist: String,
    val banner: String,
    val stageFile: String,
    val backBMP: String,
    val preview: String,
    var bpm: Double,
    val playLevel: String,
    val difficulty: Int,
    var judgeRank: Int,
    var judgeRankType: JudgeRankType,
    var total: Double,
    var totalType: TotalType,
    var volWAV: Int,
    val md5: String,
    val sha256: String,
    // base number 36 | 62
    var base: Int = 36,
    /**
     * Long note type (chart defined)
     *
     * NOTE: There're two long note type definition, one is here, defined by chart. The other
     *  one is inside [info], which is defined by user/client. By flagging
     *  [io.github.catizard.kbms.parser.ChartParserConfig.usingLR2Mode], [lnMode] would be
     *  forced as [LongNoteDef.LONG_NOTE] because LR2 doesn't recognize the long note
     *  definition by chart itself
     *
     * @see [getLnType]
     */
    val lnMode: LongNoteDef,
    val lnObj: Int = -1,
    // Whether this chart is from osu or not
    var fromOSU: Boolean = false,
    /**
     * Timeline
     *
     * This field marked as writable is only for compatibility with upstream
     */
    var timelines: List<Timeline>,
    val info: ChartInformation,
    val values: Map<String, String>,
    val wavList: Array<String>,
    val bgaList: Array<String>,
) {
    constructor(ctx: ParseContext, info: ChartInformation, md5: String, sha256: String) : this(
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
        values = ctx.extraValues,
        wavList = ctx.wavList.toTypedArray(),
        bgaList = ctx.bgaList.toTypedArray(),
    )

    // Lowest bpm in this chart
    val minBPM: Double
        get() = bpm.coerceAtMost(timelines.minOfOrNull { it.bpm } ?: bpm)

    // Highest bpm in this chart
    val maxBPM: Double
        get() = bpm.coerceAtLeast(timelines.maxOfOrNull { it.bpm } ?: bpm)

    fun getAllTimelines(): Array<Timeline> = timelines.toTypedArray()

    fun getPath(): String = info.path.toString()

    fun getLastTime(): Int = getLastMilliTime().toInt()

    fun getLastMilliTime(): Long {
        for (i in timelines.lastIndex downTo 0) {
            val tl = timelines[i]
            (0..<mode.key).forEachIndexed { lane, _ ->
                if (tl.existNote(lane) || tl.getHiddenNote(lane) != null
                    || tl.bgNotes.isNotEmpty() || tl.bgaID != -1
                    || tl.layer != -1
                ) {
                    return tl.milliTime
                }
            }
        }
        return 0
    }

    fun setTimelines(timelines: Array<Timeline>) {
        this.timelines = timelines.toList()
    }

    fun getTotalNotes(): Int = BMSModelUtils.getTotalNotes(this)

    fun getLnType(): LongNoteDef = info.lnType

    fun containsUndefinedLongNote(): Boolean {
        val keys = mode.key
        return timelines.any { timeline ->
            for (i in 0..<keys) {
                val note = timeline.getNote(i) ?: continue
                if (note is LongNote && note.type == LongNoteDef.UNDEFINED) {
                    return@any true
                }
            }
            return@any false
        }
    }

    fun getRandom(): IntArray? = info.selectedRandoms?.toIntArray()

    fun getLastNoteTime(): Int = getLastMilliTime().toInt()

    fun getLastNoteMilliTime(): Long {
        val keys = mode.key
        for (i in timelines.lastIndex downTo 0) {
            val tl = timelines[i]
            for (i in 0..<keys) {
                if (tl.existNote(i)) {
                    return tl.milliTime
                }
            }
        }
        return 0
    }

    fun toChartString(): String {
        val sb = StringBuilder()
        sb.append("JUDGERANK:" + judgeRank + "\n")
        sb.append("TOTAL:" + total + "\n")
        // TODO: Chart string is different here
        sb.append("LNMODE:" + lnMode + "\n")
        var nowbpm = -Double.Companion.MIN_VALUE
        val tlsb = StringBuilder()
        for (tl in timelines) {
            tlsb.setLength(0)
            tlsb.append(tl.getTime().toString() + ":")
            var write = false
            if (nowbpm != tl.bpm) {
                nowbpm = tl.bpm
                tlsb.append("B(" + nowbpm + ")")
                write = true
            }
            if (tl.getStop() != 0) {
                tlsb.append("S(" + tl.getStop() + ")")
                write = true
            }
            if (tl.hasSectionLine) {
                tlsb.append("L")
                write = true
            }

            tlsb.append("[")
            for (lane in 0..<mode.key) {
                val n: Note? = tl.getNote(lane)
                if (n is NormalNote) {
                    tlsb.append("1")
                    write = true
                } else if (n is LongNote) {
                    if (!n.isEnd()) {
                        val lnchars = charArrayOf('l', 'L', 'C', 'H')
                        // TODO: Milli duration?
                        tlsb.append(lnchars[n.type.ordinal].code.toLong() + n.duration)
                        write = true
                    }
                } else if (n is MineNote) {
                    tlsb.append("m" + n.damage)
                    write = true
                } else {
                    tlsb.append("0")
                }
                if (lane < mode.key - 1) {
                    tlsb.append(",")
                }
            }
            tlsb.append("]\n")

            if (write) {
                sb.append(tlsb)
            }
        }
        return sb.toString()
    }

    fun getLanes(): Array<Lane> {
        return (0..<mode.key).map { i ->
            Lane(this, i)
        }.toTypedArray()
    }
}

data class ChartInformation(
    val path: Path,
    val lnType: LongNoteDef,
    val selectedRandoms: List<Int>? = null,
)

data class Layer(
    val event: Event,
    val layerSequence: List<List<LayerSequence>> = listOf()
)

data class Event(
    val type: EventType,
    val interval: Int
)

@Serializable
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
    @JvmField val id: Int,
    @JvmField val hint: String,
    @JvmField val player: Int,
    @JvmField val key: Int,
    @JvmField val scratchKey: IntArray = intArrayOf()
) {
    BEAT_5K(5, "beat-5k", 1, 6, intArrayOf(5)),
    BEAT_7K(7, "beat-7k", 1, 8, intArrayOf(7)),
    BEAT_10K(11, "beat-10k", 2, 12, intArrayOf(5, 11)),
    BEAT_14K(14, "beat-14k", 2, 16, intArrayOf(7, 15)),
    POPN_5K(9, "popn-5k", 1, 5),
    POPN_9K(9, "popn-9k", 1, 9),
    KEYBOARD_24K(25, "keyboard-24k", 1, 26, intArrayOf(24, 25)),
    KEYBOARD_24K_DOUBLE(50, "keyboard-24k-double", 2, 52, intArrayOf(24, 25, 50, 51));

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