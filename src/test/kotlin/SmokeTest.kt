import bms.model.BMSModel
import bms.model.LongNoteDef
import bms.model.note.LongNote
import bms.model.note.MineNote
import bms.model.note.Note
import io.github.catizard.jbms.parser.BMSDecoder
import io.github.catizard.kbms.parser.ChartParserConfig
import io.github.catizard.kbms.parser.bms.BMSParser
import io.github.catizard.kbms.parser.stun
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.github.catizard.jbms.parser.BMSModel as UpstreamBMSModel
import io.github.catizard.jbms.parser.LongNote as UpstreamLongNote
import io.github.catizard.jbms.parser.MineNote as UpstreamMineNote
import io.github.catizard.jbms.parser.Note as UpstreamNote

class SmokeTest {
    companion object {
        val logger = KotlinLogging.logger { }
    }

    @Test
    fun smokeTest() {
        val testFile = System.getenv("KBMS.TestFile") ?: return
        val parser = BMSParser(ChartParserConfig(true, LongNoteDef.LONG_NOTE))
        val model = parser.parse(Paths.get(testFile))
        println(model)
    }

    @Test
    fun clapTest() {
        val testDirectory = System.getenv("KBMS.TestDirectory") ?: return
        val files = fetchAllBMSFiles(File(testDirectory))
        val real = BMSParser(ChartParserConfig(true, LongNoteDef.LONG_NOTE))
        val upstream = BMSDecoder()
        files?.forEachIndexed { i, file ->
            logger.info { "Running ${i}th clap test, file at ${file.path}" }
            val (realModel, realCost) = {
                val stun = stun()
                val realModel = real.parse(file)
                val cost = stun()
                Pair(realModel, cost)
            }()
            val (expectedModel, expectedCost) = {
                val stun = stun()
                val expectedModel = upstream.decode(file)
                val cost = stun()
                Pair(expectedModel, cost)
            }()
            logger.info { "${i}th clap test ended. Real cost: ${realCost}ms, upstream cost: ${expectedCost}ms" }
            check(realModel, expectedModel)
        }
    }

    private fun fetchAllBMSFiles(directory: File): List<File> {
        val bmsFiles = mutableListOf<File>()

        if (!directory.exists() || !directory.isDirectory) {
            return bmsFiles
        }

        val interestedExts = setOf("bms", "bme", "bml", "pms")

        fun walkDir(currentDir: File) {
            val files = currentDir.listFiles() ?: return

            for (file in files) {
                if (file.isDirectory) {
                    walkDir(file)
                } else if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in interestedExts) {
                        bmsFiles.add(file)
                    }
                }
            }
        }

        walkDir(directory)
        return bmsFiles
    }

    private fun check(real: BMSModel, expected: UpstreamBMSModel) {
        assertEquals(expected.player, real.player)
        assertEquals(expected.mode.name, real.mode.name)
        assertEquals(expected.title, real.title)
        assertEquals(expected.subTitle, real.subTitle)
        assertEquals(expected.genre, real.genre)
        assertEquals(expected.artist, real.artist)
        assertEquals(expected.subArtist, real.subArtist)
        assertEquals(expected.banner, real.banner)
        assertEquals(expected.stagefile, real.stageFile)
        assertEquals(expected.backbmp, real.backBMP)
        assertEquals(expected.preview, real.preview)
        assertEquals(expected.bpm, real.bpm)


        assertEquals(expected.playlevel, real.playLevel)
        assertEquals(expected.difficulty, real.difficulty)
        assertEquals(expected.judgerank, real.judgeRank)
        assertEquals(expected.judgerankType.name, real.judgeRankType.name)
        assertEquals(expected.total, real.total)
        assertEquals(expected.totalType.name, real.totalType.name)
        assertEquals(expected.volwav, real.volWAV)
        assertEquals(expected.mD5, real.md5)
        assertEquals(expected.shA256, real.sha256)
        assertEquals(expected.base, real.base)
        // assertEquals(real.lnMode, expected.lnmode)
        assertEquals(expected.lnobj, real.lnObj)
        assertEquals(expected.values, real.values)

        assertEquals(expected.allTimeLines.size, real.timelines.size)

        real.timelines.forEachIndexed { i, realTimeline ->
            val expectedTimeline = expected.allTimeLines[i]

            assertEquals(expectedTimeline.microTime, realTimeline.microTime)
            assertEquals(expectedTimeline.section, realTimeline.section)
            assertEquals(expectedTimeline.sectionLine, realTimeline.hasSectionLine)
            assertEquals(expectedTimeline.bpm, realTimeline.bpm)
            assertEquals(expectedTimeline.microStop, realTimeline.microStop)
            assertEquals(expectedTimeline.scroll, realTimeline.scroll)
            assertEquals(expectedTimeline.bga, realTimeline.bgaID)
            assertEquals(expectedTimeline.layer, realTimeline.layer)

            assertEquals(expectedTimeline.notes.size, realTimeline.notes.size)
            realTimeline.notes.forEachIndexed { i, realNote ->
                val expectedNote = expectedTimeline.notes[i]
                if (realNote == null) {
                    assertNull(expectedNote)
                    return@forEachIndexed
                }
                checkNote(realNote, expectedNote)
            }
            assertEquals(expectedTimeline.hiddennotes.size, realTimeline.hiddenNotes.size)
            assertEquals(expectedTimeline.eventlayer.size, realTimeline.eventLayer.size)
            realTimeline.eventLayer.forEachIndexed { j, realLayer ->
                val expectedLayer = expectedTimeline.eventlayer[j]
                assertEquals(expectedLayer.event.type.name, realLayer.event.type.name)
                assertEquals(expectedLayer.event.interval, realLayer.event.interval)

                val realLayerSequence = realLayer.layerSequence.flatMap { innerList -> innerList.map { it } }
                val expectedLayerSequence = expectedLayer.sequence.flatMap { innerArray -> innerArray.map { it } }
                assertEquals(expectedLayerSequence.size, realLayerSequence.size)
                realLayerSequence.forEachIndexed { k, realSequence ->
                    val expectedSequence = expectedLayerSequence[k]
                    assertEquals(expectedSequence.id, realSequence.id)
                    assertEquals(expectedSequence.time, realSequence.time)
                }
            }
        }
    }

    private fun checkNote(real: Note, expected: UpstreamNote) {
        assertEquals(expected.section, real.section)
        assertEquals(expected.microTime, real.microTime)
        assertEquals(expected.wav, real.wav)
        assertEquals(expected.microStarttime, real.microStart)
        assertEquals(expected.microDuration, real.duration)
        assertEquals(expected.microPlayTime, real.microPlayTime)

        assertEquals(expected.layeredNotes.size, real.layeredNotes.size)
        real.layeredNotes.forEachIndexed { i, realLayeredNote ->
            val expectedLayeredNote = expected.layeredNotes[i]
            checkNote(realLayeredNote, expectedLayeredNote)
        }

        when (real) {
            is LongNote -> {
                val expectedLongNote = expected as UpstreamLongNote
                assertEquals(expectedLongNote.isEnd, real.end)
                // TODO: Check long note type here
            }

            is MineNote -> {
                val expectedMineNote = expected as UpstreamMineNote
                assertEquals(expectedMineNote.damage, real.damage)
            }
        }
    }
}