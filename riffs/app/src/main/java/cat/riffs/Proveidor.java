package cat.riffs;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deixa que altres apps (WhatsApp, correu, Drive…) llegeixin una gravació quan la comparteixes.
 * Només dona accés de lectura, només a fitxers de la biblioteca i només a l'app que tries.
 */
public class Proveidor extends ContentProvider {

    public static final String AUTORITAT = "cat.riffs.fitxers";

    /** content://cat.riffs.fitxers/f/<carpeta>/<fitxer.wav>?nom=<nom per mostrar> */
    public static Uri uri(Context c, File wav, String nomPerMostrar) {
        File base = Ui.biblioteca(c).arrel;
        List<String> parts = new ArrayList<>();
        try {
            File a = base.getCanonicalFile();
            File f = wav.getCanonicalFile();
            while (f != null && !f.equals(a)) {
                parts.add(f.getName());
                f = f.getParentFile();
            }
            if (f == null) return null;
        } catch (IOException e) {
            return null;
        }
        Collections.reverse(parts);
        Uri.Builder b = new Uri.Builder().scheme("content").authority(AUTORITAT).appendPath("f");
        for (String p : parts) b.appendPath(p);
        b.appendQueryParameter("nom", nomPerMostrar);
        return b.build();
    }

    private File fitxer(Uri uri) throws FileNotFoundException {
        List<String> parts = uri.getPathSegments();
        if (parts.size() < 2 || !"f".equals(parts.get(0))) throw new FileNotFoundException();
        try {
            File base = Ui.biblioteca(getContext()).arrel.getCanonicalFile();
            File f = base;
            for (int i = 1; i < parts.size(); i++) f = new File(f, parts.get(i));
            f = f.getCanonicalFile();
            if (!f.getPath().startsWith(base.getPath() + File.separator) || !f.isFile()) {
                throw new FileNotFoundException();
            }
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException();
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) throw new FileNotFoundException("Només lectura");
        return ParcelFileDescriptor.open(fitxer(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projeccio, String seleccio, String[] args, String ordre) {
        File f;
        try {
            f = fitxer(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        if (projeccio == null) projeccio = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        String nom = uri.getQueryParameter("nom");
        if (nom == null || nom.isEmpty()) nom = f.getName();
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (String p : projeccio) {
            if (OpenableColumns.DISPLAY_NAME.equals(p)) {
                cols.add(p);
                vals.add(nom);
            } else if (OpenableColumns.SIZE.equals(p)) {
                cols.add(p);
                vals.add(f.length());
            }
        }
        MatrixCursor c = new MatrixCursor(cols.toArray(new String[0]), 1);
        c.addRow(vals.toArray());
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "audio/wav";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
