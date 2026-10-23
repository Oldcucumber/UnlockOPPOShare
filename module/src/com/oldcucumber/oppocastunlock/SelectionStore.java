// SPDX-License-Identifier: GPL-3.0-only
package com.oldcucumber.oppocastunlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;

public final class SelectionStore {
    public static final String AUTHORITY = "com.oldcucumber.oppocastunlock.selection";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/selection");
    private final Context context;
    private final SharedPreferences prefs;
    public SelectionStore(Context context) {
        this(context, context.getApplicationContext().getSharedPreferences("selection", Context.MODE_PRIVATE));
    }
    SelectionStore(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        synchronized (prefs) {
            if (prefs.getInt("schema", 0) < 2) {
                LinkedHashSet<String> included = new LinkedHashSet<>(prefs.getStringSet("included", Collections.emptySet()));
                // Preserve the old automatic user-app selection once, without retaining that rule after reset.
                if (prefs.contains("included") || prefs.contains("excluded") || prefs.contains("baseline")) {
                    for (ApplicationInfo info : this.context.getPackageManager().getInstalledApplications(0))
                        if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) included.add(info.packageName);
                }
                commit(prefs.edit().putStringSet("included", included).putInt("schema", 2));
            }
        }
    }
    public SelectionPolicy read() {
        synchronized (prefs) {
            return new SelectionPolicy(prefs.getStringSet("included", Collections.emptySet()), prefs.getStringSet("excluded", Collections.emptySet()));
        }
    }
    public Set<String> baseline() { return new LinkedHashSet<>(prefs.getStringSet("baseline", Collections.emptySet())); }
    public void write(SelectionPolicy policy) {
        synchronized (prefs) {
            commit(prefs.edit().putStringSet("included", policy.included).putStringSet("excluded", policy.excluded));
        }
        context.getContentResolver().notifyChange(URI, null);
    }
    public void saveBaseline(Collection<String> baseline) {
        LinkedHashSet<String> next = new LinkedHashSet<>(baseline);
        synchronized (prefs) {
            if (next.equals(baseline())) return;
            commit(prefs.edit().putStringSet("baseline", next));
        }
        context.getContentResolver().notifyChange(URI, null);
    }
    private static void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Cannot save selection");
    }
    public String encode() {
        try {
            SelectionPolicy policy = read();
            return new JSONObject().put("included", new JSONArray(policy.included))
                .put("excluded", new JSONArray(policy.excluded)).toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    public static SelectionPolicy decode(String json) {
        try {
            JSONObject object = new JSONObject(json);
            return new SelectionPolicy(strings(object.optJSONArray("included")), strings(object.optJSONArray("excluded")));
        } catch (Exception error) { throw new IllegalArgumentException("Invalid selection", error); }
    }
    private static Set<String> strings(JSONArray array) throws Exception {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (array != null) for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
        return result;
    }
}
