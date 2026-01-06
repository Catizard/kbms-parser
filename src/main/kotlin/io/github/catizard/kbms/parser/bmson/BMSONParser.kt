package io.github.catizard.kbms.parser.bmson

import bms.model.*
import io.github.catizard.kbms.parser.ChartParser
import io.github.catizard.kbms.parser.ChartParserConfig
import io.github.catizard.kbms.parser.ParseContext
import io.github.catizard.kbms.parser.bms.TimelineCache
import io.github.catizard.kbms.parser.convertHexString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedInputStream
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*

private val logger = KotlinLogging.logger {}

class BMSONParser(config: ChartParserConfig): ChartParser(config) {
    companion object {
        val Json = Json { ignoreUnknownKeys = true }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun parse(info: ChartInformation): BMSModel {
        val md5Digest = MessageDigest.getInstance("MD5")
        val sha256Digest = MessageDigest.getInstance("SHA-256")

        val bmson = Json.decodeFromStream<Bmson>(
            DigestInputStream(
                DigestInputStream(
                    BufferedInputStream(Files.newInputStream(info.path)),
                    md5Digest
                ),
                sha256Digest
            )
        )
        val sha256 = convertHexString(sha256Digest.digest())
        val md5 = convertHexString(md5Digest.digest())

        val ctx = BMSONParseContext(config)

        val baseTimeline = Timeline(0.0, 0, ctx.keys, ctx.bpm)
        ctx.timelineCaches[0] = TimelineCache(0.0, baseTimeline)

        var bpmpos = 0
        var stoppos = 0
        var scrollpos = 0
        Arrays.sort(bmson.bpmEvents, BMSONObject::compareTo)
        Arrays.sort(bmson.stopEvents, BMSONObject::compareTo)
        Arrays.sort(bmson.scrollEvents, BMSONObject::compareTo)

        while (bpmpos < bmson.bpmEvents.size || stoppos < bmson.stopEvents.size || scrollpos < bmson.scrollEvents.size) {
            val bpmy = if (bpmpos < bmson.bpmEvents.size) bmson.bpmEvents[bpmpos].y else Int.MAX_VALUE
            val stopy = if (stoppos < bmson.stopEvents.size) bmson.stopEvents[stoppos].y else Int.MAX_VALUE
            val scrolly = if (scrollpos < bmson.scrollEvents.size) bmson.scrollEvents[scrollpos].y else Int.MAX_VALUE
            if (scrolly <= stopy && scrolly <= bpmy) {
                ctx.getTimeline(scrolly).scroll = bmson.scrollEvents[scrollpos].rate
                scrollpos++
            } else if (bpmy <= stopy) {
                // NOTE: If there's a bpm event and a stop event happens on exactly same time point (i.e. same y value)
                //  We must execute the bpm change first, because the stop event's actual duration is calculated
                //  base on the bpm
                if (bmson.bpmEvents[bpmpos].bpm > 0) {
                    ctx.getTimeline(bpmy).bpm = bmson.bpmEvents[bpmpos].bpm
                } else {
                    logger.warn { "negative BPMはサポートされていません - y : ${bmson.bpmEvents[bpmpos].y}, bpm : ${bmson.bpmEvents[bpmpos].bpm}" }
                }
                bpmpos++
            } else {
                if (bmson.stopEvents[stoppos].duration >= 0) {
                    val tl = ctx.getTimeline(stopy)
                    // NOTE: The factor 4 here is because resolution is multiplied by 4 at the beginning
                    tl.microStop = ((1000.0 * 1000 * 60 * 4 * bmson.stopEvents[stoppos].duration)
                            / (tl.bpm * ctx.resolution)).toLong()
                } else {
                    logger.warn { "negative STOPはサポートされていません - y : ${bmson.stopEvents[stoppos].y}, bpm : ${bmson.stopEvents[stoppos].duration}" }
                }
                stoppos++
            }
        }

        bmson.lines.forEach { barLine ->
            ctx.getTimeline(barLine.y).hasSectionLine = true
        }

        parseSoundChannels(ctx, bmson.soundChannels)
        processKeyChannels(ctx, bmson.keyChannels)
        processMineChannels(ctx, bmson.mineChannels)
        processBGA(ctx, bmson.bga)

        ctx.timelines.addAll(ctx.timelineCaches.values.map { tlc -> tlc.timeline })

        val newInfo = ChartInformation(info.path, ctx.lnType, null)
        return BMSModel(ctx, newInfo, md5, sha256)
    }

    private fun processBGA(ctx: BMSONParseContext, bga: BGA) {
        // BGA処理
        val bgamap = arrayOfNulls<String>(bga.bgaHeader.size)
        val idmap: MutableMap<Int?, Int?> = HashMap<Int?, Int?>(bga.bgaHeader.size)
        val seqmap: MutableMap<Int, List<LayerSequence>> = mutableMapOf()
        for (i in bga.bgaHeader.indices) {
            val bh: BGAHeader = bga.bgaHeader[i]
            bgamap[i] = bh.name
            idmap[bh.id] = i
        }
        for (n in bga.bgaSequence) {
            val sequence: MutableList<LayerSequence> = ArrayList(n.sequence.size)
            for (i in sequence.indices) {
                val seq: LayerSequence = n.sequence[i]
                if (seq.id != Int.MIN_VALUE) {
                    sequence[i] = LayerSequence(seq.time, seq.id)
                } else {
                    sequence[i] = LayerSequence.endSequence(seq.time)
                }
            }
            seqmap[n.id] = sequence
        }
        for (n in bga.bgaEvents) {
            ctx.getTimeline(n.y).bgaID = idmap[n.id]!!
        }
        for (n in bga.layerEvents) {
            val idset = n.idSet
            val seqs: MutableList<List<LayerSequence>> = ArrayList<List<LayerSequence>>(seqmap.size)
            var event: Event? = null
            when (n.condition) {
                "play" -> event = Event(
                    EventType.PLAY,
                    n.interval
                )

                "miss" -> event = Event(
                    EventType.MISS,
                    n.interval
                )

                else -> event = Event(
                    EventType.ALWAYS,
                    n.interval
                )
            }
            for (seqindex in seqs.indices) {
                val nid = idset[seqindex]
                if (seqmap.containsKey(nid)) {
                    seqs[seqindex] = seqmap[nid]!!
                } else {
                    seqs[seqindex] = listOf(
                        LayerSequence(0, idmap[n.id]!!), LayerSequence.endSequence(500)
                    )
                }
            }
            ctx.getTimeline(n.y).eventLayer = listOf(Layer(event, seqs))

        }
        for (n in bga.poorEvents) {
            if (seqmap.containsKey(n.id)) {
                ctx.getTimeline(n.y).eventLayer = listOf(
                    Layer(Event(EventType.MISS, 1), listOf(seqmap[n.id]!!))
                )
            } else {
                ctx.getTimeline(n.y).eventLayer =
                    listOf<Layer>(
                        Layer(
                            Event(EventType.MISS, 1),
                            listOf(
                                listOf(
                                    LayerSequence(0, idmap[n.id]!!),
                                    LayerSequence.endSequence(500)
                                )
                            )
                        )
                    )
            }
        }
    }

    private fun processMineChannels(ctx: BMSONParseContext, mineChannels: Array<MineChannel>) {
        for (sc in mineChannels) {
            ctx.registerWAV(sc.name)
            Arrays.sort(sc.notes, BMSONObject::compareTo)
            val length: Int = sc.notes.size
            for (i in 0..<length) {
                val n: MineNote = sc.notes[i]
                val tl: Timeline = ctx.getTimeline(n.y)

                ctx.getKeyLane(n.x)?.let { key: Int ->
                    var insideln = false
                    if (ctx.lnList[key] != null) {
                        val section: Double = (n.y / ctx.resolution)
                        for (ln in ctx.lnList[key]!!) {
                            if (ln.section < section && section <= ln.pair!!.section) {
                                insideln = true
                                break
                            }
                        }
                    }

                    if (insideln) {
                        logger.warn { "LN内に地雷ノートを定義しています - x : ${n.x} y : ${n.y}" }
                    } else if (tl.existNote(key)) {
                        logger.warn { "地雷ノートを定義している位置に通常ノートが存在します - x : ${n.x} y : ${n.y}" }
                    } else {
                        tl.setNote(key, MineNote(ctx.id, 0, 0, n.damage))
                    }
                }
            }
            ctx.id++
        }
    }

    private fun processKeyChannels(ctx: BMSONParseContext, keyChannels: Array<MineChannel>) {
        for (sc in keyChannels) {
            ctx.registerWAV(sc.name)
            Arrays.sort<MineNote?>(sc.notes, BMSONObject::compareTo)
            val length: Int = sc.notes.size
            for (i in 0..<length) {
                val n: MineNote = sc.notes[i]
                val tl: Timeline = ctx.getTimeline(n.y)

                ctx.getKeyLane(n.x)?.let { key: Int ->
                    // BGノート
                    tl.setHiddenNote(key, NormalNote(ctx.id))
                }
            }
            ctx.id++
        }
    }

    private fun parseSoundChannels(ctx: BMSONParseContext, soundChannels: Array<SoundChannel>) {
        var startTime = 0L
        for (sc in soundChannels) {
            ctx.registerWAV(sc.name)
            Arrays.sort(sc.notes, BMSONObject::compareTo)
            for (i in sc.notes.indices) {
                val n: Note = sc.notes[i]
                var next: Note? = null
                for (j in i + 1..<sc.notes.size) {
                    if (sc.notes[j].y > n.y) {
                        next = sc.notes[j]
                        break
                    }
                }
                var duration: Long = 0
                if (!n.c) {
                    startTime = 0
                }
                val tl: Timeline = ctx.getTimeline(n.y)
                if (next != null && next.c) {
                    duration = ctx.getTimeline(next.y).microTime - tl.microTime
                }

                val key = ctx.getKeyLane(n.x)
                if (key == null) {
                    // BGノート
                    tl.addBackgroundNote(NormalNote(ctx.id, startTime, duration))
                    startTime += duration
                    continue
                }

                if (n.up) {
                    // LN終端音定義
                    var assigned = false
                    if (ctx.lnList[key] != null) {
                        val section: Double = (n.y / ctx.resolution)
                        for (ln in ctx.lnList[key]!!) {
                            val pair = ln.pair!!
                            if (section == pair.section) {
                                pair.wav = ctx.id
                                pair.microStart = startTime
                                pair.duration = duration
                                assigned = true
                                break
                            }
                        }
                    }
                    if (!assigned) {
                        ctx.lnup[n] = LongNote(ctx.id, startTime, duration)
                    }
                    startTime += duration
                    continue
                }
                var insideln = false
                if (ctx.lnList[key] != null) {
                    val section: Double = (n.y / ctx.resolution)
                    for (ln in ctx.lnList[key]!!) {
                        if (ln.section < section && section <= ln.pair!!.section) {
                            insideln = true
                            break
                        }
                    }
                }

                if (insideln) {
                    logger.warn { "LN内にノートを定義しています - x : ${n.x}, y : ${n.y}" }
                    tl.addBackgroundNote(NormalNote(ctx.id, startTime, duration))
                } else {
                    if (n.l > 0) {
                        // ロングノート
                        val end: Timeline = ctx.getTimeline(n.y + n.l)
                        val ln = LongNote(ctx.id, startTime, duration)
                        if (tl.getNote(key) != null) {
                            // レイヤーノート判定
                            val en: bms.model.Note? = tl.getNote(key)
                            if (en is LongNote && end.getNote(key) === en.pair) {
                                en.addLayeredNote(ln)
                            } else {
                                logger.warn { "同一の位置にノートが複数定義されています - x : ${n.x} y : ${n.y}" }
                            }
                        } else {
                            var existNote = false
                            for (tl2 in ctx.timelineCaches.subMap(n.y, false, n.y + n.l, true).values) {
                                if (tl2.timeline.existNote(key)) {
                                    existNote = true
                                    break
                                }
                            }
                            if (existNote) {
                                logger.warn { "LN内にノートを定義しています - x : ${n.x} y : ${n.y}" }
                                tl.addBackgroundNote(
                                    NormalNote(
                                        ctx.id,
                                        startTime,
                                        duration
                                    )
                                )
                            } else {
                                tl.setNote(key, ln)
                                var lnend: LongNote? = null
                                for (up in ctx.lnup.entries) {
                                    if (up.key.y == n.y + n.l && up.key.x == n.x) {
                                        lnend = up.value
                                        break
                                    }
                                }
                                if (lnend == null) {
                                    lnend = LongNote(-2)
                                }

                                end.setNote(key, lnend)
                                ln.type = LongNoteDef.fromLNMode(n.t) ?: ctx.lnMode
                                ln.connectPair(lnend)
                                if (ctx.lnList[key] == null) {
                                    ctx.lnList[key] = ArrayList<LongNote>()
                                }
                                ctx.lnList[key]!!.add(ln)
                            }
                        }
                    } else {
                        // 通常ノート
                        if (tl.existNote(key)) {
                            if (tl.getNote(key) is NormalNote) {
                                tl.getNote(key)!!.addLayeredNote(
                                    NormalNote(
                                        ctx.id,
                                        startTime,
                                        duration
                                    )
                                )
                            } else {
                                logger.warn { "同一の位置にノートが複数定義されています - x : ${n.x} y : ${n.y}" }
                            }
                        } else {
                            tl.setNote(key, NormalNote(ctx.id, startTime, duration))
                        }
                    }
                    startTime += duration
                }
            }
            ctx.id++
        }
    }
}

@Serializable
data class Bmson(
    @SerialName("version") val version: String,
    @SerialName("info") val info: BMSInfo,
    @SerialName("lines") val lines: Array<BarLine> = emptyArray(),
    @SerialName("bpm_events") val bpmEvents: Array<BpmEvent> = emptyArray(),
    @SerialName("stop_events") val stopEvents: Array<StopEvent> = emptyArray(),
    @SerialName("scroll_events") val scrollEvents: Array<ScrollEvent> = emptyArray(),
    @SerialName("sound_channels") val soundChannels: Array<SoundChannel> = emptyArray() ,
    @SerialName("bga") val bga: BGA = BGA(),
    @SerialName("mine_channels") val mineChannels: Array<MineChannel> = emptyArray(),
    @SerialName("key_channels") val keyChannels: Array<MineChannel> = emptyArray(),
) {
    // Below functions are keeping same result with upstream

    fun getFullSubTitle(): String {
        val sb = StringBuilder(info.subTitle)
        if (info.subTitle.isNotEmpty() && info.chartName.isNotEmpty()) {
            sb.append(" ")
            sb.append("[").append(info.chartName).append("]")
        }
        return sb.toString()
    }

    fun getFullSubArtist(): String = info.subArtists.joinToString(",")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Bmson

        if (version != other.version) return false
        if (info != other.info) return false
        if (!lines.contentEquals(other.lines)) return false
        if (!bpmEvents.contentEquals(other.bpmEvents)) return false
        if (!stopEvents.contentEquals(other.stopEvents)) return false
        if (!scrollEvents.contentEquals(other.scrollEvents)) return false
        if (!soundChannels.contentEquals(other.soundChannels)) return false
        if (bga != other.bga) return false
        if (!mineChannels.contentEquals(other.mineChannels)) return false
        if (!keyChannels.contentEquals(other.keyChannels)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + info.hashCode()
        result = 31 * result + lines.contentHashCode()
        result = 31 * result + bpmEvents.contentHashCode()
        result = 31 * result + stopEvents.contentHashCode()
        result = 31 * result + scrollEvents.contentHashCode()
        result = 31 * result + soundChannels.contentHashCode()
        result = 31 * result + bga.hashCode()
        result = 31 * result + mineChannels.contentHashCode()
        result = 31 * result + keyChannels.contentHashCode()
        return result
    }
}

class BMSONParseContext(
    config: ChartParserConfig,
) : ParseContext(config = config) {
    val timelineCaches: TreeMap<Int, TimelineCache> = TreeMap()
    var resolution: Double = 0.0
    val lnList: Array<MutableList<LongNote>?> = arrayOfNulls(keys)
    val lnup: MutableMap<Note, LongNote> = mutableMapOf()
    var keyAssign: Array<Int?> = arrayOfNulls(0)

    var id = 0

    constructor(config: ChartParserConfig, bmson: Bmson) : this(config = config) {
        title = bmson.info.title
        subTitle = bmson.getFullSubTitle()
        artist = bmson.info.artist
        subArtist = bmson.getFullSubArtist()
        genre = bmson.info.genre
        playLevel = bmson.info.level.toString()
        lnMode = LongNoteDef.fromLNMode(bmson.info.lnType) ?: LongNoteDef.LONG_NOTE
        banner = bmson.info.bannerImage
        backBMP = bmson.info.backImage
        stageFile = bmson.info.eyecatchImage
        preview = bmson.info.previewMusic

        this.bpm = bmson.info.initBpm
        this.resolution = if (bmson.info.resolution > 0) bmson.info.resolution * 4.0 else 960.0
        this.total = bmson.info.total
        this.playMode = Mode.fromHint(bmson.info.modeHint) ?: Mode.BEAT_7K
        this.keyAssign = when (playMode) {
            Mode.BEAT_5K -> arrayOf(0, 1, 2, 3, 4, null, null, 5)
            Mode.BEAT_10K -> arrayOf(0, 1, 2, 3, 4, null, null, 5, 6, 7, 8, 9, 10, null, null)
            else -> (0..<playMode.key).map { i -> i }.toTypedArray()
        }

        if (bmson.info.judgeRank < 0) {
            logger.warn { "judge_rank is negative" }
        } else if (bmson.info.judgeRank < 5) {
            this.judgeRank = bmson.info.judgeRank
            this.judgeRankType = JudgeRankType.BMS_RANK
            logger.warn { "Unexpected value of judge_rank: $judgeRank, judge rank type would be set as bms rather than bmson" }
        } else {
            this.judgeRank = bmson.info.judgeRank
            this.judgeRankType = JudgeRankType.BMSON_JUDGERANK
        }

        if (bmson.info.total > 0) {
            this.total = bmson.info.total
            this.totalType = TotalType.BMSON
        } else {
            logger.warn { "total is negative" }
        }
    }

    fun registerWAV(wavFileName: String) {
        wavMap[id] = wavList.size
        wavList.add(wavFileName)
    }

    /**
     * TODO: The code here is almost identical compared to the code at [io.github.catizard.kbms.parser.bms.Section].
     *  The only difference is the key type here is [Int] while the other side is using [Double].
     *  So I currently cannot unify the two code into one.
     */
    fun getTimeline(y: Int): Timeline {
        // Timeをus単位にする場合はこのメソッド内部だけ変更すればOK
        val tlc = timelineCaches[y]
        if (tlc != null) {
            return tlc.timeline
        }

        val le = timelineCaches.lowerEntry(y)
        val bpm: Double = le.value.timeline.bpm
        val time: Double = (le.value.time + le.value.timeline.microStop
                + (240000.0 * 1000 * ((y - le.key!!) / resolution)) / bpm)

        val tl = Timeline(y / resolution, time.toLong(), keys, bpm)
        timelineCaches[y] = TimelineCache(time, tl)

        return tl
    }

    fun getKeyLane(x: Int): Int? {
        return if (x > 0 && x <= keyAssign.size) keyAssign[x - 1] else null
    }
}

@Serializable
data class BMSInfo(
    @SerialName("title") val title: String = "",
    @SerialName("subtitle") val subTitle: String = "",
    @SerialName("genre") val genre: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("sub_artists") val subArtists: Array<String> = emptyArray(),
    @SerialName("mode_hint") val modeHint: String = "beat-7k",
    @SerialName("chart_name") val chartName: String = "",
    @SerialName("judge_rank") val judgeRank: Int = 100,
    @SerialName("total") val total: Double = 100.0,
    @SerialName("init_bpm") val initBpm: Double,
    @SerialName("level") val level: Int,

    @SerialName("back_image") val backImage: String = "",
    @SerialName("eyecatch_image") val eyecatchImage: String = "",
    @SerialName("banner_image") val bannerImage: String = "",
    @SerialName("preview_music") val previewMusic: String = "",
    @SerialName("resolution") val resolution: Int = 240,
    @SerialName("ln_type") val lnType: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BMSInfo

        if (judgeRank != other.judgeRank) return false
        if (total != other.total) return false
        if (initBpm != other.initBpm) return false
        if (level != other.level) return false
        if (resolution != other.resolution) return false
        if (lnType != other.lnType) return false
        if (title != other.title) return false
        if (subTitle != other.subTitle) return false
        if (genre != other.genre) return false
        if (artist != other.artist) return false
        if (!subArtists.contentEquals(other.subArtists)) return false
        if (modeHint != other.modeHint) return false
        if (chartName != other.chartName) return false
        if (backImage != other.backImage) return false
        if (eyecatchImage != other.eyecatchImage) return false
        if (bannerImage != other.bannerImage) return false
        if (previewMusic != other.previewMusic) return false

        return true
    }

    override fun hashCode(): Int {
        var result = judgeRank
        result = 31 * result + total.hashCode()
        result = 31 * result + initBpm.hashCode()
        result = 31 * result + level
        result = 31 * result + resolution
        result = 31 * result + lnType
        result = 31 * result + title.hashCode()
        result = 31 * result + subTitle.hashCode()
        result = 31 * result + genre.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + subArtists.contentHashCode()
        result = 31 * result + modeHint.hashCode()
        result = 31 * result + chartName.hashCode()
        result = 31 * result + backImage.hashCode()
        result = 31 * result + eyecatchImage.hashCode()
        result = 31 * result + bannerImage.hashCode()
        result = 31 * result + previewMusic.hashCode()
        return result
    }
}