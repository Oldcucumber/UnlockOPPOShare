// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Old_cucumber
package com.oldcucumber.oppocastunlock;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Old_cucumber. Runs only inside the selected OPPO Connect package. */
public final class CastUnlockHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.oplus.linker";
    private static final String MAIN_MANAGER = TARGET + ".pc.cast.rus.CastRUSDataManager";
    private static final String LEGACY_MANAGER = TARGET + ".synergy.entry.rus.PCSynergyRUSDataManager";
    private final List<Class<?>> managers = new ArrayList<>();
    private final AtomicBoolean rebuildApps = new AtomicBoolean(true);
    private volatile Context context;
    private volatile List<String> userApps = Collections.emptyList();
    private volatile long lastScan;
    private boolean scanErrorLogged;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) throws Throwable {
        if (!TARGET.equals(load.packageName)) return;
        installManager(load.classLoader, MAIN_MANAGER, true);
        installManager(load.classLoader, LEGACY_MANAGER, false);

        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Context attached = (Context) param.args[0];
                if (attached != null && TARGET.equals(attached.getPackageName())) {
                    context = attached.getApplicationContext();
                    if (context == null) context = attached;
                    refreshApps(context, true);
                    publishAll();
                }
            }
        });

        String finder = TARGET + ".mac.client.data.source.FindPhoneApplication";
        try {
            XposedHelpers.findAndHookMethod(finder, load.classLoader, "getAllWhiteApps",
                    Context.class, boolean.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    refreshApps((Context) param.args[0], false);
                    publishAll();
                    if (rebuildApps.getAndSet(false)) param.args[1] = true;
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) rebuildApps.set(true);
                }
            });
        } catch (Throwable error) { log("App cache hook unavailable: " + error); }

        try {
            XposedHelpers.findAndHookMethod(TARGET + ".mac.client.data.source.PackageChangeReceiver",
                    load.classLoader, "onReceive", Context.class, Intent.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    refreshApps((Context) param.args[0], true);
                    publishAll();
                }
            });
        } catch (Throwable error) { log("Package update hook unavailable: " + error); }
        log("Loaded in " + load.processName + "; author=Old_cucumber");
    }

    private void installManager(ClassLoader loader, String name, boolean required) {
        try {
            final Class<?> type = XposedHelpers.findClass(name, loader);
            // Fail this manager explicitly if its storage layout changed.
            XposedHelpers.findField(type, "sMirageAppList");
            managers.add(type);
            XposedHelpers.findAndHookMethod(type, "getMirageAppList", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    refreshApps(context, false);
                    ArrayList<String> expanded = merge(param.getResult());
                    publish(type, expanded);
                    param.setResult(expanded);
                }
            });
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals("commitMirageAppList") && method.getParameterTypes().length == 0) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) return;
                            refreshApps(context, false);
                            publishCurrent(type);
                            rebuildApps.set(true);
                        }
                    });
                } else if (method.getName().equals("setMirageAppList") && method.getParameterTypes().length == 1
                        && Collection.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            refreshApps(context, false);
                            param.args[0] = merge(param.args[0]);
                            rebuildApps.set(true);
                        }
                    });
                }
            }
            log("Hooked " + name);
        } catch (Throwable error) {
            log((required ? "ERROR: required" : "Optional") + " manager unavailable: " + error);
        }
    }

    private synchronized void refreshApps(Context supplied, boolean force) {
        if (supplied != null) context = supplied;
        Context current = context;
        if (current == null) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && lastScan != 0 && now - lastScan < 3000) return;
        try {
            ArrayList<String> packages = new ArrayList<>();
            for (ApplicationInfo info : current.getPackageManager().getInstalledApplications(0)) {
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) packages.add(info.packageName);
            }
            Collections.sort(packages);
            if (!packages.equals(userApps)) {
                userApps = Collections.unmodifiableList(packages);
                rebuildApps.set(true);
                log("User apps=" + packages.size());
            }
            lastScan = now;
        } catch (Throwable error) {
            if (!scanErrorLogged) {
                scanErrorLogged = true;
                log("Package enumeration failed: " + error);
            }
        }
    }

    private ArrayList<String> merge(Object original) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (original instanceof Collection<?>) {
            for (Object value : (Collection<?>) original) {
                if (value instanceof String) result.add((String) value);
            }
        }
        result.addAll(userApps);
        return new ArrayList<>(result);
    }

    private void publish(Class<?> type, ArrayList<String> expanded) {
        try {
            Object current = XposedHelpers.getStaticObjectField(type, "sMirageAppList");
            if (!expanded.equals(current)) {
                // Update the field as well, so inlined getters read the expanded list.
                XposedHelpers.setStaticObjectField(type, "sMirageAppList", expanded);
            }
        } catch (Throwable error) { log("Cannot update " + type.getName() + ": " + error); }
    }

    private void publishCurrent(Class<?> type) {
        try { publish(type, merge(XposedHelpers.getStaticObjectField(type, "sMirageAppList"))); }
        catch (Throwable error) { log("Cannot read " + type.getName() + ": " + error); }
    }

    private void publishAll() {
        for (Class<?> manager : managers) publishCurrent(manager);
    }

    private static void log(String message) { XposedBridge.log("[OPPOCastUnlock] " + message); }
}
