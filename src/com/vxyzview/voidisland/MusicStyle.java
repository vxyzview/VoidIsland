package com.vxyzview.voidisland;

/** The names and descriptions of the music designs (Style > Music). Pure Java, so the unit checks can cover it; the views are in {@link Music}. */
final class MusicStyle {
    private MusicStyle() {}

    static final int COMPACT = 8, EXPANDED = 8;

    static final String[] CNAMES = {"Classic", "Vinyl", "Ring", "Wave", "Song", "Track", "Beat", "Timer"};
    static final String[] CDESC = {
        "Round-cornered art on the left, the lyric line (or four dancing bars) on the right.",
        "A little record that spins while the song plays, with the lyric line or the bars beside it.",
        "Round art inside a ring that fills as the song goes on, with the lyric line or the bars beside it.",
        "Round art and a wide, rolling waveform. The lyric line takes its place when the song has one.",
        "The title and artist on the pill instead of lyrics, with four small bars.",
        "Flat and calm: rounded art, the title with a slim progress line under it, and a play / pause badge in the accent.",
        "Rounded art, the title and artist, and a little capsule on the right with a dot-matrix equalizer that dances to the beat.",
        "Round art, the title, and a tonal capsule with the time left in the song, counting down."};
    static final String[] ENAMES = {"Classic", "Vinyl", "Poster", "Mini", "Lyrics", "Bento", "Wave", "Cover"};
    static final String[] EDESC = {
        "Art, title and artist, the seek bar and the three buttons.",
        "A record that spins under a tonearm which lowers when the song plays and lifts when it pauses.",
        "Big centred art in a soft accent frame, the title below it, then the seek bar and buttons.",
        "One slim row: art, title and artist and the buttons, with a thin seek bar underneath.",
        "The song's lines in the middle of the card, the current one big, with the seek bar and buttons.",
        "Flat tonal tiles: big art beside a title tile, then the seek bar and three roomy buttons with an accent play key.",
        "A header with art and the song, a wide rolling waveform across the card, then the seek bar and buttons.",
        "Wide cover art with the title on a flat chip over it, an equalizer badge in the corner, then the seek bar and buttons."};
}
