package cat.riffs;

import java.util.ArrayDeque;

/**
 * Llista de «fotos» (estats en text) per poder desfer. La de dalt és l'estat actual; desfer la treu i torna
 * l'anterior. Es guarden com a molt {@code MAX} passos. No depèn d'Android.
 */
public final class Desfes {

    public static final int MAX = 60;
    private final ArrayDeque<String> pila = new ArrayDeque<>();

    /** Comença de nou amb aquest estat (el de quan s'obre la pantalla). */
    public void inici(String foto) {
        pila.clear();
        if (foto != null) pila.push(foto);
    }

    /** S'ha fet un canvi: aquest és l'estat nou. Torna true si s'ha afegit un pas. */
    public boolean registra(String foto) {
        if (foto == null) return false;
        if (!pila.isEmpty() && pila.peek().equals(foto)) return false;
        pila.push(foto);
        while (pila.size() > MAX + 1) pila.removeLast();
        return true;
    }

    public boolean pot() {
        return pila.size() > 1;
    }

    public int passos() {
        return Math.max(0, pila.size() - 1);
    }

    /** Desfà l'últim canvi: torna l'estat al qual s'ha de tornar (o null si no n'hi ha). */
    public String desfes() {
        if (pila.size() < 2) return null;
        pila.pop();
        return pila.peek();
    }
}
