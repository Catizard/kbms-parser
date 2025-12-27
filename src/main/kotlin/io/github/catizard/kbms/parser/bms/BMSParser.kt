package io.github.catizard.kbms.parser.bms

import bms.model.BMSModel
import bms.model.ChartInformation
import bms.model.JudgeRankType
import bms.model.LongNoteDef
import bms.model.Mode
import bms.model.Timeline
import bms.model.TotalType
import io.github.catizard.kbms.parser.convertHexString
import io.github.catizard.kbms.parser.matchReservedWord
import bms.model.note.LongNote
import io.github.catizard.kbms.parser.parseInt62
import io.github.catizard.kbms.parser.ChartParser
import io.github.catizard.kbms.parser.ChartParserConfig
import io.github.catizard.kbms.parser.ParseContext
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*
import kotlin.math.abs

val logger = KotlinLogging.logger {}

class BMSParser(config: ChartParserConfig) : ChartParser(config) {
    /**
     * ## Parse progress
     *
     * The whole parse progress can be vaguely divided into 3 pieces:
     *
     * 1. Prepare the reader that reads the bms file from disk and calculates the hashes(md5 & sha256)
     * 2. Read the bms file line-by-line, collecting the meta data and the channel messages
     * 3. Build the timeline based on the channel messages
     */
    override fun parse(info: ChartInformation): BMSModel {
        logger.info { "Start parsing bms at ${info.path}" }
        val ctx = BMSParseContext(selectedRandoms = info.selectedRandoms).apply {
            val isPMS = info.path.toFile().toString().endsWith(".pms")
            if (isPMS) {
                playMode = Mode.POPN_5K
            }
        }

        val md5Digest = MessageDigest.getInstance("MD5")
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val data = Files.readAllBytes(info.path)
        val duplexDigestInputStream = DigestInputStream(
            DigestInputStream(
                ByteArrayInputStream(data), md5Digest
            ), sha256Digest
        )

        val bmsLineParser = BMSLineParser()

        // The BMS file format doesn't have a strong restriction about the lines' order, so we
        //  couldn't do any optimization based on the envision like "out-of-order" around here.
        // TODO: Can we speed up the file read progress?
        BufferedReader(InputStreamReader(duplexDigestInputStream, "MS932")).use { br ->
            var line = ""
            while (br.readLine()?.also { line = it } != null) {
                if (line.length < 2) {
                    continue
                }
                bmsLineParser.parse(ctx, line)
            }
        }

        // NOTE: We must call this function after all the section lines are collected
        parseChannelMessages(ctx)

        // Finishing work
        if (ctx.totalType != TotalType.BMS) {
            logger.warn { "Total type is not defined" }
        }

        if (ctx.total <= 60.0) {
            logger.warn { "Total value is too low" }
        }

        if (ctx.player > 1 && (ctx.playMode == Mode.BEAT_5K || ctx.playMode == Mode.BEAT_7K)) {
            logger.warn { "#PLAYER定義が2以上にもかかわらず2P側のノーツ定義が一切ありません" }
        }

        if (ctx.player == 1 && (ctx.playMode == Mode.BEAT_10K || ctx.playMode == Mode.BEAT_14K)) {
            logger.warn { "#PLAYER定義が1にもかかわらず2P側のノーツ定義が存在します" }
        }

        val md5 = convertHexString(md5Digest.digest())
        val sha256 = convertHexString(sha256Digest.digest())


        val usedRandoms = ctx.selectedRandoms ?: ctx.randomRecord
        val outputInfo = ChartInformation(info.path, ctx.lnType, usedRandoms)
        return BMSModel(ctx, outputInfo, md5, sha256)
    }

