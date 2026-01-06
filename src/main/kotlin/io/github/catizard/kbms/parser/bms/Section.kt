package io.github.catizard.kbms.parser.bms

import bms.model.Event
import bms.model.EventType
import bms.model.Layer
import bms.model.LayerSequence
import bms.model.Mode
import bms.model.Timeline
import bms.model.LongNote
import bms.model.MineNote
import bms.model.NormalNote
import io.github.catizard.kbms.parser.parseInt36
import io.github.catizard.kbms.parser.parseXX
import java.util.*

/**
 * Section is bound with BMSParser
 */
class Section {
    // Section's expand rate, defaults to 1.0
    val rate: Double
    val sectionNum: Double
    val channelLines: MutableList<String> = mutableListOf()
    val bpmChange: TreeMap<Double, Double> = TreeMap()
    val stop: TreeMap<Double, Double> = TreeMap()
    val scroll: TreeMap<Double, Double> = TreeMap()
    val poor: List<Int>
    val ctx: BMSParseContext
    val timelineCache: TreeMap<Double, TimelineCache>

    // Alias of ctx.playMode.key
    private val keys
        get() = ctx.playMode.key

    /**
     * Channel messages could be divided into 3 kinds:
     *
     * - An event that indicates something changed, like bpm, measure-length, etc...
     * - An event that an image/layer should be displayed
     * - A series of notes that would be appearing in this track
     *
     * In this constructor, the very first one kind of messages would be parsed and stored in different fields for
     *  subsequent usage. The rest 2 would be untouched and stored into `channelLines` directly. They'll be converted
     *  as `Timeline` instance later.
     *
     * @see bms.model.Timeline
     * @see io.github.catizard.kbms.parser.bms.Section.processTimeline
     */
    constructor(
        prev: Section?,
        ctx: BMSParseContext,
        channelMessages: List<String>?,
        timelineCache: TreeMap<Double, TimelineCache>
    ) {
        this.sectionNum = if (prev == null) 0.0 else prev.sectionNum + prev.rate
        this.timelineCache = timelineCache
        this.ctx = ctx

        var rate = 1.0
        var poor = listOf<Int>()
        channelMessages?.forEach { channelMessage ->
            val channel = parseInt36(channelMessage[4], channelMessage[5]) ?: return@forEach
            when (ChannelDef.valueOf(channel)) {
                ChannelDef.LANE_AUTOPLAY, ChannelDef.BGA_PLAY, ChannelDef.LAYER_PLAY -> channelLines.add(channelMessage)
                ChannelDef.SECTION_RATE -> {
                    val split = channelMessage.split(":")
                    split.getOrNull(1)?.toDoubleOrNull()?.let {
                        rate = it
                    } ?: logger.warn { "NaN passed as rate of the section" }
                }
                // NOTE: BPM_CHANGE doesn't rely on base number defined in bms file!
                ChannelDef.BPM_CHANGE -> {
                    parseLine(channelMessage) { pos, x1, x2 ->
                        val data = parseInt36(x1, x2) ?: return@parseLine
                        if (data != 0) {
                            // TODO: Ask god about this math
                            bpmChange[pos] = (data / 36).toDouble() * 16 + (data % 36)
                        }
                    }
                }

                ChannelDef.POOR_PLAY -> {
                    parseLine(ctx.base, channelMessage) { data ->
                        poor = data
                        // NOTE: jbmstable-parser claimed there is an extra situation
                        //  that poor image file could be exactly one image and we
                        //  should exclude the 0 value
                        val uniquePoorImageIds = data.filter { it != 0 }.toSet().stream().toList()
                        if (uniquePoorImageIds.size == 1) {
                            poor = listOf<Int>(uniquePoorImageIds[0])
                        }
                    }
                }

                ChannelDef.BPM_CHANGE_EXTEND -> {
                    parseLine(ctx.base, channelMessage) { pos, data ->
                        val bpm = ctx.bpmTable[data]
                        if (bpm != null) {
                            bpmChange[pos] = bpm
                        } else {
                            logger.warn { "BPM_CHANGE_EXTEND receives a bpm that hasn't been defined" }
                        }
                    }
                }

                ChannelDef.STOP -> {
                    parseLine(ctx.base, channelMessage) { pos, data ->
                        val st = ctx.stopTable[data]
                        if (st != null) {
                            stop[pos] = st
                        } else {
                            logger.warn { "STOP receives a stop that hasn't been defined" }
                        }
                    }
                }

                ChannelDef.SCROLL -> {
                    parseLine(ctx.base, channelMessage) { pos, data ->
                        val sc = ctx.scrollTable[data]
                        if (sc != null) {
                            scroll[pos] = sc
                        } else {
                            logger.warn { "SCROLL receives a stop that hasn't been defined" }
                        }
                    }
                }

                else -> {
                    // NOTE: Some chart has 2p side notes definition, but doesn't set #PLAYER correctly. In this case,
                    //  we need to manually convert the play mode and key lane count
                    var baseChannel = 0
                    var channelP2 = 0
                    for (noteChannel in ChannelDef.NOTE_CHANNELS) {
                        if (channel in noteChannel..noteChannel + 8) {
                            baseChannel = noteChannel
                            channelP2 = channel - noteChannel
                            channelLines.add(channelMessage)
                            break
                        }
                    }
                    // 5/10 Keys => 7/14 Keys
                    if (channelP2 == 7 || channelP2 == 8) {
                        if (ctx.playMode == Mode.BEAT_5K) {
                            ctx.playMode = Mode.BEAT_7K
                        }
                        if (ctx.playMode == Mode.BEAT_10K) {
                            ctx.playMode = Mode.BEAT_14K
                        }
                    }
                    // 5/7 Keys => 10/14 Keys
                    val baseChannelDef = ChannelDef.valueOf(baseChannel)
                    if (baseChannelDef == ChannelDef.P2_KEY_BASE
                        || baseChannelDef == ChannelDef.P2_INVISIBLE_KEY_BASE
                        || baseChannelDef == ChannelDef.P2_LONG_KEY_BASE
                        || baseChannelDef == ChannelDef.P2_MINE_KEY_BASE
                    ) {
                        if (ctx.playMode == Mode.BEAT_5K) {
                            ctx.playMode = Mode.BEAT_10K
                        }
                        if (ctx.playMode == Mode.BEAT_7K) {
                            ctx.playMode = Mode.BEAT_14K
                        }
                    }
                }
            }
        }
        this.rate = rate
        this.poor = poor
    }

