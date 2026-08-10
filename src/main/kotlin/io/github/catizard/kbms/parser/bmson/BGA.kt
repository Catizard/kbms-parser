package io.github.catizard.kbms.parser.bmson

import bms.model.LayerSequence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BGA(
    @SerialName("bga_header") val bgaHeader: Array<BGAHeader> = emptyArray(),
    @SerialName("bga_sequence") val bgaSequence: Array<BGASequence> = emptyArray(),
    @SerialName("bga_events") val bgaEvents: Array<BNote> = emptyArray(),
    @SerialName("layer_events") val layerEvents: Array<BNote> = emptyArray(),
    @SerialName("poor_events") val poorEvents: Array<BNote> = emptyArray(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BGA

        if (!bgaHeader.contentEquals(other.bgaHeader)) return false
        if (!bgaSequence.contentEquals(other.bgaSequence)) return false
        if (!bgaEvents.contentEquals(other.bgaEvents)) return false
        if (!layerEvents.contentEquals(other.layerEvents)) return false
        if (!poorEvents.contentEquals(other.poorEvents)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bgaHeader.contentHashCode()
        result = 31 * result + bgaSequence.contentHashCode()
        result = 31 * result + bgaEvents.contentHashCode()
        result = 31 * result + layerEvents.contentHashCode()
        result = 31 * result + poorEvents.contentHashCode()
        return result
    }
}

@Serializable
data class BGAHeader(
    val id: Int,
    val name: String
)

@Serializable
data class BGASequence(
    val id: Int,
    val sequence: Array<LayerSequence> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BGASequence

        if (id != other.id) return false
        if (!sequence.contentEquals(other.sequence)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + sequence.contentHashCode()
        return result
    }
}

@Serializable
data class BNote(
    val id: Int,
    @SerialName("id_set") val idSet: IntArray = intArrayOf(),
    val condition: String,
    val interval: Int
) : BMSONObject() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BNote

        if (id != other.id) return false
        if (interval != other.interval) return false
        if (!idSet.contentEquals(other.idSet)) return false
        if (condition != other.condition) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + interval
        result = 31 * result + idSet.contentHashCode()
        result = 31 * result + condition.hashCode()
        return result
    }
}