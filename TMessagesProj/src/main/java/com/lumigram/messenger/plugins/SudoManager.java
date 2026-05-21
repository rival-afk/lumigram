package com.lumigram.messenger.plugins;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SudoManager {

    public enum Backend {
        NONE,
        ROOT,
        SHIZUKU
    }

    private static final SudoManager INSTANCE = new SudoManager();

    private volatile Backend availableBackend = Backend.NONE;
    private volatile boolean initialized;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lumigram-sudo");
        t.setDaemon(true);
        return t;
    });

    private SudoManager() {
    }

    @NonNull
    public static SudoManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(@NonNull Context context) {
        if (initialized) return;

        if (checkRootAccess()) {
            availableBackend = Backend.ROOT;
            FileLog.d("SudoManager: root access available");
        }

        initialized = true;
    }

    public boolean isAvailable() {
        return availableBackend != Backend.NONE;
    }

    @NonNull
    public Backend getAvailableBackend() {
        return availableBackend;
    }

    public boolean isRootAvailable() {
        return availableBackend == Backend.ROOT;
    }

    @Nullable
    public SudoResult execute(@NonNull String command) {
        return execute(command, 30000);
    }

    @Nullable
    public SudoResult execute(@NonNull String command, long timeoutMs) {
        if (!initialized) {
            return new SudoResult(false, -1, "SudoManager not initialized", "");
        }

        switch (availableBackend) {
            case ROOT:
                return executeRoot(command, timeoutMs);
            default:
                return new SudoResult(false, -1, "No privileged backend available", "");
            }
    }

    public void executeAsync(@NonNull String command, @Nullable SudoCallback callback) {
        executor.execute(() -> {
            SudoResult result = execute(command);
            if (callback != null) {
                callback.onResult(result);
            }
        });
    }

    @Nullable
    private SudoResult executeRoot(@NonNull String command, long timeoutMs) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append('\n');
                    }
                } catch (Exception ignored) {
                }
            });
            stdoutReader.setDaemon(true);
            stdoutReader.start();

            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append('\n');
                    }
                } catch (Exception ignored) {
                }
            });
            stderrReader.setDaemon(true);
            stderrReader.start();

            stdoutReader.join(timeoutMs);
            stderrReader.join(timeoutMs);

            int exitCode = process.waitFor();
            return new SudoResult(exitCode == 0, exitCode,
                    stderr.length() > 0 ? stderr.toString().trim() : null,
                    stdout.toString().trim());

        } catch (Exception e) {
            FileLog.e("SudoManager: root command failed", e);
            return new SudoResult(false, -1, e.getMessage(), "");
        }
    }

    private boolean checkRootAccess() {
        for (String path : new String[]{"/sbin/su", "/system/bin/su", "/system/xbin/su",
                "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su",
                "/data/local/su"}) {
            if (new File(path).exists()) {
                return true;
            }
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            int code = process.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public static final class SudoResult {
        public final boolean success;
        public final int exitCode;
        @Nullable
        public final String error;
        @NonNull
        public final String output;

        SudoResult(boolean success, int exitCode, @Nullable String error, @NonNull String output) {
            this.success = success;
            this.exitCode = exitCode;
            this.error = error;
            this.output = output;
        }
    }

    public interface SudoCallback {
        void onResult(@NonNull SudoResult result);
    }
}
