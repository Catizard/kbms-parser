package bms.model

import java.util.*

data class Lane(val model: BMSModel, val lane: Int) {
    val notes: Array<Note?>
    private var notebasepos = 0
    private var noteseekpos = 0

    val hiddens: Array<Note?>
    private var hiddenbasepos = 0
    private var hiddenseekpos = 0

    init {
        val note: MutableCollection<Note?> = ArrayDeque<Note?>()
        val hnote: MutableCollection<Note?> = ArrayDeque<Note?>()
        for (tl in model.getAllTimelines()) {
            if (tl.existNote(lane)) {
                note.add(tl.getNote(lane))
            }
            if (tl.getHiddenNote(lane) != null) {
                hnote.add(tl.getHiddenNote(lane))
            }
        }
        notes = note.toTypedArray<Note?>()
        hiddens = hnote.toTypedArray<Note?>()
    }

    val note: Note?
        get() {
            if (noteseekpos < notes.size) {
                return notes[noteseekpos++]
            }
            return null
        }

    val hidden: Note?
        get() {
            if (hiddenseekpos < hiddens.size) {
                return hiddens[hiddenseekpos++]
            }
            return null
        }

    fun reset() {
        noteseekpos = notebasepos
        hiddenseekpos = hiddenbasepos
    }

    fun mark(time: Int) {
        while (notebasepos < notes.size - 1 && notes[notebasepos + 1]!!.getTime() < time) {
            notebasepos++
        }
        while (notebasepos > 0 && notes[notebasepos]!!.getTime() > time) {
            notebasepos--
        }
        noteseekpos = notebasepos
        while (hiddenbasepos < hiddens.size - 1
            && hiddens[hiddenbasepos + 1]!!.getTime() < time
        ) {
            hiddenbasepos++
        }
        while (hiddenbasepos > 0 && hiddens[hiddenbasepos]!!.getTime() > time) {
            hiddenbasepos--
        }
        hiddenseekpos = hiddenbasepos
    }
}