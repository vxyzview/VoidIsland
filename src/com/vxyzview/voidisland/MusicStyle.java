package com.vxyzview.voidisland;

/** The names and descriptions of the music designs (Style > Music). Pure Java, so the unit checks can cover it; the views are in {@link Music}. */
final class MusicStyle {
    private MusicStyle() {}

    static final int COMPACT = 5, EXPANDED = 5;

    static final String[] CNAMES = {"Classic", "Vinyl", "Ring", "Wave", "Song"};
    static final String[] CDESC = {
        "Round-cornered art on the left, the lyric line (or four dancing bars) on the right.",
        "A little record that spins while the song plays, with the lyric line or the bars beside it.",
        "Round art inside a ring that fills as the song goes on, with the lyric line or the bars beside it.",
        "Round art and a wide, rolling waveform. The lyric line takes its place when the song has one.",
        "The title and artist on the pill instead of lyrics, with four small bars."};
    static final String[] ENAMES = {"Classic", "Vinyl", "Poster", "Mini", "Lyrics"};
    static final String[] EDESC = {
        "Art, title and artist, the seek bar and the three buttons.",
        "A record that spins under a tonearm which lowers when the song plays and lifts when it pauses.",
        "Big centred art in a soft accent frame, the title below it, then the seek bar and buttons.",
        "One slim row: art, title and artist and the buttons, with a thin seek bar underneath.",
        "The song's lines in the middle of the card, the current one big, with the seek bar and buttons."};
}
