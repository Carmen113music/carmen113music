package cat.riffs;

/**
 * Presets dels pads d'acords: 50 seqüències de 8 acords (pop/rock, indie i funk) que en Roger va preparar.
 * Són adaptacions per tocar (no transcripcions literals). Cada preset posa els 8 acords, en ordre, als pads 1–8.
 * No depèn d'Android.
 */
public final class AcordsPresets {

    private AcordsPresets() {}

    /** Títol, grup, tonalitat de referència i els 8 acords separats per comes (notació anglesa). */
    public static final String[][] LLISTA = {
            {"Creep", "Radiohead", "G", "G,B,C,Cm,G,Em,Am,D"},
            {"Where Is My Mind?", "Pixies", "E", "E,C#m,G#,A,E,B,C#m,A"},
            {"Boulevard of Broken Dreams", "Green Day", "F minor", "Fm,Db,Eb,Bb,Fm,Ab,Db,C"},
            {"Riptide", "Vance Joy", "Ab major", "Bbm,Ab,Db,Ab,Bbm,Fm,Db,Ab"},
            {"Take Me Out", "Franz Ferdinand", "F minor", "Fm,C,Ab,Eb,Fm,Db,Ab,C"},
            {"Seven Nation Army", "The White Stripes", "E minor / E", "E,G,A,B,E,D,A,E"},
            {"Dani California", "Red Hot Chili Peppers", "A minor", "Am,G,F,E,Am,C,Dm,E"},
            {"Come Together", "The Beatles", "D minor", "Dm,G,C,F,Dm,Am,G,A7"},
            {"(I Can’t Get No) Satisfaction", "The Rolling Stones", "E major", "E,A,D,A,E,B7,A,E"},
            {"Paranoid Android", "Radiohead", "C major / A minor", "C,D,Em,F,G,Am,F,E"},
            {"Mr. Brightside", "The Killers", "D major / B minor", "D,A,Bm,G,D,A,G,A"},
            {"Why’d You Only Call Me When You’re High?", "Arctic Monkeys", "F# minor", "Dmaj7,B7,F#m7,C#,Dmaj7,B7,F#m7,C#7"},
            {"Lost Kitten", "Metric", "C# minor", "C#m,F#m,E,Amaj7,C#m,G#m,A,B"},
            {"Naive", "The Kooks", "B major", "Bmaj7,E,G#m,F#,B,E,C#m,F#"},
            {"Don’t Look Back in Anger", "Oasis", "C major", "C,G,Am,E7,F,G,C,G"},
            {"Champagne Supernova", "Oasis", "A major / F# minor", "A,E,D,F#m,A,E,D,E"},
            {"Snap Out of It", "Arctic Monkeys", "Bb major / G minor", "Bb,Fm,Db,C,Bb,Eb,Gm,F"},
            {"I Love You", "Fontaines D.C.", "E minor", "Em,G,Am,C,Em,D,C,B7"},
            {"Arms", "The Paper Kites", "G major", "G,D,Bm,A,G,Em,C,D"},
            {"Big God", "Florence + The Machine", "F major / D minor", "Dm,Bb,C,F,Dm,Gm,Bb,A7"},
            {"Reckoner", "Radiohead", "E minor / G major", "Em,Am,C,G,Em,D,C,B7"},
            {"The Less I Know the Better", "Tame Impala", "C# minor", "C#m7,F#m7,G#m7,C#m7,Amaj7,G#m7,F#m7,G#7"},
            {"Feel Good Inc.", "Gorillaz", "Eb minor", "Ebm,Bbm,Abm,Db,Ebm,Gb,Bbm,Db"},
            {"Dean Town", "Vulfpeck", "F# minor", "F#m7,C#m7,E7,B7,F#m7,A7,Dmaj7,C#7"},
            {"Pick Up the Pieces", "Average White Band", "F minor", "Fm7,Cm7,Bbm7,Cm7,Fm7,Ab7,Db7,C7"},
            {"Play That Funky Music", "Wild Cherry", "E minor / E funk", "Em7,A7,Em7,A7,G7,A7,Em7,B7"},
            {"Good Times", "Chic", "E minor", "Em7,A7,Em7,A7,Cmaj7,B7,Em7,A7"},
            {"Sex Machine", "James Brown", "Eb dominant funk", "Eb9,Eb9,Ab9,Ab9,Eb9,Eb9,Bb9,Ab9"},
            {"Cissy Strut", "The Meters", "C mixolydian / C blues", "C7,F7,C7,G7,C7,Bb7,F7,C7"},
            {"Brick House", "The Commodores", "A minor", "Am7,D7,Am7,D7,G7,C7,Am7,E7"},
            {"Treasure", "Bruno Mars", "Ab major", "Abmaj7,Fm7,Gm7,Cm7,Dbmaj7,Bbm7,Eb7,Abmaj7"},
            {"Superstition", "Stevie Wonder", "Eb minor / blues", "Ebm7,Abm7,Ebm7,Abm7,B7,Bb7,Ebm7,Bb7"},
            {"Sing It Back", "Moloko", "Bb minor", "Bbm7,Ebm7,Abm7,Db7,Gbmaj7,F7,Bbm7,F7"},
            {"Get Up (I Feel Like Being a) Sex Machine", "James Brown", "E dominant funk", "E9,E9,A9,A9,E9,E9,B9,A9"},
            {"Flashlight", "Parliament", "C minor", "Cm7,Fm7,Ab,G7,Cm7,Bb,Ab,G7"},
            {"Give Up the Funk", "Parliament", "C minor / funk", "Cm7,F7,Bb7,Eb7,Ab7,G7,Cm7,G7"},
            {"Thank You (Falettinme Be Mice Elf Agin)", "Sly & The Family Stone", "Eb mixolydian / funk", "Eb7,Ab7,Eb7,Bb7,Ab7,Eb7,Db7,Eb7"},
            {"Jungle Boogie", "Kool & The Gang", "G minor funk", "Gm7,Fm7,Gm7,Fm7,Cm7,D7,Gm7,D7"},
            {"Uptown Funk", "Mark Ronson ft. Bruno Mars", "D minor funk", "Dm7,G7,Dm7,G7,Bbmaj7,A7,Dm7,G7"},
            {"Super Freak", "Rick James", "F minor", "Fm7,Bbm7,Eb7,Abmaj7,Dbmaj7,C7,Fm7,C7"},
            {"I Got You (I Feel Good)", "James Brown", "E dominant funk", "E7,A7,E7,B7,A7,E7,D7,E7"},
            {"Valerie", "Amy Winehouse", "Eb major / C minor", "Eb,Ab,Cm,Bb,Eb,Ab,Fm,Bb"},
            {"Back to Black", "Amy Winehouse", "D minor", "Dm,Bb,F,A7,Dm,Gm,Bb,A7"},
            {"Rolling in the Deep", "Adele", "C minor", "Cm,Eb,Bb,Ab,Cm,Eb,Fm,G7"},
            {"Locked Out of Heaven", "Bruno Mars", "D minor / F major", "Dm,F,C,Bb,Dm,F,Gm,A7"},
            {"Royals", "Lorde", "D major / B minor", "D,Bm,G,A,D,Bm,Em,A"},
            {"Somebody That I Used to Know", "Gotye", "D minor / F major", "Dm,Am,C,G,Dm,F,C,G"},
            {"Electric Feel", "MGMT", "F# minor", "F#m,D,A,E,F#m,C#m,D,E"},
            {"Kids", "MGMT", "B minor", "Bm,G,D,A,Bm,Em,G,A"},
            {"Clocks", "Coldplay", "Eb major / C minor", "Eb,Bb,Cm,Ab,Eb,Bb,Fm,Ab"},
    };

    public static int quants() {
        return LLISTA.length;
    }

    public static String titol(int i) {
        return LLISTA[i][0];
    }

    public static String grup(int i) {
        return LLISTA[i][1];
    }

    public static String tonalitat(int i) {
        return LLISTA[i][2];
    }

    public static String[] acords(int i) {
        return LLISTA[i][3].split(",");
    }

    /** Busca un preset pel títol (-1 si no hi és). */
    public static int index(String titol) {
        if (titol == null) return -1;
        for (int i = 0; i < LLISTA.length; i++) if (LLISTA[i][0].equals(titol)) return i;
        return -1;
    }
}
