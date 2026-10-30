package cat.riffs;

/** Els 128 instruments General MIDI, en català, agrupats en 16 famílies de 8. No depèn d'Android. */
public final class Gm {

    private Gm() {}

    public static final String[] FAMILIES = {
            "Pianos", "Percussió afinada", "Orgues", "Guitarres", "Baixos", "Cordes", "Conjunts", "Metalls",
            "Llengüetes", "Flautes", "Leads de sinte", "Pads de sinte", "Efectes de sinte", "Instruments del món",
            "Percussió", "Efectes de so"};

    public static final String[] NOMS = {
            // pianos
            "Piano de cua", "Piano brillant", "Piano de cua elèctric", "Piano de taverna", "Piano elèctric (Rhodes)",
            "Piano elèctric (FM)", "Clavicèmbal", "Clavinet",
            // percussió afinada
            "Celesta", "Glockenspiel", "Caixa de música", "Vibràfon", "Marimba", "Xilòfon", "Campanes tubulars", "Dulcimer",
            // orgues
            "Orgue de barres (Hammond)", "Orgue de percussió", "Orgue de rock", "Orgue d'església", "Harmònium",
            "Acordió", "Harmònica", "Bandoneó",
            // guitarres
            "Guitarra clàssica (niló)", "Guitarra acústica (acer)", "Guitarra elèctrica (jazz)", "Guitarra elèctrica (neta)",
            "Guitarra elèctrica (apagada)", "Guitarra saturada", "Guitarra distorsionada", "Harmònics de guitarra",
            // baixos
            "Contrabaix", "Baix elèctric (dits)", "Baix elèctric (pua)", "Baix sense trasts", "Baix slap 1", "Baix slap 2",
            "Baix de sinte 1", "Baix de sinte 2",
            // cordes
            "Violí", "Viola", "Violoncel", "Contrabaix d'orquestra", "Cordes en trèmolo", "Cordes en pizzicato",
            "Arpa", "Timbales",
            // conjunts
            "Cordes 1", "Cordes 2 (lentes)", "Cordes de sinte 1", "Cordes de sinte 2", "Cor (aaah)", "Veus (oooh)",
            "Veu de sinte", "Cop d'orquestra",
            // metalls
            "Trompeta", "Trombó", "Tuba", "Trompeta amb sordina", "Trompa", "Secció de metalls", "Metalls de sinte 1",
            "Metalls de sinte 2",
            // llengüetes
            "Saxo soprano", "Saxo alt", "Saxo tenor", "Saxo baríton", "Oboè", "Corn anglès", "Fagot", "Clarinet",
            // flautes
            "Flautí", "Flauta travessera", "Flauta dolça", "Flauta de Pan", "Ampolla bufada", "Shakuhachi", "Xiulet", "Ocarina",
            // leads
            "Lead quadrat", "Lead de serra", "Lead de cal·líope", "Lead chiff", "Lead charang", "Lead de veu",
            "Lead de quintes", "Lead de baix + lead",
            // pads
            "Pad new age", "Pad càlid", "Pad polisinte", "Pad de cor", "Pad d'arc", "Pad metàl·lic", "Pad d'halo", "Pad d'escombrat",
            // efectes de sinte
            "Pluja", "Banda sonora", "Cristall", "Atmosfera", "Brillantor", "Follets", "Ressons", "Ciència-ficció",
            // món
            "Sitar", "Banjo", "Shamisen", "Koto", "Kalimba", "Gaita", "Violí folk", "Shanai",
            // percussió
            "Campaneta", "Agogó", "Steel drum", "Caixa xinesa", "Taiko", "Tom melòdic", "Tambor de sinte", "Plat al revés",
            // efectes
            "Fregament de cordes", "Respiració", "Onades", "Ocells", "Telèfon", "Helicòpter", "Aplaudiments", "Tret"};

    /** Instruments que sonen més greus (el piano roll i el teclat s'obren més avall). */
    public static boolean esGreu(int programa) {
        return programa >= 32 && programa <= 39 || programa == 43 || programa == 58 || programa == 70;
    }

    public static String nom(int programa) {
        return programa >= 0 && programa < NOMS.length ? NOMS[programa] : "Instrument " + (programa + 1);
    }
}
