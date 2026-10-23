// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Old_cucumber
package com.oldcucumber.oppocastunlock;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CastUnlockHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.oplus.linker";
    private static final String MAIN = TARGET + ".pc.cast.rus.CastRUSDataManager";
    private static final String FINDER = TARGET + ".mac.client.data.source.FindPhoneApplication";
    private final List<Manager> managers = new ArrayList<>();
    private final AtomicBoolean rebuildApps = new AtomicBoolean(true);
    private final AtomicBoolean observing = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Context context;
    private volatile SelectionPolicy policy = SelectionPolicy.defaults();
    private volatile List<String> userApps = Collections.emptyList();
    private volatile long lastScan;
    private ClassLoader loader;
    private Handler handler;
    private Runnable reload;

    private static final class Manager {
        final Class<?> type;
        volatile List<String> original = Collections.emptyList();
        volatile Object published;
        volatile List<String> reported;
        Manager(Class<?> type) { this.type = type; }
    }
    private static final class PublishedApps extends ArrayList<String> {
        PublishedApps(Collection<String> apps) { super(apps); }
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) throws Throwable {
        if (!TARGET.equals(load.packageName)) return;
        loader = load.classLoader;
        installManager(MAIN, true);
        installManager(TARGET + ".synergy.entry.rus.PCSynergyRUSDataManager", false);
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Context c = (Context) p.args[0];
                if (c != null && TARGET.equals(c.getPackageName())) {
                    initialize(c); refreshApps(true); publishAll();
                }
            }
        });
        try {
            XposedHelpers.findAndHookMethod(FINDER, loader, "getAllWhiteApps", Context.class, boolean.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        initialize((Context) p.args[0]); refreshApps(false); publishAll();
                        if (rebuildApps.getAndSet(false)) p.args[1] = true;
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable()) rebuildApps.set(true);
                    }
                });
            XposedHelpers.findAndHookMethod(TARGET + ".mac.client.data.source.PackageChangeReceiver", loader,
                "onReceive", Context.class, Intent.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        initialize((Context) p.args[0]); refreshApps(true); publishAll();
                    }
                });
        } catch (Throwable error) { log("Cache hook: " + error); }
        log("Loaded " + load.processName);
    }

    private void installManager(String name, boolean required) {
        try {
            Class<?> type = XposedHelpers.findClass(name, loader);
            XposedHelpers.findField(type, "sMirageAppList");
            final Manager state = new Manager(type);
            managers.add(state);
            XposedHelpers.findAndHookMethod(type, "getMirageAppList", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    refreshApps(false);
                    capture(state, p.getResult());
                    p.setResult(publish(state));
                }
            });
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals("commitMirageAppList") && method.getParameterTypes().length == 0) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable()) return;
                            capture(state, XposedHelpers.getStaticObjectField(type, "sMirageAppList"));
                            publish(state); rebuildApps.set(true);
                        }
                    });
                } else if (method.getName().equals("setMirageAppList") && method.getParameterTypes().length == 1
                        && Collection.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable()) return;
                            capture(state, XposedHelpers.getStaticObjectField(type, "sMirageAppList"));
                            publish(state); rebuildApps.set(true);
                        }
                    });
                }
            }
        } catch (Throwable error) { log((required ? "Required" : "Optional") + " manager: " + error); }
    }

    private void initialize(Context supplied) {
        if (supplied == null) return;
        context = supplied.getApplicationContext() != null ? supplied.getApplicationContext() : supplied;
        if (!observing.compareAndSet(false, true)) return;
        try {
            handler = new Handler(Looper.getMainLooper());
            reload = () -> worker.execute(() -> {
                readPolicy(); publishAll(); rebuildApps.set(true);
                try {
                    Class<?> finder = XposedHelpers.findClass(FINDER, loader);
                    Object instance = XposedHelpers.getStaticObjectField(finder, "INSTANCE");
                    XposedHelpers.callMethod(instance, "getAllWhiteApps", context, true);
                    rebuildApps.set(false);
                } catch (Throwable error) { log("Refresh list: " + error); }
            });
            context.getContentResolver().registerContentObserver(SelectionStore.URI, false, new ContentObserver(handler) {
                @Override public void onChange(boolean selfChange) {
                    handler.removeCallbacks(reload); handler.postDelayed(reload, 250);
                }
            });
            readPolicy();
        } catch (Throwable error) { observing.set(false); log("Selection bridge: " + error); }
    }

    private void readPolicy() {
        if (context == null) return;
        try (Cursor cursor = context.getContentResolver().query(SelectionStore.URI, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) policy = SelectionStore.decode(cursor.getString(0));
        } catch (Throwable error) { log("Read selection: " + error); }
    }

    private synchronized void refreshApps(boolean force) {
        Context c = context;
        if (c == null) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && lastScan != 0 && now - lastScan < 3000) return;
        try {
            ArrayList<String> next = new ArrayList<>();
            for (ApplicationInfo info : c.getPackageManager().getInstalledApplications(0))
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) next.add(info.packageName);
            Collections.sort(next);
            if (!next.equals(userApps)) { userApps = Collections.unmodifiableList(next); rebuildApps.set(true); }
            lastScan = now;
        } catch (Throwable error) { log("Enumerate apps: " + error); }
    }

    private void capture(Manager state, Object value) {
        if (value instanceof PublishedApps || !(value instanceof Collection<?>)) return;
        ArrayList<String> original = new ArrayList<>();
        for (Object item : (Collection<?>) value) if (item instanceof String) original.add((String) item);
        state.original = original;
    }

    private ArrayList<String> publish(Manager state) {
        ArrayList<String> next = new PublishedApps(policy.apply(state.original, userApps));
        XposedHelpers.setStaticObjectField(state.type, "sMirageAppList", next);
        state.published = next;
        if (MAIN.equals(state.type.getName()) && context != null && !state.original.equals(state.reported)) {
            try {
                Bundle extras = new Bundle(); extras.putStringArrayList("packages", new ArrayList<>(state.original));
                context.getContentResolver().call(SelectionStore.URI, "baseline", null, extras);
                state.reported = new ArrayList<>(state.original);
            } catch (Throwable error) { log("Report list: " + error); }
        }
        return next;
    }
    private void publishAll() {
        for (Manager state : managers) try {
            capture(state, XposedHelpers.getStaticObjectField(state.type, "sMirageAppList")); publish(state);
        } catch (Throwable error) { log("Update whitelist: " + error); }
    }
    private static void log(String message) { XposedBridge.log("[OPPOCastUnlock] " + message); }
}
