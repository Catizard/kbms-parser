package io.github.catizard.kbms.parser

import bms.model.BMSModel
import bms.model.ChartInformation
import bms.model.LongNoteDef
import io.github.catizard.kbms.parser.bms.BMSParser
import io.github.catizard.kbms.parser.bmson.BMSONParser
import io.github.catizard.kbms.parser.osu.OSUParser
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
                BMSONParser(config)
            } else if (s.endsWith(".bms") || s.endsWith(".bme") || s.endsWith(".bml") || s.endsWith(".pms")) {
                BMSParser(config)
            } else if (s.endsWith(".osu")) {
                OSUParser(config)
            } else {
                throw IllegalArgumentException("No related parser for file: $path")
            }
        }
    }

    fun parse(file: File): BMSModel {
        return parse(file.toPath())
    }

    fun parse(path: Path, selectedRandoms: List<Int>? = null): BMSModel {
        // Enforce LN mode if the parser sets to follow LR2 behavior
        val lnType = if (config.usingLR2Mode) {
            LongNoteDef.LONG_NOTE
        } else {
            config.lnType
        }
        return parse(ChartInformation(path = path, lnType = lnType, selectedRandoms = selectedRandoms))
    }

    abstract fun parse(info: ChartInformation): BMSModel
}

data class ChartParserConfig(
    val usingLR2Mode: Boolean = false,
    var lnType: LongNoteDef,
)

