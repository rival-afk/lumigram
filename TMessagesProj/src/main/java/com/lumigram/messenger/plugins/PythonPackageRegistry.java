package com.lumigram.messenger.plugins;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registry for requested python requirements.
 * Runtime installation/execution is intentionally decoupled and can be plugged in later.
 */
final class PythonPackageRegistry {

    private final File stateFile;
    private final Set<String> requirements = new LinkedHashSet<>();

    PythonPackageRegistry(@NonNull File pluginsRuntimeDir) {
        this.stateFile = new File(pluginsRuntimeDir, "python_requirements.json");
        load();
    }

    synchronized void registerRequirements(@NonNull String pluginId, @NonNull Iterable<String> deps) {
        boolean changed = false;
        for (String dep : deps) {
            if (dep == null) {
                continue;
            }
            String normalized = dep.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (requirements.add(normalized)) {
                changed = true;
            }
        }
        if (changed) {
            save(pluginId);
        }
    }

    @NonNull
    synchronized Set<String> snapshot() {
        return new LinkedHashSet<>(requirements);
    }

    private void load() {
        try {
            if (!stateFile.exists()) {
                return;
            }
            String raw = readFile(stateFile);
            JSONObject root = new JSONObject(raw);
            JSONArray items = root.optJSONArray("requirements");
            if (items == null) {
                return;
            }
            for (int i = 0; i < items.length(); i++) {
                String value = items.optString(i, "").trim();
                if (!value.isEmpty()) {
                    requirements.add(value);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void save(@NonNull String sourcePluginId) {
        try {
            JSONObject root = new JSONObject();
            root.put("last_source_plugin_id", sourcePluginId);

            JSONArray items = new JSONArray();
            for (String dep : requirements) {
                items.put(dep);
            }
            root.put("requirements", items);

            writeFile(stateFile, root.toString(2));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @NonNull
    private static String readFile(@NonNull File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = in.read(bytes);
            if (read <= 0) {
                return "";
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }

    private static void writeFile(@NonNull File file, @NonNull String data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