    private fun parseChannelMessages(ctx: BMSParseContext) {
        var prev: Section? = null
        val timelines = TreeMap<Double, TimelineCache>()
        val sections = (0..ctx.maxTrack).map { track ->
            val channelMessages = ctx.getChannelMessages(track)
            val next = Section(prev, ctx, channelMessages, timelines)
            prev = next
            next
        }

        val lnList: Array<MutableList<LongNote>?> = arrayOfNulls(ctx.playMode.key)
        val lnEndStatus: Array<LongNote?> = arrayOfNulls(ctx.playMode.key)
        val baseTimeline = Timeline(0.0, 0, ctx.playMode.key, ctx.bpm)
        timelines[0.0] = TimelineCache(0.0, baseTimeline)
        sections.forEach { section -> section.processTimeline(ctx, lnList, lnEndStatus) }
        ctx.timelines.addAll(timelines.values.map { tlc -> tlc.timeline })
        if (ctx.timelines[0].bpm == 0.0) {
            throw IllegalArgumentException("Failed to parse BMS: initial bpm is corrupted")
        }

        lnEndStatus.forEachIndexed { lane, note ->
            note ?: return@forEachIndexed

            logger.warn { "Lane ${lane + 1} has uncompleted long note" }
            if (note.section != Double.MIN_VALUE) {
                timelines[note.section]?.timeline?.setNote(lane, null)
            }
        }

        ctx.timelines.lastOrNull()?.let {
            if (it.milliTime >= ctx.getLastMilliTime() + 30000) {
                logger.warn { "最後のノート定義から30秒以上の余白があります" }
            }
        }
    }
}

class BMSParseContext(
    selectedRandoms: List<Int>? = null,
    var player: Int = 0,
    var genre: String = "",
    var title: String = "",
    var subTitle: String = "",
    var artist: String = "",
    var subArtist: String = "",
    var playLevel: String = "",
    var judgeRank: Int = 2,
    var judgeRankType: JudgeRankType = JudgeRankType.BMS_RANK,
    var total: Double = 100.0,
    var totalType: TotalType = TotalType.BMSON,
    var volWAV: Int = 0,
    var stageFile: String = "",
    var backBMP: String = "",
    var preview: String = "",
    var lnObj: Int = -1,
    var lnMode: LongNoteDef = LongNoteDef.UNDEFINED,
    var difficulty: Int = 0,
    var banner: String = "",
) : ParseContext(selectedRandoms = selectedRandoms) {
    /**
     * Track => channel messages
     *
     * Example:
     * Suppose we have below 3 command lines:
     *
     * - #00211:03030303
     * - #00211:01010101
     * - #00311:03030303
     *
     * Then the value of `channelMessages` is:
     * - 2 => ("#00211:03030303", "#00211:01010101")
     * - 3 => ("#00311:03030303")
     */
    private val channelMessages: MutableMap<Int, MutableList<String>> = mutableMapOf()

    /**
     * The maximum track value that has been registered
     */
    var maxTrack: Int = 0
        private set

    fun getChannelMessages(track: Int): List<String>? = channelMessages[track]

    fun registerChannelMessage(track: Int, line: String) {
        maxTrack = maxTrack.coerceAtLeast(track)
        channelMessages.putIfAbsent(track, mutableListOf())
        channelMessages[track]!!.add(line)
    }
}

private interface LineParser {
    fun parse(ctx: BMSParseContext, line: String): Boolean
}

private class BMSLineParser : LineParser {
    private val extraValueLineParser = ExtraValueLineParser()
    private val commandLineParser = CommandLineParser()

    override fun parse(ctx: BMSParseContext, line: String): Boolean {
        when (line[0]) {
            '%', '@' -> extraValueLineParser.parse(ctx, line)
            '#' -> commandLineParser.parse(ctx, line)
        }
        return true
    }
}

private class ExtraValueLineParser : LineParser {
    override fun parse(ctx: BMSParseContext, line: String): Boolean {
        val index = line.indexOf(' ')
        if (index > 0 && index + 1 < line.length) {
            ctx.extraValues[line.substring(1, index)] = line.substring(index + 1)
        }
        return true
    }
}

private class CommandLineParser : LineParser {
    private val controlCommandParser = ControlCommandParser()
    private val channelMessageCollector = ChannelMessageCollector()
    private val resourceLineParser = ResourceLineParser()
    private val metadataLineParser = MetaDataLineParser()

    override fun parse(ctx: BMSParseContext, line: String): Boolean {
        if (controlCommandParser.parse(ctx, line)) {
            return true
        }
        if (ctx.shouldSkip()) {
            return true
        }
        if (channelMessageCollector.parse(ctx, line)) {
            return true
        }
        if (resourceLineParser.parse(ctx, line)) {
            return true
        }
        if (metadataLineParser.parse(ctx, line)) {
            return true
        }
        logger.error { "Skipping command $line" }
        return false
    }
}

