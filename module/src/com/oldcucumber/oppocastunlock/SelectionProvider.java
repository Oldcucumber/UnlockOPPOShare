// SPDX-License-Identifier: GPL-3.0-only
package com.oldcucumber.oppocastunlock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import java.util.ArrayList;

/** Read-only configuration bridge for the selected OPPO process. */
public final class SelectionProvider extends ContentProvider {
    private SelectionStore store;
    @Override public boolean onCreate() { store = new SelectionStore(getContext()); return true; }
    private void enforceCaller() {
        if (Binder.getCallingUid() == Process.myUid()) return;
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages != null) for (String pkg : packages) if ("com.oplus.linker".equals(pkg)) return;
        throw new SecurityException("Unsupported caller");
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        enforceCaller();
        if (!SelectionStore.URI.equals(uri)) throw new IllegalArgumentException("Unknown selection URI");
        MatrixCursor cursor = new MatrixCursor(new String[]{"policy"});
        cursor.addRow(new Object[]{store.encode()});
        cursor.setNotificationUri(getContext().getContentResolver(), SelectionStore.URI);
        return cursor;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        enforceCaller();
        if (!"baseline".equals(method) || extras == null) throw new IllegalArgumentException("Unknown method");
        ArrayList<String> packages = extras.getStringArrayList("packages");
        if (packages != null) store.saveBaseline(packages);
        return Bundle.EMPTY;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.oppo.cast.selection"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
