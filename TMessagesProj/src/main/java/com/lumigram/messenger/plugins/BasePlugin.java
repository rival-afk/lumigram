package com.lumigram.messenger.plugins;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public abstract class BasePlugin {

    @NonNull
    protected final PluginManifest manifest;

    @NonNull
    protected final File pluginDir;

    @NonNull
    protected final File runtimeDir;

    protected volatile boolean active;

    public BasePlugin(
            @NonNull PluginManifest manifest,
            @NonNull File pluginDir,
            @NonNull File runtimeDir
    ) {
        this.manifest = manifest;
        this.pluginDir = pluginDir;
        this.runtimeDir = runtimeDir;
        this.active = false;
    }

    @NonNull
    public final PluginManifest getManifest() {
        return manifest;
    }

    @NonNull
    public final File getPluginDir() {
        return pluginDir;
    }

    @NonNull
    public final File getRuntimeDir() {
        return runtimeDir;
    }

    public final boolean isActive() {
        return active;
    }

    public void onPluginLoad() {
    }

    @Nullable
    public HookResult onUpdateHook(
            @NonNull String updateName,
            int account,
            @NonNull Object update
    ) {
        return HookResult.pass();
    }

    @Nullable
    public HookResult onMessageHook(
            int account,
            @NonNull Object message
    ) {
        return HookResult.pass();
    }

    @Nullable
    public HookResult onMessageSend(
            int account,
            @NonNull Object message
    ) {
        return HookResult.pass();
    }

    @Nullable
    public HookResult onMessageReceived(
            int account,
            @NonNull Object message
    ) {
        return HookResult.pass();
    }

    @Nullable
    public HookResult onChatOpen(
            int account,
            long chatId
    ) {
        return HookResult.pass();
    }

    @Nullable
    public HookResult onChatClose(
            int account,
            long chatId
    ) {
        return HookResult.pass();
    }

    @Nullable
    public Object onButtonClick(
            int account,
            @NonNull Object button
    ) {
        return null;
    }

    @Nullable
    public HookResult onFileDownload(
            int account,
            @NonNull Object file
    ) {
        return HookResult.pass();
    }

    @Nullable
    public HookResult onCallEvent(
            int account,
            @NonNull String event,
            @NonNull Object call
    ) {
        return HookResult.pass();
    }

    public void onPluginUnload() {
    }

    public static final class HookResult {
        public final boolean handled;
        @Nullable
        public final Object data;

        private HookResult(boolean handled, @Nullable Object data) {
            this.handled = handled;
            this.data = data;
        }

        @NonNull
        public static HookResult pass() {
            return new HookResult(false, null);
        }

        @NonNull
        public static HookResult handled(@Nullable Object data) {
            return new HookResult(true, data);
        }

        @NonNull
        public static HookResult handled() {
            return new HookResult(true, null);
        }
    }
}
