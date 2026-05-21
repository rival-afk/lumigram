package com.lumigram.messenger.plugins;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PermissionManager {

    private static final PermissionManager INSTANCE = new PermissionManager();
    private static final String PREFS_NAME = "lumigram_plugin_permissions";
    private static final String KEY_GRANTED = "granted_";

    private static final Map<String, PermissionInfo> REGISTRY = new HashMap<>();

    static {
        register("telegram.read_messages", "Read Messages", "Read any messages in any chats", PermissionLevel.NORMAL);
        register("telegram.send_messages", "Send Messages", "Send messages on your behalf", PermissionLevel.NORMAL);
        register("telegram.read_contacts", "Read Contacts", "Read your contact list", PermissionLevel.NORMAL);
        register("telegram.modify_chats", "Modify Chats", "Create, delete, and modify chats", PermissionLevel.SENSITIVE);
        register("telegram.call", "Make Calls", "Initiate voice and video calls", PermissionLevel.NORMAL);
        register("telegram.read_stories", "Read Stories", "View stories", PermissionLevel.NORMAL);
        register("telegram.delete_messages", "Delete Messages", "Delete your or others' messages", PermissionLevel.SENSITIVE);
        register("android.camera", "Camera", "Access the camera", PermissionLevel.NORMAL);
        register("android.microphone", "Microphone", "Access the microphone", PermissionLevel.NORMAL);
        register("android.location", "Location", "Access device location", PermissionLevel.SENSITIVE);
        register("android.storage.read", "Read Storage", "Read external storage", PermissionLevel.SENSITIVE);
        register("android.storage.write", "Write Storage", "Write to external storage", PermissionLevel.SENSITIVE);
        register("android.storage.system", "System Storage", "Access /data and /system partitions", PermissionLevel.DANGEROUS);
        register("sudo.root_command", "Root Command", "Execute any command as root", PermissionLevel.DANGEROUS);
        register("sudo.shizuku_api", "Shizuku API", "Access Shizuku privileged API", PermissionLevel.DANGEROUS);
        register("sudo.network_capture", "Network Capture", "Intercept network traffic", PermissionLevel.DANGEROUS);
        register("sudo.gpu_access", "GPU Access", "Access GPU for computation", PermissionLevel.DANGEROUS);
        register("lumigram.ghost_mode", "Ghost Mode", "Hide online, typing, and read status", PermissionLevel.NORMAL);
        register("lumigram.spy_mode", "Spy Mode", "Save deleted messages, log calls", PermissionLevel.SENSITIVE);
        register("lumigram.secret_storage", "Secret Storage", "Read/write encrypted secure vault", PermissionLevel.SENSITIVE);
        register("lumigram.external_network", "External Network", "Use own network connections (Tor, VPN)", PermissionLevel.SENSITIVE);
    }

    public enum PermissionLevel {
        NORMAL(0),
        SENSITIVE(1),
        DANGEROUS(2);

        public final int severity;

        PermissionLevel(int severity) {
            this.severity = severity;
        }
    }

    public static final class PermissionInfo {
        @NonNull
        public final String id;
        @NonNull
        public final String displayName;
        @NonNull
        public final String description;
        @NonNull
        public final PermissionLevel level;

        PermissionInfo(@NonNull String id, @NonNull String displayName,
                       @NonNull String description, @NonNull PermissionLevel level) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.level = level;
        }
    }

    private SharedPreferences prefs;
    private PermissionDialogProvider dialogProvider;
    private volatile boolean initialized;

    private PermissionManager() {
    }

    @NonNull
    public static PermissionManager getInstance() {
        return INSTANCE;
    }

    public static void register(@NonNull String id, @NonNull String displayName,
                                @NonNull String description, @NonNull PermissionLevel level) {
        REGISTRY.put(id, new PermissionInfo(id, displayName, description, level));
    }

    @Nullable
    public static PermissionInfo getInfo(@NonNull String permissionId) {
        return REGISTRY.get(permissionId);
    }

    @NonNull
    public static Map<String, PermissionInfo> getRegistry() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public void initialize(@NonNull Context context) {
        if (initialized) return;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dialogProvider = new PermissionDialogProvider();
        initialized = true;
    }

    public void setDialogProvider(@NonNull PermissionDialogProvider provider) {
        this.dialogProvider = provider;
    }

    public boolean isGranted(@NonNull String pluginId, @NonNull String permissionId) {
        Set<String> granted = getGrantedSet(pluginId);
        return granted.contains(permissionId);
    }

    public void grant(@NonNull String pluginId, @NonNull String permissionId) {
        Set<String> granted = getGrantedSet(pluginId);
        granted.add(permissionId);
        saveGrantedSet(pluginId, granted);
        FileLog.d("PermissionManager: granted " + permissionId + " to " + pluginId);
    }

    public void revoke(@NonNull String pluginId, @NonNull String permissionId) {
        Set<String> granted = getGrantedSet(pluginId);
        granted.remove(permissionId);
        saveGrantedSet(pluginId, granted);
        FileLog.d("PermissionManager: revoked " + permissionId + " from " + pluginId);
    }

    public void revokeAll(@NonNull String pluginId) {
        prefs.edit().remove(KEY_GRANTED + pluginId).apply();
        FileLog.d("PermissionManager: revoked all permissions from " + pluginId);
    }

    @NonNull
    public Set<String> getGrantedPermissions(@NonNull String pluginId) {
        return Collections.unmodifiableSet(getGrantedSet(pluginId));
    }

    @NonNull
    public PermissionRequestResult requestPermissions(
            @NonNull String pluginId,
            @NonNull String pluginName,
            @NonNull List<String> requiredPermissions,
            @NonNull List<String> optionalPermissions
    ) {
        List<String> missingRequired = new ArrayList<>();
        List<String> missingOptional = new ArrayList<>();

        for (String perm : requiredPermissions) {
            if (!isGranted(pluginId, perm)) {
                missingRequired.add(perm);
            }
        }
        for (String perm : optionalPermissions) {
            if (!isGranted(pluginId, perm)) {
                missingOptional.add(perm);
            }
        }

        if (missingRequired.isEmpty() && missingOptional.isEmpty()) {
            return new PermissionRequestResult(true, Collections.emptyList(), Collections.emptyList());
        }

        PermissionRequestResult result = dialogProvider.showPermissionDialog(
                pluginId, pluginName, missingRequired, missingOptional
        );

        if (result.allGranted) {
            for (String perm : result.justGranted) {
                grant(pluginId, perm);
            }
        }

        return result;
    }

    public boolean checkAndroidPermission(@NonNull Context context, @NonNull String androidPermission) {
        return ContextCompat.checkSelfPermission(context, androidPermission)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private Set<String> getGrantedSet(@NonNull String pluginId) {
        return new HashSet<>(prefs.getStringSet(KEY_GRANTED + pluginId, Collections.emptySet()));
    }

    private void saveGrantedSet(@NonNull String pluginId, @NonNull Set<String> permissions) {
        prefs.edit().putStringSet(KEY_GRANTED + pluginId, permissions).apply();
    }

    public static final class PermissionRequestResult {
        public final boolean allGranted;
        @NonNull
        public final List<String> justGranted;
        @NonNull
        public final List<String> denied;

        PermissionRequestResult(boolean allGranted, @NonNull List<String> justGranted, @NonNull List<String> denied) {
            this.allGranted = allGranted;
            this.justGranted = justGranted;
            this.denied = denied;
        }
    }

    public static class PermissionDialogProvider {
        @NonNull
        public PermissionRequestResult showPermissionDialog(
                @NonNull String pluginId,
                @NonNull String pluginName,
                @NonNull List<String> requiredPermissions,
                @NonNull List<String> optionalPermissions
        ) {
            return new PermissionRequestResult(true, new ArrayList<>(requiredPermissions), Collections.emptyList());
        }
    }

    @NonNull
    public static List<String> checkSdkInt(@NonNull String permissionId) {
        if ("android.camera".equals(permissionId)) {
            return Collections.singletonList(android.Manifest.permission.CAMERA);
        }
        if ("android.microphone".equals(permissionId)) {
            return Collections.singletonList(android.Manifest.permission.RECORD_AUDIO);
        }
        if ("android.location".equals(permissionId)) {
            return Collections.singletonList(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if ("android.storage.read".equals(permissionId)) {
            if (Build.VERSION.SDK_INT >= 33) {
                return Collections.singletonList(android.Manifest.permission.READ_MEDIA_IMAGES);
            }
            return Collections.singletonList(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if ("android.storage.write".equals(permissionId)) {
            if (Build.VERSION.SDK_INT >= 33) {
                return Collections.emptyList();
            }
            return Collections.singletonList(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return Collections.emptyList();
    }
}
