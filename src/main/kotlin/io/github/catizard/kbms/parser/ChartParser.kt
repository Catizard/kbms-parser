package io.github.catizard.kbms.parser

import bms.model.BMSModel
import io.github.catizard.kbms.parser.bms.BMSParser
import bms.model.ChartInformation
import bms.model.LongNoteDef
import bms.model.Mode
import bms.model.Timeline
import io.github.catizard.kbms.parser.bms.logger
import java.io.File
import java.nio.file.Path
import java.util.Locale.getDefault

/**
 * Base class of all the chart parsers. All parsers must be implemented as stateless.
 * I.e. function `parse` must be a pure function
 */
abstract class ChartParser(val config: ChartParserConfig) {
    companion object {
        fun create(path: Path, config: ChartParserConfig): ChartParser {
            val s = path.fileName.toString().lowercase(getDefault())
            return if (s.endsWith(".bmson")) {
                TODO()
            } else if (s.endsWith(".osu")) {
                TODO()
            } else if (s.endsWith(".bms") || s.endsWith(".bme") || s.endsWith(".bml") || s.endsWith(".pms")) {
                BMSParser(config)
            } else {
                throw IllegalArgumentException("No related parser for file: $path")
            }
        }
    }

    fun parse(file: File): BMSModel {
        return parse(file.toPath())
    }

    fun parse(path: Path): BMSModel {
        // Enforce LN mode if the parser sets to follow LR2 behavior
        val lnType = if (config.usingLR2Mode) {
            LongNoteDef.LONG_NOTE
        } else {
            config.lnType
        }
        return parse(ChartInformation(path = path, lnType = lnType))
    }

    abstract fun parse(info: ChartInformation): BMSModel
}

data class ChartParserConfig(
    val usingLR2Mode: Boolean = false,
    var lnType: LongNoteDef,
)

/**
 * Context object used during parsing, any parser can extend it for extra fields
 */
open class ParseContext(
    val lnType: LongNoteDef = LongNoteDef.UNDEFINED,
    val randomStack: ArrayDeque<Int> = ArrayDeque(),
    val randomRecord: MutableList<Int> = mutableListOf(),
    val skipStack: ArrayDeque<Boolean> = ArrayDeque(),
    val bpmTable: MutableMap<Int, Double> = mutableMapOf(),
    val stopTable: MutableMap<Int, Double> = mutableMapOf(),
    val scrollTable: MutableMap<Int, Double> = mutableMapOf(),
    val extraValues: MutableMap<String, String> = mutableMapOf(),
    val selectedRandoms: List<Int>? = null,
    var base: Int = 36,
    var bpm: Double = 0.0,
    val wavList: MutableList<String> = ArrayList(62 * 62),
    // xx -> index of wavList
    private val wavMap: Array<Int?> = arrayOfNulls(62 * 62),
    val bgaList: MutableList<String> = ArrayList(62 * 62),
    // xx -> index of bgaList
    private val bgaMap: Array<Int?> = arrayOfNulls(62 * 62),
    val timelines: MutableList<Timeline> = mutableListOf(),
) {
    private var selectedRandomCount = 0
    var playMode: Mode = Mode.BEAT_5K
        set(value) {
            field = value
            timelines.forEach { tl ->
                tl.setLaneCount(value.key)
            }
        }

    val keys: Int
        get() = playMode.key

    /**
     * Push one random number to stack top
     */
    fun pushNextRandom(r: Int) {
        randomStack.addLast(
            if (selectedRandoms != null) {
                selectedRandomCount++
                selectedRandoms[selectedRandomCount - 1]
            } else {
                val rng = (Math.random() * r).toInt() + 1
                randomRecord.add(r)
                rng
            }
        )
    }

    /**
     * Pop one random number from stack top; do nothing if random stack is empty
     */
    fun popRandom() {
        if (randomStack.isEmpty()) return logger.warn { "#ENDRANDOM doesn't have corresponding #RANDOM command" }
        randomStack.removeLast()
    }

    /**
     * Push one skip flag to stack top; do nothing if random stack is empty
     */
    fun pushSkipFlag(r: Int) {
        if (randomStack.isEmpty()) return logger.warn { "#IF doesn't have corresponding #RANDOM command" }
        skipStack.addLast(randomStack.last() != r)
    }

    /**
     * Pop one skip flag from stack top; do nothing if skip stack is empty
     */
    fun popSkipFlag() {
        if (skipStack.isEmpty()) return logger.warn { "#ENDIF doesn't have corresponding #RANDOM command" }
        skipStack.removeLast()
    }

    fun pushBPM(x1: Char, x2: Char, bpm: Double) {
        val bpmID = parseXX(base, x1, x2) ?: return logger.warn { "NaN argument passed to #BPM" }
        bpmTable[bpmID] = bpm
    }

    fun registerWAV(x1: Char, x2: Char, wavFileName: String) {
        val wavID = parseXX(base, x1, x2) ?: return logger.warn { "NaN argument passed to #WAV" }
        wavMap[wavID] = wavList.size
        wavList.add(wavFileName)
    }

    fun registerBMP(x1: Char, x2: Char, bmpFileName: String) {
        val bmpID = parseXX(base, x1, x2) ?: return logger.warn { "NaN argument passed to #BMP" }
        bgaMap[bmpID] = bgaList.size
        bgaList.add(bmpFileName)
    }

    fun registerStop(x1: Char, x2: Char, stop: Double) {
        val stopID = parseXX(base, x1, x2) ?: return logger.warn { "NaN argument passed to #STOP" }
        stopTable[stopID] = stop
    }

    fun registerScroll(x1: Char, x2: Char, scroll: Double) {
        val scrollID = parseXX(base, x1, x2) ?: return logger.warn { "NaN argument passed to #SCROLL" }
        scrollTable[scrollID] = scroll
    }

    fun shouldSkip(): Boolean = skipStack.isNotEmpty() && skipStack.last()

    fun getLastMilliTime(): Long {
        for (i in timelines.lastIndex downTo 0) {
            val tl = timelines[i]
            (0..<keys).forEachIndexed { lane, _ ->
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

    fun getWavID(data: Int): Int = wavMap[data] ?: -2

    fun getBgaID(data: Int): Int = bgaMap[data] ?: -2
}