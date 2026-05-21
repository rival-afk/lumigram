package com.lumigram.messenger.plugins;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

public final class NativePluginBridge {

    private static boolean nativeLoaded;

    static {
        boolean libLoaded = false;
        try {
            System.loadLibrary("lumi");
            libLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            FileLog.d("NativePluginBridge: native library not available");
        }
        if (libLoaded) {
            try {
                nativeLoaded = true;
                nativePing();
            } catch (UnsatisfiedLinkError e) {
                FileLog.d("NativePluginBridge: JNI bridge not found in native lib, C++ plugins disabled");
                nativeLoaded = false;
            }
        }
    }

    private static native boolean nativePing();

    private NativePluginBridge() {
    }

    public static boolean isAvailable() {
        return nativeLoaded;
    }

    @Nullable
    public static NativeHandle loadPlugin(
            @NonNull String pluginPath,
            @NonNull String pluginId,
            @NonNull String pluginVersion
    ) {
        if (!nativeLoaded) {
            FileLog.w("NativePluginBridge: JNI bridge not available");
            return null;
        }
        try {
            long handle = nativeLoadPlugin(pluginPath, pluginId, pluginVersion);
            if (handle == 0) {
                return null;
            }
            return new NativeHandle(handle);
        } catch (UnsatisfiedLinkError e) {
            nativeLoaded = false;
            FileLog.e("NativePluginBridge: JNI bridge lost");
            return null;
        }
    }

    public static boolean callOnLoad(@NonNull NativeHandle handle) {
        try {
            return nativeCallOnLoad(handle.value);
        } catch (UnsatisfiedLinkError e) {
            nativeLoaded = false;
            return false;
        }
    }

    public static boolean callOnUnload(@NonNull NativeHandle handle) {
        try {
            return nativeCallOnUnload(handle.value);
        } catch (UnsatisfiedLinkError e) {
            nativeLoaded = false;
            return false;
        }
    }

    public static void unloadPlugin(@NonNull NativeHandle handle) {
        try {
            nativeUnloadPlugin(handle.value);
        } catch (UnsatisfiedLinkError e) {
            nativeLoaded = false;
        }
    }

    private static native long nativeLoadPlugin(
            @NonNull String pluginPath,
            @NonNull String pluginId,
            @NonNull String pluginVersion
    );

    private static native boolean nativeCallOnLoad(long handle);
    private static native boolean nativeCallOnUnload(long handle);
    private static native void nativeUnloadPlugin(long handle);

    public static final class NativeHandle {
        private final long value;

        NativeHandle(long value) {
            this.value = value;
        }

        public long getValue() {
            return value;
        }
    }
}
