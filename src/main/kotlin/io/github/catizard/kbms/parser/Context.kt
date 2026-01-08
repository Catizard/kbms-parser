package io.github.catizard.kbms.parser

import bms.model.*

/**
 * Context object used during parsing, any parser can extend it for extra fields
 */
open class ParseContext(val config: ChartParserConfig) {
    var player: Int = 0
    var genre: String = ""
    var title: String = ""
    var subTitle: String = ""
    var artist: String = ""
    var subArtist: String = ""
    var playLevel: String = ""
    var judgeRank: Int = 2
    var judgeRankType: JudgeRankType = JudgeRankType.BMS_RANK
    var total: Double = 100.0
    var totalType: TotalType = TotalType.BMSON
    var volWAV: Int = 0
    var stageFile: String = ""
    var backBMP: String = ""
    var preview: String = ""
    var lnObj: Int = -1
    var lnMode: LongNoteDef = LongNoteDef.UNDEFINED
    var difficulty: Int = 0
    var banner: String = ""
    val lnType: LongNoteDef = LongNoteDef.UNDEFINED
    val randomStack: ArrayDeque<Int> = ArrayDeque()
    val randomRecord: MutableList<Int> = mutableListOf()
    val skipStack: ArrayDeque<Boolean> = ArrayDeque()
    val bpmTable: MutableMap<Int, Double> = mutableMapOf()
    val stopTable: MutableMap<Int, Double> = mutableMapOf()
    val scrollTable: MutableMap<Int, Double> = mutableMapOf()
    val extraValues: MutableMap<String, String> = mutableMapOf()
    var base: Int = 36
    var bpm: Double = 0.0
    val wavList: MutableList<String> = ArrayList(62 * 62)

    // xx -> index of wavList
    protected val wavMap: Array<Int?> = arrayOfNulls(62 * 62)
    val bgaList: MutableList<String> = ArrayList(62 * 62)

    // xx -> index of bgaList
    protected val bgaMap: Array<Int?> = arrayOfNulls(62 * 62)
    val timelines: MutableList<Timeline> = mutableListOf()
    var playMode: Mode = Mode.BEAT_5K
        set(value) {
            field = value
            timelines.forEach { tl ->
                tl.setLaneCount(value.key)
            }
        }

    val keys: Int
        get() = playMode.key

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