    /**
     * @param lnList A two-dimension array, the first level is lane index, the second is the long notes on this lane.
     *  Please note that the long note has been added to [lnList] are all 'paired' (i.e. [LongNote.pair] is not null)
     * @param startLN A shared status, storing each lane's long note that hasn't been 'paired'
     */
    fun processTimeline(ctx: BMSParseContext, lnList: Array<MutableList<LongNote>?>, startLN: Array<LongNote?>) {
        // TODO: Ask god what does "Add section line" means
        val baseTimeline = getTimeline(sectionNum)
        baseTimeline.hasSectionLine = true

        if (poor.isNotEmpty()) {
            val poorSequences = (0..poor.size).map { i ->
                if (i == poor.size) {
                    LayerSequence.endSequence(POOR_TIME)
                } else {
                    if (ctx.getBgaID(poor[i]) != -2) {
                        LayerSequence(i * POOR_TIME / poor.size, ctx.getBgaID(poor[i]))
                    } else {
                        LayerSequence(i * POOR_TIME / poor.size, -1)
                    }
                }
            }
            baseTimeline.eventLayer = listOf(
                Layer(
                    event = Event(EventType.MISS, 1),
                    layerSequence = listOf(poorSequences)
                )
            )
        }

        // TODO: I couldn't understand...
        // BPM変化。ストップシーケンステーブル準備
        val stops = stop.entries.iterator()
        var ste = if (stops.hasNext()) stops.next() else null
        val bpms = bpmChange.entries.iterator()
        var bce = if (bpms.hasNext()) bpms.next() else null
        val scrolls = scroll.entries.iterator()
        var sce = if (scrolls.hasNext()) scrolls.next() else null

        while (ste != null || bce != null || sce != null) {
            val bc: Double = (bce?.key ?: 2.0)
            val st: Double = (ste?.key ?: 2.0)
            val sc: Double = (sce?.key ?: 2.0)
            if (sc <= st && sc <= bc) {
                getTimeline(sectionNum + sc * rate).scroll = (sce!!.value)
                sce = if (scrolls.hasNext()) scrolls.next() else null
            } else if (bc <= st) {
                getTimeline(sectionNum + bc * rate).bpm = (bce!!.value)
                bce = if (bpms.hasNext()) bpms.next() else null
            } else if (st <= 1) {
                val tl: Timeline = getTimeline(sectionNum + ste!!.key * rate)
                tl.microStop = ((1000.0 * 1000 * 60 * 4 * ste.value / (tl.bpm)).toLong())
                ste = if (stops.hasNext()) stops.next() else null
            }
        }

        for (channelMessage in channelLines) {
            val paramChannel = parseInt36(channelMessage[4], channelMessage[5]) ?: continue
            val channelLane = ChannelLane.create(ctx.playMode, paramChannel)
            if (channelLane?.lane == -1) {
                continue
            }

            // Not a note
            if (channelLane == null) {
                when (val channelDef = ChannelDef.valueOf(paramChannel)) {
                    ChannelDef.LANE_AUTOPLAY -> {
                        parseLine(ctx.base, channelMessage) { pos, data ->
                            getTimeline(sectionNum + rate * pos).addBackgroundNote(NormalNote(ctx.getWavID(data)))
                        }
                    }

                    ChannelDef.BGA_PLAY -> {
                        parseLine(ctx.base, channelMessage) { pos, data ->
                            getTimeline(sectionNum + rate * pos).bgaID = ctx.getBgaID(data)
                        }
                    }

                    ChannelDef.LAYER_PLAY -> {
                        parseLine(ctx.base, channelMessage) { pos, data ->
                            getTimeline(sectionNum + rate * pos).layer = ctx.getBgaID(data)
                        }
                    }

                    else -> {
                        logger.warn { "Unexpected channel type $channelDef when processing timelines" }
                    }
                }

                continue
            }

            val lnObj = ctx.lnObj
            val lnMode = ctx.lnMode
            val key = channelLane.lane
            when (channelLane.type) {
                NoteType.Normal -> {
                    parseLine(ctx.base, channelMessage) { pos, data ->
                        // Normal note / LNObj
                        val tl = getTimeline(sectionNum + rate * pos)
                        if (tl.existNote(key)) {
                            logger.warn { "Conflict happens when trying to add a normal key ${key + 1} at time(ms): ${tl.milliTime}" }
                        }
                        if (data == lnObj) {
                            for (entry in timelineCache.descendingMap().entries) {
                                if (entry.key >= tl.section) {
                                    continue
                                }
                                val tl2 = entry.value.timeline
                                if (tl2.existNote(key)) {
                                    val note = tl2.getNote(key)!!
                                    if (note is NormalNote) {
                                        val ln = LongNote(note.wav, lnMode)
                                        tl2.setNote(key, ln)
                                        val lnEND = LongNote(-2)
                                        tl.setNote(key, lnEND)
                                        ln.connectPair(lnEND)

                                        if (lnList[key] == null) {
                                            lnList[key] = mutableListOf()
                                        }
                                        lnList[key]!!.add(ln)
                                        break
                                    } else if (note is LongNote && note.pair == null) {
                                        logger.warn { "LN's start note and end note are not paired: ${key + 1}, section: ${tl2.section} - ${tl.section}" }
                                        val lnEND = LongNote(-2)
                                        tl.setNote(key, lnEND)
                                        note.connectPair(lnEND)

                                        if (lnList[key] == null) {
                                            lnList[key] = mutableListOf()
                                        }
                                        lnList[key]!!.add(note)
                                        startLN[key] = null
                                        break
                                    } else {
                                        logger.warn { "Cannot parse ln node, lane: ${key}, time(ms): ${tl2.milliTime}" }
                                        break
                                    }
                                }
                            }
                        } else {
                            tl.setNote(key, NormalNote(wav = ctx.getWavID(data)))
                        }
                    }
                }

                NoteType.Invisible -> {
                    parseLine(ctx.base, channelMessage) { pos, data ->
                        getTimeline(sectionNum + rate * pos).setHiddenNote(
                            key,
                            NormalNote(wav = ctx.getWavID(data))
                        )
                    }
                }

                NoteType.Long -> {
                    parseLine(ctx.base, channelMessage) { pos, data ->
                        val tl = getTimeline(sectionNum + rate * pos)
                        val insideLN =
                            lnList[key]?.any { longNote -> tl.section in longNote.section..longNote.pair!!.section }
                                ?: false
                        if (insideLN) {
                            if (startLN[key] == null) {
                                val ln = LongNote(ctx.getWavID(data))
                                ln.section = Double.MIN_VALUE
                                startLN[key] = ln
                                logger.warn { "LN内にLN開始ノートを定義しようとしています : ${key + 1}, section: ${tl.section}, time(ms): ${tl.microTime}"  }
                            } else {
                                if (startLN[key]!!.section != Double.MIN_VALUE) {
                                    timelineCache.get(startLN[key]!!.section)!!.timeline.setNote(key, null)
                                }
                                startLN[key] = null
                                logger.warn { "LN内にLN終端ノートを定義しようとしています : ${key + 1}, section: ${tl.section}, time(ms): ${tl.microTime}" }
                            }
                            return@parseLine logger.warn { "LN's start note is inside another long node period" }
                        }

                        if (startLN[key] == null) {
                            if (tl.existNote(key)) {
                                val note = tl.getNote(key)!!
                                logger.warn { "LN's start place has a normal note: ${key + 1}, time(ms): ${tl.milliTime}" }
                                if (note is NormalNote && note.wav != ctx.getWavID(data)) {
                                    tl.addBackgroundNote(note)
                                }
                            }
                            val ln = LongNote(ctx.getWavID(data))
                            tl.setNote(key, ln)
                            startLN[key] = ln
                            return@parseLine
                        }

                        val startLongNote = startLN[key]!!
                        if (startLongNote.section == Double.MIN_VALUE) {
                            startLN[key] = null
                            return@parseLine
                        }

                        // Handle ln's tail
                        for (e in timelineCache.descendingMap().entries) {
                            if (e.key >= tl.section) {
                                continue
                            }
                            val tl2 = e.value.timeline
                            if (tl2.section == startLongNote.section) {
                                startLongNote.type = lnMode
                                val noteEnd = LongNote(
                                    if (startLongNote.wav != ctx.getWavID(data)) {
                                        ctx.getWavID(data)
                                    } else {
                                        -2
                                    }
                                )
                                tl.setNote(key, noteEnd)
                                startLongNote.connectPair(noteEnd)
                                if (lnList[key] == null) {
                                    lnList[key] = mutableListOf()
                                }
                                lnList[key]!!.add(startLongNote)

                                startLN[key] = null
                                break
                            } else if (tl2.existNote(key)) {
                                val note = tl2.getNote(key)
                                logger.warn { "LN内に通常ノートが存在します。レーン: ${key + 1} time(ms): ${tl2.milliTime}" }
                                tl2.setNote(key, null)
                                if (note is NormalNote) {
                                    tl2.addBackgroundNote(note)
                                }
                            }
                        }
                    }
                }

                NoteType.Mine -> {
                    parseLine(channelMessage) { pos, x1, x2 ->
                        val data = parseInt36(x1, x2) ?: return@parseLine
                        if (data == 0) return@parseLine

                        val tl = getTimeline(sectionNum + rate * pos)
                        val insideLN = tl.existNote(key)
                                || lnList[key]?.any { longNote -> tl.section in longNote.section..longNote.pair!!.section } ?: false

                        if (!insideLN) {
                            tl.setNote(key, MineNote(wav = ctx.getWavID(0), damage = data.toDouble()))
                        } else {
                            logger.warn { "Conflict happens when trying to add mine note: ${key + 1}, time(ms): ${tl.milliTime}" }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val POOR_TIME = 500L

        /**
         * Split out a single channel message and execute the `action` function
         *
         * Example:
         *
         * ```
         * "#00211:03030303"
         *         |      |
         *         +------+ => we want to perform the 'action' here for each '03'
         * ```
         *
         * @param base 36 or 62
         * @param line channel message line
         * @param action function that receives current executing param pos and data. The data
         *  passed to action would be parsed as a 36-based or 62-based integer
         */
        fun parseLine(base: Int, line: String, action: (pos: Double, data: Int) -> Unit) {
            val f = line.indexOf(":") + 1
            val m = (line.length - f) / 2
            (0..<m).forEach { i ->
                val data = parseXX(base, line[f + i * 2], line[f + i * 2 + 1]) ?: return@forEach
                if (data == 0) return@forEach
                action(i * 1.0 / m, data)
            }
        }

        /**
         * This variant of `parseLine` is because channel event `BPM_CHANGE` doesn't rely the
         *  base number: The data should always be treated as a 36-based number
         *
         * @param action function that receives current executing param pos and data. The data
         *  passed to action wouldn't be parsed
         */
        fun parseLine(line: String, action: (pos: Double, x1: Char, x2: Char) -> Unit) {
            val f = line.indexOf(":") + 1
            val m = (line.length - f) / 2
            (0..<m).forEach { i ->
                action(i * 1.0 / m, line[f + i * 2], line[f + i * 2 + 1])
            }
        }

        /**
         * @param action function that receives the param as a whole
         */
        fun parseLine(base: Int, line: String, action: (data: List<Int>) -> Unit) {
            val f = line.indexOf(":") + 1
            val m = (line.length - f) / 2
            action((0..<m).map { i ->
                parseXX(base, line[f + i * 2], line[f + i * 2 + 1])
            }.filter { it != null }.map { it!! })
        }
    }

    private fun getTimeline(sectionNum: Double): Timeline {
        if (timelineCache.containsKey(sectionNum)) {
            return timelineCache[sectionNum]!!.timeline
        }

        val le: Map.Entry<Double, TimelineCache> = timelineCache.lowerEntry(sectionNum)
        val scroll = le.value.timeline.scroll
        val bpm = le.value.timeline.bpm
        val time = le.value.time + le.value.timeline.microStop + (240000.0 * 1000 * (sectionNum - le.key)) / bpm

        val timeline = Timeline(sectionNum, time.toLong(), keys, bpm, scroll = scroll)
        timelineCache[sectionNum] = TimelineCache(time, timeline)
        return timeline
    }
}

enum class ChannelDef(val value: Int) {
    LANE_AUTOPLAY(1),
    SECTION_RATE(2),
    BPM_CHANGE(3),
    BGA_PLAY(4),
    POOR_PLAY(6),
    LAYER_PLAY(7),
    BPM_CHANGE_EXTEND(8),
    STOP(9),
    SCROLL(1020),

    P1_KEY_BASE(1 * 36 + 1),
    P2_KEY_BASE(2 * 36 + 1),
    P1_INVISIBLE_KEY_BASE(3 * 36 + 1),
    P2_INVISIBLE_KEY_BASE(4 * 36 + 1),
    P1_LONG_KEY_BASE(5 * 36 + 1),
    P2_LONG_KEY_BASE(6 * 36 + 1),
    P1_MINE_KEY_BASE(13 * 36 + 1),
    P2_MINE_KEY_BASE(14 * 36 + 1);

    companion object {
        val NOTE_CHANNELS = arrayOf<Int>(
            P1_KEY_BASE.value,
            P2_KEY_BASE.value,
            P1_INVISIBLE_KEY_BASE.value,
            P2_INVISIBLE_KEY_BASE.value,
            P1_LONG_KEY_BASE.value,
            P2_LONG_KEY_BASE.value,
            P1_MINE_KEY_BASE.value,
            P2_MINE_KEY_BASE.value
        )

        val P1_KEY_RANGE = IntRange(P1_KEY_BASE.value, P1_KEY_BASE.value + 8)
        val P2_KEY_RANGE = IntRange(P2_KEY_BASE.value, P2_KEY_BASE.value + 8)
        val P1_INVISIBLE_KEY_RANGE = IntRange(P1_INVISIBLE_KEY_BASE.value, P1_INVISIBLE_KEY_BASE.value + 8)
        val P2_INVISIBLE_KEY_RANGE = IntRange(P2_INVISIBLE_KEY_BASE.value, P2_INVISIBLE_KEY_BASE.value + 8)
        val P1_LONG_KEY_RANGE = IntRange(P1_LONG_KEY_BASE.value, P1_LONG_KEY_BASE.value + 8)
        val P2_LONG_KEY_RANGE = IntRange(P2_LONG_KEY_BASE.value, P2_LONG_KEY_BASE.value + 8)
        val P1_MINE_KEY_RANGE = IntRange(P1_MINE_KEY_BASE.value, P1_MINE_KEY_BASE.value + 8)
        val P2_MINE_KEY_RANGE = IntRange(P2_MINE_KEY_BASE.value, P2_MINE_KEY_BASE.value + 8)

        fun valueOf(value: Int): ChannelDef? {
            return when (value) {
                LANE_AUTOPLAY.value -> LANE_AUTOPLAY
                SECTION_RATE.value -> SECTION_RATE
                BPM_CHANGE.value -> BPM_CHANGE
                BGA_PLAY.value -> BGA_PLAY
                POOR_PLAY.value -> POOR_PLAY
                LAYER_PLAY.value -> LAYER_PLAY
                BPM_CHANGE_EXTEND.value -> BPM_CHANGE_EXTEND
                STOP.value -> STOP
                P1_KEY_BASE.value -> P1_KEY_BASE
                P2_KEY_BASE.value -> P2_KEY_BASE
                P1_INVISIBLE_KEY_BASE.value -> P1_INVISIBLE_KEY_BASE
                P2_INVISIBLE_KEY_BASE.value -> P2_INVISIBLE_KEY_BASE
                P1_LONG_KEY_BASE.value -> P1_LONG_KEY_BASE
                P2_LONG_KEY_BASE.value -> P2_LONG_KEY_BASE
                P1_MINE_KEY_BASE.value -> P1_MINE_KEY_BASE
                P2_MINE_KEY_BASE.value -> P2_MINE_KEY_BASE
                SCROLL.value -> SCROLL
                else -> null
            }
        }
    }
}

/**
 * ChannelLane is a helper class for handling mapping from the channel number to actual key lane index
 *  For example, the channel `11`, as the KEY1 of the P1 side, should be mapped to `0` lane in BEAT 7K
 *  mode. Below is a small illustrate of how the table is calculated(there's only 7K mode, others can be
 *  calculated by the same way)
 *
 * > (P1 side)
 *
 * ```
 * |------------------------------------------------------|
 * | Channel | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 |
 * | offset  | 0  | 1  | 2  | 3  | 4  | 5  | 6  | 7  | 8  |
 * | KEYS    | K1 | K2 | K3 | K4 | K5 | SC | X  | K6 | K7 |
 * | lane    | 0  | 1  | 2  | 3  | 4  | 7  | -1 | 5  | 6  |
 * |------------------------------------------------------|
 * ```
 *
 * > (P2 side)
 *
 * ```
 * |------------------------------------------------------|
 * | Channel | 21 | 22 | 23 | 24 | 25 | 26 | 27 | 28 | 29 |
 * | offset  | 0  | 1  | 2  | 3  | 4  | 5  | 6  | 7  | 8  |
 * | KEYS    | K1 | K2 | K3 | K4 | K5 | SC | X  | K6 | K7 |
 * | lane    | 8  | 9  | 10 | 11 | 12 | 15 | -1 | 13 | 14 |
 * |------------------------------------------------------|
 * ```
 *
 * Please note that channel 17 and 27 are unused. And P2 lane number need to be added 8.
 */
private data class ChannelLane(
    val channel: Int,
    val lane: Int,
    val type: NoteType,
) {
    companion object {
        private val CHANNEL_ASSIGN_BEAT5_P1 = arrayOf(0, 1, 2, 3, 4, 5, -1, -1, -1)
        private val CHANNEL_ASSIGN_BEAT5_P2 = arrayOf(6, 7, 8, 9, 10, 11, -1, -1, -1)
        private val CHANNEL_ASSIGN_BEAT7_P1 = arrayOf(0, 1, 2, 3, 4, 7, -1, 5, 6)
        private val CHANNEL_ASSIGN_BEAT7_P2 = arrayOf(8, 9, 10, 11, 12, 15, -1, 13, 14)
        private val CHANNEL_ASSIGN_POPN_P1 = arrayOf(0, 1, 2, 3, 4, -1, -1, -1, -1)
        private val CHANNEL_ASSIGN_POPN_P2 = arrayOf(-1, 5, 6, 7, 8, -1, -1, -1, -1)

        private fun getP1SideLane(mode: Mode, offset: Int): Int = when (mode) {
            Mode.BEAT_7K, Mode.BEAT_14K -> CHANNEL_ASSIGN_BEAT7_P1[offset]
            Mode.POPN_9K -> CHANNEL_ASSIGN_POPN_P1[offset]
            else -> CHANNEL_ASSIGN_BEAT5_P1[offset]
        }

        private fun getP2SideLane(mode: Mode, offset: Int): Int = when (mode) {
            Mode.BEAT_7K, Mode.BEAT_14K -> CHANNEL_ASSIGN_BEAT7_P2[offset]
            Mode.POPN_9K -> CHANNEL_ASSIGN_POPN_P2[offset]
            else -> CHANNEL_ASSIGN_BEAT5_P2[offset]
        }

        /**
         * [within] iterates through the channel definition ranges and returns some information about[channel]:
         *
         * - Is it a P2 side channel?
         * - What's the offset value from [channel]'s base
         * - What's the node type it represents
         *
         * If [channel] is not within any ranges, a null would be returned
         */
        private fun within(channel: Int): Triple<Boolean, Int, NoteType>? {
            return when (channel) {
                in ChannelDef.P1_KEY_RANGE -> Triple(false, channel - ChannelDef.P1_KEY_BASE.value, NoteType.Normal)
                in ChannelDef.P2_KEY_RANGE -> Triple(true, channel - ChannelDef.P2_KEY_BASE.value, NoteType.Normal)
                in ChannelDef.P1_INVISIBLE_KEY_RANGE -> Triple(
                    false,
                    channel - ChannelDef.P1_INVISIBLE_KEY_BASE.value,
                    NoteType.Invisible
                )

                in ChannelDef.P2_INVISIBLE_KEY_RANGE -> Triple(
                    true,
                    channel - ChannelDef.P2_INVISIBLE_KEY_BASE.value,
                    NoteType.Invisible
                )

                in ChannelDef.P1_LONG_KEY_RANGE -> Triple(
                    false,
                    channel - ChannelDef.P1_LONG_KEY_BASE.value,
                    NoteType.Long
                )

                in ChannelDef.P2_LONG_KEY_RANGE -> Triple(
                    true,
                    channel - ChannelDef.P2_LONG_KEY_BASE.value,
                    NoteType.Long
                )

                in ChannelDef.P1_MINE_KEY_RANGE -> Triple(
                    false,
                    channel - ChannelDef.P1_MINE_KEY_BASE.value,
                    NoteType.Mine
                )

                in ChannelDef.P2_MINE_KEY_RANGE -> Triple(
                    true,
                    channel - ChannelDef.P2_MINE_KEY_BASE.value,
                    NoteType.Mine
                )

                else -> null
            }
        }

        /**
         * [create] constructs a [ChannelLane]
         *
         * @return There're 3 possible return values:
         *
         * - A null value, which means this [channel] is not a 'note'
         * - A [ChannelLane], but [bms.model.ChannelLane.lane] is -1, which means this is a corrupted channel message
         * - Otherwise, it's a playable 'note'
         */
        fun create(mode: Mode, channel: Int): ChannelLane? {
            return when (channel) {
                in ChannelDef.P1_KEY_RANGE -> ChannelLane(
                    channel,
                    getP1SideLane(
                        mode, channel - ChannelDef.P1_KEY_BASE.value,
                    ),
                    NoteType.Normal
                )

                in ChannelDef.P2_KEY_RANGE -> ChannelLane(
                    channel,
                    getP2SideLane(
                        mode, channel - ChannelDef.P2_KEY_BASE.value
                    ),
                    NoteType.Normal
                )

                in ChannelDef.P1_LONG_KEY_RANGE -> ChannelLane(
                    channel,
                    getP1SideLane(
                        mode, channel - ChannelDef.P1_LONG_KEY_BASE.value
                    ),
                    NoteType.Long
                )

                in ChannelDef.P2_LONG_KEY_RANGE -> ChannelLane(
                    channel,
                    getP2SideLane(
                        mode, channel - ChannelDef.P2_LONG_KEY_BASE.value
                    ),
                    NoteType.Long
                )

                in ChannelDef.P1_INVISIBLE_KEY_RANGE -> ChannelLane(
                    channel,
                    getP1SideLane(
                        mode, channel - ChannelDef.P1_INVISIBLE_KEY_BASE.value
                    ),
                    NoteType.Invisible
                )

                in ChannelDef.P2_INVISIBLE_KEY_RANGE -> ChannelLane(
                    channel,
                    getP2SideLane(
                        mode, channel - ChannelDef.P2_INVISIBLE_KEY_BASE.value
                    ),
                    NoteType.Invisible
                )

                in ChannelDef.P1_MINE_KEY_RANGE -> ChannelLane(
                    channel,
                    getP1SideLane(
                        mode, channel - ChannelDef.P1_MINE_KEY_BASE.value
                    ),
                    NoteType.Mine
                )

                in ChannelDef.P2_MINE_KEY_RANGE -> ChannelLane(
                    channel,
                    getP2SideLane(
                        mode, channel - ChannelDef.P2_MINE_KEY_BASE.value
                    ),
                    NoteType.Mine
                )

                else -> null
            }
        }
    }
}

/**
 * BMS format note types
 */
private enum class NoteType {
    Normal,
    Long,
    Invisible,
    Mine
}
