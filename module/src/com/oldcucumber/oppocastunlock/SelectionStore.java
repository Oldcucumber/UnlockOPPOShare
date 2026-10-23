// SPDX-License-Identifier: GPL-3.0-only
package com.oldcucumber.oppocastunlock;

import android.content.Context;
import android.content.SharedPreferences;
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
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("selection", Context.MODE_PRIVATE);
    }
    public SelectionPolicy read() {
        return new SelectionPolicy(prefs.getStringSet("included", Collections.emptySet()), prefs.getStringSet("excluded", Collections.emptySet()));
    }
    public Set<String> baseline() { return new LinkedHashSet<>(prefs.getStringSet("baseline", Collections.emptySet())); }
    public synchronized void write(SelectionPolicy policy) {
        if (!prefs.edit().putStringSet("included", policy.included).putStringSet("excluded", policy.excluded).commit())
            throw new IllegalStateException("Cannot save selection");
        context.getContentResolver().notifyChange(URI, null);
    }
    public synchronized void saveBaseline(Collection<String> baseline) {
        LinkedHashSet<String> next = new LinkedHashSet<>(baseline);
        if (!next.equals(baseline())) {
            prefs.edit().putStringSet("baseline", next).commit();
            context.getContentResolver().notifyChange(URI, null);
        }
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
