package com.lumigram.messenger.plugins;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PluginArchiveParser {

    private static final String[] MANIFEST_CANDIDATES = {
            "lumigram-plugin.json",
            "plugin.json",
            "manifest.json",
            "explug.json"
    };

    private static final String[] PLUGIN_EXTENSIONS = {".plugin", ".lplug", ".explug", ".zip", ".apk"};

    static boolean isPluginFile(@NonNull File file) {
        String name = file.getName().toLowerCase(Locale.US);
        for (String ext : PLUGIN_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static PluginManifest parse(@NonNull File archiveFile) {
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            ZipEntry manifestEntry = findManifest(zipFile);
            if (manifestEntry == null) {
                return null;
            }
            String json = readUtf8(zipFile, manifestEntry);
            JSONObject root = new JSONObject(json);

            String defaultId = archiveFile.getName().replaceAll("\\.[^.]+$", "");
            String id = root.optString("id", defaultId).trim();
            String name = root.optString("name", id).trim();
            String version = root.optString("version", "0.0.0").trim();
            String author = root.optString("author", null);
            String description = root.optString("description", null);
            int minSdk = root.optInt("min_sdk", 26);
            String minLumigramVersion = root.optString("min_lumigram_version", "1.0.0").trim();

            String typeStr = root.optString("type", "python").trim();
            PluginManifest.PluginType type = PluginManifest.PluginType.fromString(typeStr);

            String entryPoint = root.optString("entrypoint", null);
            if (entryPoint == null) {
                entryPoint = root.optString("entry_point", "plugin:Plugin");
            }

            String cppLibrary = root.optString("cpp_library", null);

            String requirementsFile = root.optString("requirements", null);

            int priority = root.optInt("priority", 0);

            String signer = root.optString("signer", null);
            String signatureAlgorithmStr = root.optString("signature_algo", null);

            List<String> permissions = parseStringArray(root, "permissions");
            List<String> optionalPermissions = parseStringArray(root, "optional_permissions");
            List<String> modules = parseStringArray(root, "modules");

            List<String> pythonRequirements = new ArrayList<>();
            if (requirementsFile != null) {
                ZipEntry reqEntry = zipFile.getEntry(requirementsFile);
                if (reqEntry != null) {
                    String reqContent = readUtf8(zipFile, reqEntry);
                    for (String line : reqContent.split("\\n")) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            pythonRequirements.add(line);
                        }
                    }
                }
            }
            JSONObject python = root.optJSONObject("python");
            if (python != null) {
                JSONArray deps = python.optJSONArray("requirements");
                if (deps != null) {
                    for (int i = 0; i < deps.length(); i++) {
                        String dep = deps.optString(i, "").trim();
                        if (!dep.isEmpty() && !pythonRequirements.contains(dep)) {
                            pythonRequirements.add(dep);
                        }
                    }
                }
            }

            return new PluginManifest(
                    id, name, version,
                    author, description,
                    minSdk, minLumigramVersion,
                    type, entryPoint, cppLibrary, requirementsFile,
                    priority,
                    signer, signatureAlgorithmStr,
                    permissions, optionalPermissions, modules,
                    pythonRequirements
            );
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @NonNull
    private static List<String> parseStringArray(@NonNull JSONObject root, @NonNull String key) {
        List<String> result = new ArrayList<>();
        JSONArray arr = root.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String value = arr.optString(i, "").trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    @Nullable
    private static ZipEntry findManifest(@NonNull ZipFile zipFile) {
        for (String candidate : MANIFEST_CANDIDATES) {
            ZipEntry entry = zipFile.getEntry(candidate);
            if (entry != null) {
                return entry;
            }
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry nested = entries.nextElement();
                String name = nested.getName().toLowerCase(Locale.US);
                if (name.endsWith("/" + candidate) || name.equals(candidate)) {
                    return nested;
                }
            }
        }
        return null;
    }

    @NonNull
    private static String readUtf8(@NonNull ZipFile zipFile, @NonNull ZipEntry entry) throws Exception {
        try (InputStream stream = zipFile.getInputStream(entry)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
