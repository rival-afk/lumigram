package com.lumigram.messenger.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.util.HashSet;
import java.util.Set;

final class PluginSafeMode {

    private static final String PREFS_NAME = "lumigram_plugin_safe_mode";
    private static final String KEY_SAFE_MODE = "safe_mode_active";
    private static final String KEY_CRASHED_PLUGINS = "crashed_plugins";
    private static final String KEY_CRASH_COUNT = "crash_count_";
    private static final String KEY_LAST_CRASH_PLUGIN = "last_crash_plugin";
    private static final int MAX_CRASHES_BEFORE_SAFE = 1;

    private final SharedPreferences prefs;
    private volatile boolean safeMode;

    PluginSafeMode(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.safeMode = prefs.getBoolean(KEY_SAFE_MODE, false);
    }

    boolean isSafeMode() {
        return safeMode;
    }

    void enterSafeMode(@NonNull String crashedPluginId) {
        safeMode = true;
        prefs.edit()
                .putBoolean(KEY_SAFE_MODE, true)
                .putString(KEY_LAST_CRASH_PLUGIN, crashedPluginId)
                .apply();
        FileLog.w("PluginSafeMode: entered safe mode due to plugin " + crashedPluginId);
    }

    void exitSafeMode() {
        safeMode = false;
        prefs.edit()
                .putBoolean(KEY_SAFE_MODE, false)
                .remove(KEY_LAST_CRASH_PLUGIN)
                .apply();
        FileLog.d("PluginSafeMode: exited safe mode");
    }

    @Nullable
    String getLastCrashedPlugin() {
        return prefs.getString(KEY_LAST_CRASH_PLUGIN, null);
    }

    void recordCrash(@NonNull String pluginId) {
        String key = KEY_CRASH_COUNT + pluginId;
        int count = prefs.getInt(key, 0) + 1;
        prefs.edit().putInt(key, count).apply();

        if (count >= MAX_CRASHES_BEFORE_SAFE) {
            enterSafeMode(pluginId);
        }

        Set<String> crashed = new HashSet<>(prefs.getStringSet(KEY_CRASHED_PLUGINS, new HashSet<>()));
        crashed.add(pluginId);
        prefs.edit().putStringSet(KEY_CRASHED_PLUGINS, crashed).apply();

        FileLog.w("PluginSafeMode: recorded crash for " + pluginId + " (count=" + count + ")");
    }

    int getCrashCount(@NonNull String pluginId) {
        return prefs.getInt(KEY_CRASH_COUNT + pluginId, 0);
    }

    void resetCrashCount(@NonNull String pluginId) {
        prefs.edit()
                .remove(KEY_CRASH_COUNT + pluginId)
                .apply();
    }

    boolean hasCrashed(@NonNull String pluginId) {
        Set<String> crashed = prefs.getStringSet(KEY_CRASHED_PLUGINS, new HashSet<>());
        return crashed.contains(pluginId);
    }

    void clearCrashHistory(@NonNull String pluginId) {
        Set<String> crashed = new HashSet<>(prefs.getStringSet(KEY_CRASHED_PLUGINS, new HashSet<>()));
        crashed.remove(pluginId);
        prefs.edit()
                .putStringSet(KEY_CRASHED_PLUGINS, crashed)
                .remove(KEY_CRASH_COUNT + pluginId)
                .apply();
    }
}