private class ControlCommandParser : LineParser {
    override fun parse(ctx: BMSParseContext, line: String): Boolean {
        line.matchReservedWord("RANDOM")?.let { r ->
            val r = r.toIntOrNull()
            if (r == null) {
                logger.warn { "NaN argument passed to #RANDOM" }
            } else {
                ctx.pushNextRandom(r)
            }
            return true
        }
        line.matchReservedWord("ENDRANDOM")?.let {
            ctx.popRandom()
            return true
        }
        line.matchReservedWord("IF")?.let { r ->
            val r = r.toIntOrNull()
            if (r == null) {
                logger.warn { "NaN argument passed to #IF" }
            } else {
                ctx.pushSkipFlag(r)
            }
            return true
        }
        line.matchReservedWord("ENDIF")?.let {
            ctx.popSkipFlag()
            return true
        }
        return false
    }
}

/**
 * NOTE: This parser doesn't actually parse the channel message, it only collects the lines
 *  This is because we cannot do the actual task before we had collected all lines.
 *  For actual channel message parser see ChannelMessageParser
 */
private class ChannelMessageCollector : LineParser {
    override fun parse(ctx: BMSParseContext, line: String): Boolean {
        val c1 = line[1]
        if (c1 in '0'..'9' && line.length > 6) {
            val c2 = line[2]
            val c3 = line[3]
            if (c2 in '0'..'9' && c3 in '0'..'9') {
                val track = (c1 - '0') * 100 + (c2 - '0') * 10 + (c3 - '0')
                ctx.registerChannelMessage(track, line)
            }
            return true
        } else {
            return false
        }
    }
}

private class ResourceLineParser : LineParser {
    override fun parse(ctx: BMSParseContext, line: String): Boolean {
        line.matchReservedWord("BPM")?.let {
            if (line[4] == ' ') {
                val bpm = line.substring(5).trim().toDoubleOrNull()
                if (bpm == null) {
                    logger.warn { "NaN argument passed to #BPM" }
                } else {
                    if (bpm > 0) {
                        ctx.bpm = bpm
                    } else {
                        logger.warn { "Negative BPM" }
                    }
                }
            } else {
                val bpm = line.substring(7).trim().toDoubleOrNull()
                if (bpm == null) {
                    logger.warn { "NaN argument passed to #BPM" }
                } else {
                    if (bpm > 0) {
                        ctx.pushBPM(line[4], line[5], bpm)
                    } else {
                        logger.warn { "Negative BPM" }
                    }
                }
            }
            return true
        }
        line.matchReservedWord("WAV")?.let {
            if (line.length >= 8) {
                val fileName = line.substring(7).trim().replace('\\', '/')
                ctx.registerWAV(line[4], line[5], fileName)
            } else {
                logger.warn { "Corrupted #WAV command" }
            }
            return true
        }
        line.matchReservedWord("BMP")?.let {
            if (line.length >= 8) {
                val fileName = line.substring(7).trim().replace('\\', '/')
                ctx.registerBMP(line[4], line[5], fileName)
            } else {
                logger.warn { "Corrupted #BMP command" }
            }
            return true
        }
        line.matchReservedWord("STOP")?.let {
            if (line.length >= 9) {
                val stop = (line.substring(8).trim().toDouble() / 192).let {
                    if (it < 0) {
                        logger.warn { "Negative #STOP" }
                        abs(it)
                    } else {
                        it
                    }
                }
                ctx.registerStop(line[5], line[6], stop)
            } else {
                logger.warn { "Corrupted #STOP command" }
            }
            return true
        }
        line.matchReservedWord("SCROLL")?.let {
            if (line.length >= 11) {
                val scroll = line.substring(10).trim().toDouble()
                ctx.registerScroll(line[7], line[8], scroll)
            } else {
                logger.warn { "Corrupted #SCROLL" }
            }
            return true
        }

        return false
    }
}

/**
 * TODO: Do we have a chance to speed up this process?
 */
private class MetaDataLineParser : LineParser {
    override fun parse(ctx: BMSParseContext, line: String): Boolean {
        for (cw in CommandWord.entries) {
            if (line.length > cw.name.length + 2) {
                line.matchReservedWord(cw.name)?.let { arg ->
                    cw.action(ctx, arg)
                    break
                }
            }
        }
        return true
    }
}

