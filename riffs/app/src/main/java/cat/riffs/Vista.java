package cat.riffs;

import java.util.ArrayList;
import java.util.List;

/**
 * Estat compartit de la vista de l'Estudi: zoom, on comença la part visible, el capçal i el clip triat.
 * La regla i les pistes el fan servir perquè vagin sempre a la par.
 */
public final class Vista {

    public interface Oient {
        void canvi();
    }

    public float pxPerMs = 0.12f;   // zoom
    public double iniciMs = -200;   // moment que queda a l'esquerra de tot
    public long capcalMs = 0;
    public int triada = -1;         // pista del clip triat (-1 = cap)
    public int snap = Projecte.SNAP_TEMPS;

    private final List<Oient> oients = new ArrayList<>();

    public void escolta(Oient o) {
        oients.add(o);
    }

    public void avisa() {
        for (Oient o : oients) o.canvi();
    }

    public float x(double ms) {
        return (float) ((ms - iniciMs) * pxPerMs);
    }

    public double ms(float x) {
        return iniciMs + x / pxPerMs;
    }

    /** Zoom mantenint quiet el moment que hi ha sota {@code x}. */
    public void zoom(float factor, float x) {
        double sota = ms(x);
        pxPerMs = Math.max(0.01f, Math.min(1.5f, pxPerMs * factor));
        iniciMs = sota - x / pxPerMs;
        limita();
    }

    public void limita() {
        if (iniciMs < -2000) iniciMs = -2000;
    }
}