private enum class CommandWord(val action: (ctx: BMSParseContext, arg: String) -> Unit) {
    PLAYER({ ctx: BMSParseContext, arg: String ->
        when (val player = arg.toIntOrNull()) {
            null -> logger.warn { "NaN passed as argument to #PLAYER" }
            in 1..<3 -> ctx.player = player
            else -> logger.warn { "Unexpected value passed to #PLAYER" }
        }
    }),
    GENRE({ ctx: BMSParseContext, arg: String -> ctx.genre = arg }),
    TITLE({ ctx: BMSParseContext, arg: String -> ctx.title = arg }),
    SUBTITLE({ ctx: BMSParseContext, arg: String -> ctx.subTitle = arg }),
    ARTIST({ ctx: BMSParseContext, arg: String -> ctx.artist = arg }),
    SUBARTIST({ ctx: BMSParseContext, arg: String -> ctx.subArtist = arg }),
    PLAYLEVEL({ ctx: BMSParseContext, arg: String -> ctx.playLevel = arg }),
    Rank({ ctx: BMSParseContext, arg: String ->
        when (val rank = arg.toIntOrNull()) {
            null -> logger.warn { "NaN passed as argument to #Rank" }
            in 0..<5 -> {
                ctx.judgeRank = rank
                ctx.judgeRankType = JudgeRankType.BMS_RANK
            }

            else -> logger.warn { "Unexpected value passed to #Rank" }

        }
    }),
    DEFEXRANK({ ctx: BMSParseContext, arg: String ->
        when (val rank = arg.toIntOrNull()) {
            null -> logger.warn { "NaN passed as argument to #DEFEXRANK" }
            in 0..Int.MAX_VALUE -> {
                ctx.judgeRank = rank
                ctx.judgeRankType = JudgeRankType.BMS_DEFEXRANK
            }

            else -> logger.warn { "Unexpected value passed to #DEFEXRANK" }
        }
    }),
    TOTAL({ ctx: BMSParseContext, arg: String ->
        val total = arg.toDoubleOrNull()
        if (total == null) {
            logger.warn { "NaN passed as argument to #TOTAL" }
        } else if (total > 0) {
            ctx.total = total
            ctx.totalType = TotalType.BMS
        } else {
            logger.warn { "Unexpected value passed to #TOTAL" }
        }
    }),
    VOLWAV({ ctx: BMSParseContext, arg: String ->
        when (val volWAV = arg.toIntOrNull()) {
            null -> logger.warn { "NaN passed as argument to #VOLWAV" }
            else -> ctx.volWAV = volWAV
        }
    }),
    STAGEFILE({ ctx: BMSParseContext, arg: String ->
        ctx.stageFile = arg.replace('\\', '/')
    }),
    BACKBMP({ ctx: BMSParseContext, arg: String ->
        ctx.backBMP = arg.replace('\\', '/')
    }),
    PREVIEW({ ctx: BMSParseContext, arg: String ->
        ctx.preview = arg.replace('\\', '/')
    }),
    LNOBJ({ ctx: BMSParseContext, arg: String ->
        if (ctx.base == 62) {
            val lnObj = parseInt62(arg[0], arg[1])
            if (lnObj == null) {
                logger.warn { "NaN passed as argument to #LNOBJ" }
            } else {
                ctx.lnObj = lnObj
            }
        } else {
            ctx.lnObj = Integer.parseInt(arg, 36)
        }
    }),
    LNMODE({ ctx: BMSParseContext, arg: String ->
        val lnMode = arg.toIntOrNull()
        if (lnMode == null) {
            logger.warn { "NaN passed as argument to #LNMODE" }
        } else {
            if (lnMode !in 0..3) {
                logger.warn { "Unexpected value passed to #LNMODE" }
            } else {
                ctx.lnMode = LongNoteDef.Companion.fromLNMode(lnMode)!!
            }
        }
    }),
    DIFFICULTY({ ctx: BMSParseContext, arg: String ->
        val difficulty = arg.toIntOrNull()
        if (difficulty == null) {
            logger.warn { "NaN passed as argument to #DIFFICULTY" }
        } else {
            ctx.difficulty = difficulty
        }
    }),
    BANNER({ ctx: BMSParseContext, arg: String ->
        ctx.banner = arg.replace('\\', '/')
    }),
    BASE({ ctx: BMSParseContext, arg: String ->
        val base = arg.toIntOrNull()
        if (base == null) {
            logger.warn { "NaN passed as argument to #BASE" }
        } else if (base != 62 && base != 36) {
            logger.warn { "Unexpected value passed to #BASE" }
        } else {
            ctx.base = base
        }
    })
}

data class TimelineCache(val time: Double, val timeline: Timeline)
