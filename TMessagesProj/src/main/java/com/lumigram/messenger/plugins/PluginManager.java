package com.lumigram.messenger.plugins;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lumigram.messenger.plugins.proxy.JavaDynamicProxyFactory;
import com.lumigram.messenger.plugins.proxy.StaticProxyRegistry;

import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

public final class PluginManager {

    private static final PluginManager INSTANCE = new PluginManager();

    private final Map<String, PluginManifest> installedPlugins = new ConcurrentHashMap<>();
    private final Map<String, PluginLifecycle> pluginStates = new ConcurrentHashMap<>();
    private final Map<String, BasePlugin> activeInstances = new ConcurrentHashMap<>();
    private final List<PluginLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    private volatile boolean initialized;
    private File pluginsDir;
    private File runtimeDir;
    private PythonPackageRegistry pythonPackageRegistry;
    private PluginSafeMode safeMode;
    private PermissionManager permissionManager;
    private PluginLogger pluginLogger;
    private SecureVault secureVault;
    private SudoManager sudoManager;
    private Context appContext;

    private final PriorityBlockingQueue<PluginLoadTask> loadQueue = new PriorityBlockingQueue<>(
            11, (a, b) -> Integer.compare(b.priority, a.priority)
    );
    private volatile Thread loaderThread;
    private volatile boolean loaderRunning;

    private final ExecutorService pluginExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, "lumigram-plugin-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, throwable) -> {
                FileLog.e("PluginManager: uncaught in " + thread.getName(), throwable);
            });
            return t;
        }
    });

    private PluginManager() {
    }

    @NonNull
    public static PluginManager getInstance() {
        return INSTANCE;
    }

    public interface PluginLifecycleListener {
        void onPluginStateChanged(@NonNull String pluginId, @NonNull PluginLifecycle oldState, @NonNull PluginLifecycle newState, @Nullable Throwable error);
    }

    public void addLifecycleListener(@NonNull PluginLifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    public void removeLifecycleListener(@NonNull PluginLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    public synchronized void initialize(@NonNull Context context) {
        if (initialized) {
            return;
        }

        this.appContext = context.getApplicationContext();
        File filesDir = context.getFilesDir();
        pluginsDir = new File(filesDir, "lumigram/plugins");
        runtimeDir = new File(filesDir, "lumigram/plugin_runtime");

        safeMode = new PluginSafeMode(context);

        mkdirs(pluginsDir);
        mkdirs(runtimeDir);

        pythonPackageRegistry = new PythonPackageRegistry(runtimeDir);

        permissionManager = PermissionManager.getInstance();
        permissionManager.initialize(context);

        pluginLogger = PluginLogger.getInstance();
        pluginLogger.initialize(context);

        secureVault = SecureVault.getInstance();
        secureVault.initialize(context);

        sudoManager = SudoManager.getInstance();
        sudoManager.initialize(context);

        rescanInstalledPlugins();

        if (safeMode.isSafeMode()) {
            String crashedId = safeMode.getLastCrashedPlugin();
            FileLog.w("PluginManager: initialized in SAFE MODE, last crash from " + crashedId);
            for (PluginManifest manifest : installedPlugins.values()) {
                setState(manifest.id, PluginLifecycle.DISABLED);
            }
        }

        startLoaderThread();
        if (pluginLogger != null) {
            pluginLogger.i(null, "PluginManager initialized: " + installedPlugins.size() + " plugins, safeMode=" + safeMode.isSafeMode());
        }
        initialized = true;
        FileLog.d("PluginManager initialized: " + installedPlugins.size() + " plugins discovered, safeMode=" + safeMode.isSafeMode());
    }

    private void startLoaderThread() {
        if (loaderThread != null && loaderThread.isAlive()) {
            return;
        }
        loaderRunning = true;
        loaderThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            while (loaderRunning) {
                try {
                    PluginLoadTask task = loadQueue.poll(5, TimeUnit.SECONDS);
                    if (task != null) {
                        PluginManifest manifest = installedPlugins.get(task.pluginId);
                        if (manifest != null && pluginStates.get(task.pluginId) == PluginLifecycle.LOADING) {
                            String reason = checkPluginCompatibility(manifest);
                            if (reason != null) {
                                FileLog.e("PluginManager: " + task.pluginId + " not compatible: " + reason);
                                setState(task.pluginId, PluginLifecycle.ERROR);
                                continue;
                            }
                            if (!requestPluginPermissions(manifest)) {
                                FileLog.e("PluginManager: permission denied for " + task.pluginId);
                                setState(task.pluginId, PluginLifecycle.DISABLED);
                                continue;
                            }
                            BasePlugin instance = createPluginInstance(manifest);
                            if (instance != null) {
                                activeInstances.put(task.pluginId, instance);
                                setState(task.pluginId, PluginLifecycle.RUNNING);
                                FileLog.d("PluginManager: loaded " + task.pluginId + " from queue (priority=" + task.priority + ")");
                            } else {
                                setState(task.pluginId, PluginLifecycle.ERROR);
                            }
                        }
                    }
                } catch (InterruptedException ignored) {
                    break;
                } catch (Throwable t) {
                    FileLog.e("PluginManager: loader thread error", t);
                }
            }
        }, "lumigram-loader");
        loaderThread.setDaemon(true);
        loaderThread.start();
        FileLog.d("PluginManager: loader thread started");
    }

    private void stopLoaderThread() {
        loaderRunning = false;
        if (loaderThread != null) {
            loaderThread.interrupt();
            loaderThread = null;
        }
    }

    public synchronized void rescanInstalledPlugins() {
        installedPlugins.clear();
        if (pluginsDir == null || !pluginsDir.exists()) {
            return;
        }

        File[] archives = pluginsDir.listFiles(file ->
                file.isFile() && PluginArchiveParser.isPluginFile(file)
        );

        if (archives == null) {
            return;
        }

        for (File archive : archives) {
            PluginManifest manifest = PluginArchiveParser.parse(archive);
            if (manifest == null) {
                FileLog.e("PluginManager: failed to parse " + archive.getName());
                continue;
            }
            if (manifest.signer != null) {
                PluginSignatureValidator.VerifyResult vr = PluginSignatureValidator.verifyPlugin(archive, manifest);
                if (!vr.valid) {
                    FileLog.e("PluginManager: signature verification failed for " + manifest.id + ": " + vr.message);
                    continue;
                }
                FileLog.d("PluginManager: signature verified for " + manifest.id);
            }
            installedPlugins.put(manifest.id, manifest);
            pluginStates.putIfAbsent(manifest.id, PluginLifecycle.INSTALLED);
            if (pythonPackageRegistry != null) {
                pythonPackageRegistry.registerRequirements(manifest.id, manifest.pythonRequirements);
            }
        }
    }

    @NonNull
    public synchronized PluginLifecycle getPluginState(@NonNull String pluginId) {
        return pluginStates.getOrDefault(pluginId, PluginLifecycle.UNINSTALLED);
    }

    @Nullable
    public synchronized PluginManifest getPlugin(@NonNull String pluginId) {
        return installedPlugins.get(pluginId);
    }

    @NonNull
    public synchronized List<PluginManifest> getInstalledPlugins() {
        List<PluginManifest> list = new ArrayList<>(installedPlugins.values());
        list.sort((a, b) -> a.id.compareToIgnoreCase(b.id));
        return Collections.unmodifiableList(list);
    }

    @Nullable
    public File getPluginsDir() {
        return pluginsDir;
    }

    public boolean isSafeMode() {
        return safeMode != null && safeMode.isSafeMode();
    }

    public void exitSafeMode() {
        if (safeMode != null) {
            safeMode.exitSafeMode();
            for (PluginManifest manifest : installedPlugins.values()) {
                if (pluginStates.get(manifest.id) == PluginLifecycle.DISABLED) {
                    setState(manifest.id, PluginLifecycle.INSTALLED);
                }
            }
        }
    }

    public void installPlugin(@NonNull File archiveFile) {
        if (!PluginArchiveParser.isPluginFile(archiveFile)) {
            FileLog.e("PluginManager: not a plugin file: " + archiveFile.getName());
            return;
        }

        if (pluginsDir == null) {
            FileLog.e("PluginManager: not initialized");
            return;
        }

        PluginManifest manifest = PluginArchiveParser.parse(archiveFile);
        if (manifest == null) {
            FileLog.e("PluginManager: invalid plugin archive: " + archiveFile.getName());
            return;
        }

        if (manifest.signer != null) {
            PluginSignatureValidator.VerifyResult vr = PluginSignatureValidator.verifyPlugin(archiveFile, manifest);
            if (!vr.valid) {
                FileLog.e("PluginManager: signature verification failed for " + manifest.id + ": " + vr.message);
                return;
            }
            FileLog.d("PluginManager: signature verified for " + manifest.id);
        }

        String reason = checkPluginCompatibility(manifest);
        if (reason != null) {
            FileLog.e("PluginManager: " + manifest.id + " not compatible: " + reason);
            return;
        }

        File dest = new File(pluginsDir, manifest.id + ".plugin");
        try {
            copyFile(archiveFile, dest);
        } catch (IOException e) {
            FileLog.e(e);
            return;
        }

        installedPlugins.put(manifest.id, manifest);
        setState(manifest.id, PluginLifecycle.INSTALLED);

        if (pythonPackageRegistry != null) {
            pythonPackageRegistry.registerRequirements(manifest.id, manifest.pythonRequirements);
        }

        if (safeMode != null) {
            safeMode.clearCrashHistory(manifest.id);
        }

        FileLog.d("PluginManager: installed plugin " + manifest.id + " v" + manifest.version);

        loadQueue.offer(new PluginLoadTask(manifest.id, manifest.priority));
    }

    public synchronized boolean uninstallPlugin(@NonNull String pluginId) {
        PluginManifest manifest = installedPlugins.get(pluginId);
        if (manifest == null) {
            return false;
        }

        unloadPlugin(pluginId);

        if (pluginsDir != null) {
            File pluginFile = new File(pluginsDir, pluginId + ".plugin");
            if (pluginFile.exists()) {
                pluginFile.delete();
            }
            deleteRecursive(new File(pluginsDir, pluginId));
        }

        if (runtimeDir != null) {
            deleteRecursive(new File(runtimeDir, pluginId));
        }

        if (permissionManager != null) {
            permissionManager.revokeAll(pluginId);
        }

        installedPlugins.remove(pluginId);
        setState(pluginId, PluginLifecycle.UNINSTALLED);
        FileLog.d("PluginManager: uninstalled plugin " + pluginId);
        return true;
    }

    public synchronized void loadPlugin(@NonNull String pluginId) {
        PluginManifest manifest = installedPlugins.get(pluginId);
        if (manifest == null) {
            FileLog.e("PluginManager: cannot load unknown plugin " + pluginId);
            return;
        }

        if (safeMode != null && safeMode.isSafeMode()) {
            FileLog.w("PluginManager: safe mode active, cannot load " + pluginId);
            return;
        }

        if (safeMode != null && safeMode.hasCrashed(pluginId)) {
            FileLog.w("PluginManager: plugin " + pluginId + " previously crashed, skipping auto-load");
            return;
        }

        PluginLifecycle currentState = pluginStates.get(pluginId);
        if (currentState == PluginLifecycle.RUNNING || currentState == PluginLifecycle.LOADING) {
            return;
        }

        String reason = checkPluginCompatibility(manifest);
        if (reason != null) {
            FileLog.e("PluginManager: " + pluginId + " not compatible: " + reason);
            setState(pluginId, PluginLifecycle.ERROR);
            return;
        }

        if (!requestPluginPermissions(manifest)) {
            FileLog.e("PluginManager: permission denied for " + pluginId);
            setState(pluginId, PluginLifecycle.DISABLED);
            return;
        }

        setState(pluginId, PluginLifecycle.LOADING);
        loadQueue.offer(new PluginLoadTask(pluginId, manifest.priority));
        FileLog.d("PluginManager: queued " + pluginId + " for loading (priority=" + manifest.priority + ")");
    }

    public synchronized void loadPluginNow(@NonNull String pluginId) {
        PluginManifest manifest = installedPlugins.get(pluginId);
        if (manifest == null) {
            FileLog.e("PluginManager: cannot load unknown plugin " + pluginId);
            return;
        }

        if (safeMode != null && safeMode.isSafeMode()) {
            FileLog.w("PluginManager: safe mode active, cannot load " + pluginId);
            return;
        }

        if (safeMode != null && safeMode.hasCrashed(pluginId)) {
            FileLog.w("PluginManager: plugin " + pluginId + " previously crashed, skipping auto-load");
            return;
        }

        PluginLifecycle currentState = pluginStates.get(pluginId);
        if (currentState == PluginLifecycle.RUNNING) {
            return;
        }

        String reason = checkPluginCompatibility(manifest);
        if (reason != null) {
            FileLog.e("PluginManager: " + pluginId + " not compatible: " + reason);
            setState(pluginId, PluginLifecycle.ERROR);
            return;
        }

        if (!requestPluginPermissions(manifest)) {
            FileLog.e("PluginManager: permission denied for " + pluginId);
            setState(pluginId, PluginLifecycle.DISABLED);
            return;
        }

        setState(pluginId, PluginLifecycle.LOADING);
        BasePlugin instance = createPluginInstance(manifest);
        if (instance == null) {
            setState(pluginId, PluginLifecycle.ERROR);
            return;
        }
        activeInstances.put(pluginId, instance);
        setState(pluginId, PluginLifecycle.RUNNING);
    }

    public synchronized void unloadPlugin(@NonNull String pluginId) {
        BasePlugin instance = activeInstances.remove(pluginId);
        if (instance != null) {
            try {
                instance.onPluginUnload();
            } catch (Throwable t) {
                FileLog.e("PluginManager: error unloading " + pluginId, t);
            }
            PluginLifecycle current = pluginStates.get(pluginId);
            if (current == PluginLifecycle.RUNNING || current == PluginLifecycle.ERROR) {
                setState(pluginId, PluginLifecycle.INSTALLED);
            }
        }
    }

    public synchronized void disablePlugin(@NonNull String pluginId) {
        unloadPlugin(pluginId);
        setState(pluginId, PluginLifecycle.DISABLED);
    }

    public synchronized void enablePlugin(@NonNull String pluginId) {
        PluginManifest manifest = installedPlugins.get(pluginId);
        if (manifest == null) {
            return;
        }
        pluginStates.put(pluginId, PluginLifecycle.INSTALLED);
        if (safeMode != null) {
            safeMode.clearCrashHistory(pluginId);
        }
    }

    @Nullable
    public static String checkPluginCompatibility(@NonNull PluginManifest manifest) {
        if (manifest.minSdk > android.os.Build.VERSION.SDK_INT) {
            return "Requires Android API " + manifest.minSdk + " (current: " + android.os.Build.VERSION.SDK_INT + ")";
        }

        String currentVersion = BuildConfig.VERSION_NAME;
        if (currentVersion != null && manifest.minLumigramVersion != null) {
            try {
                String[] minParts = manifest.minLumigramVersion.split("\\.");
                String[] curParts = currentVersion.split("\\.");
                int maxLen = Math.max(minParts.length, curParts.length);
                for (int i = 0; i < maxLen; i++) {
                    int minPart = i < minParts.length ? parseIntSafe(minParts[i]) : 0;
                    int curPart = i < curParts.length ? parseIntSafe(curParts[i]) : 0;
                    if (curPart < minPart) {
                        return "Requires Lumigram v" + manifest.minLumigramVersion + " (current: v" + currentVersion + ")";
                    }
                    if (curPart > minPart) {
                        break;
                    }
                }
            } catch (Exception e) {
                FileLog.e("PluginManager: version compare failed", e);
            }
        }
        return null;
    }

    private static int parseIntSafe(@NonNull String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Nullable
    public BasePlugin getActivePlugin(@NonNull String pluginId) {
        return activeInstances.get(pluginId);
    }

    @NonNull
    public List<BasePlugin> getActivePlugins() {
        return new ArrayList<>(activeInstances.values());
    }

    public <T> T createDynamicProxy(@NonNull Class<T> iface, @NonNull InvocationHandler handler) {
        return JavaDynamicProxyFactory.create(iface, handler);
    }

    public void registerStaticProxy(@NonNull String className, @NonNull StaticProxyRegistry.MethodInterceptor interceptor) {
        StaticProxyRegistry.registerClassInterceptor(className, interceptor);
    }

    @NonNull
    public List<String> getRequestedPythonRequirements() {
        if (pythonPackageRegistry == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(pythonPackageRegistry.snapshot());
    }

    public void dispatchUpdateHook(@NonNull String updateName, int account, @NonNull Object update) {
        for (BasePlugin plugin : activeInstances.values()) {
            if (!plugin.isActive()) {
                continue;
            }
            pluginExecutor.execute(() -> {
                try {
                    plugin.onUpdateHook(updateName, account, update);
                } catch (Throwable t) {
                    handlePluginCrash(plugin.getManifest().id, t);
                }
            });
        }
    }

    void handlePluginCrash(@NonNull String pluginId, @NonNull Throwable error) {
        FileLog.e("PluginManager: plugin " + pluginId + " crashed", error);
        if (pluginLogger != null) {
            pluginLogger.log(PluginLogger.Level.ERROR, PluginLogger.Category.PLUGIN_EXECUTION,
                    pluginId, "Plugin crashed: " + error.getMessage(), error);
        }
        if (safeMode != null) {
            safeMode.recordCrash(pluginId);
        }
        unloadPlugin(pluginId);
        setState(pluginId, PluginLifecycle.ERROR);

        if (safeMode != null && safeMode.isSafeMode()) {
            disablePlugin(pluginId);
        }
    }

    public ExecutorService getPluginExecutor() {
        return pluginExecutor;
    }

    public void enqueueLoad(@NonNull String pluginId) {
        PluginManifest manifest = installedPlugins.get(pluginId);
        if (manifest == null) {
            return;
        }
        loadQueue.offer(new PluginLoadTask(pluginId, manifest.priority));
    }

    public int getLoadQueueSize() {
        return loadQueue.size();
    }

    private boolean requestPluginPermissions(@NonNull PluginManifest manifest) {
        if (permissionManager == null) {
            return true;
        }
        PermissionManager.PermissionRequestResult result = permissionManager.requestPermissions(
                manifest.id, manifest.name,
                manifest.permissions, manifest.optionalPermissions
        );
        return result.allGranted;
    }

    private void setState(@NonNull String pluginId, @NonNull PluginLifecycle newState) {
        PluginLifecycle oldState = pluginStates.put(pluginId, newState);
        if (oldState == newState) {
            return;
        }
        for (PluginLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onPluginStateChanged(pluginId, oldState != null ? oldState : PluginLifecycle.UNINSTALLED, newState, null);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    @Nullable
    private BasePlugin createPluginInstance(@NonNull PluginManifest manifest) {
        try {
            if (manifest.type == PluginManifest.PluginType.PYTHON) {
                return createPythonPlugin(manifest);
            } else {
                return createCppPlugin(manifest);
            }
        } catch (Throwable t) {
            FileLog.e("PluginManager: failed to create instance for " + manifest.id, t);
            return null;
        }
    }

    @Nullable
    private BasePlugin createPythonPlugin(@NonNull PluginManifest manifest) {
        File pluginRuntimeDir = new File(runtimeDir, manifest.id);
        mkdirs(pluginRuntimeDir);
        return new PythonPluginInstance(appContext, manifest, new File(pluginsDir, manifest.id + ".plugin"), pluginRuntimeDir);
    }

    @Nullable
    private BasePlugin createCppPlugin(@NonNull PluginManifest manifest) {
        File pluginRuntimeDir = new File(runtimeDir, manifest.id);
        mkdirs(pluginRuntimeDir);
        return new CppPluginInstance(manifest, new File(pluginsDir, manifest.id + ".plugin"), pluginRuntimeDir);
    }

    private static void mkdirs(@NonNull File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            FileLog.e("PluginManager: failed to create " + dir);
        }
    }

    private static void copyFile(@NonNull File src, @NonNull File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static void deleteRecursive(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    public void shutdown() {
        stopLoaderThread();
        loadQueue.clear();
        for (String pluginId : new ArrayList<>(activeInstances.keySet())) {
            unloadPlugin(pluginId);
        }
        pluginExecutor.shutdownNow();
    }

    private static final class PluginLoadTask {
        final String pluginId;
        final int priority;

        PluginLoadTask(String pluginId, int priority) {
            this.pluginId = pluginId;
            this.priority = priority;
        }
    }

    static class PythonPluginInstance extends BasePlugin {
        private final Context context;
        private boolean extracted;
        private PythonRuntime.PythonSession pythonSession;

        PythonPluginInstance(
                @NonNull Context context,
                @NonNull PluginManifest manifest,
                @NonNull File pluginDir,
                @NonNull File runtimeDir
        ) {
            super(manifest, pluginDir, runtimeDir);
            this.context = context;
        }

        @Override
        public void onPluginLoad() {
            super.onPluginLoad();
            if (!extracted) {
                extracted = extractPlugin();
            }

            if (PythonRuntime.isAvailable() && extracted) {
                pythonSession = PythonRuntime.createSession(
                        context,
                        manifest.id,
                        runtimeDir,
                        manifest.entryPoint != null ? manifest.entryPoint : "plugin:Plugin",
                        manifest.pythonRequirements.isEmpty() ? null : manifest.pythonRequirements
                );
                if (pythonSession != null) {
                    pythonSession.call("on_plugin_load");
                }
            }

            active = true;
            FileLog.d("PythonPluginInstance loaded: " + manifest.id);
        }

        @Override
        public void onPluginUnload() {
            active = false;
            if (pythonSession != null) {
                pythonSession.call("on_plugin_unload");
                pythonSession = null;
            }
            super.onPluginUnload();
            FileLog.d("PythonPluginInstance unloaded: " + manifest.id);
        }

        @Nullable
        @Override
        public HookResult onUpdateHook(@NonNull String updateName, int account, @NonNull Object update) {
            if (pythonSession != null) {
                Object result = pythonSession.call("on_update_hook", updateName, account, update);
                if (result != null) {
                    return HookResult.handled(result);
                }
            }
            return super.onUpdateHook(updateName, account, update);
        }

        private boolean extractPlugin() {
            if (!pluginDir.exists()) {
                return false;
            }
            try (ZipFile zipFile = new ZipFile(pluginDir)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    File target = new File(runtimeDir, entry.getName());
                    if (entry.isDirectory()) {
                        target.mkdirs();
                    } else {
                        target.getParentFile().mkdirs();
                        try (java.io.InputStream in = zipFile.getInputStream(entry);
                             java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                FileLog.e("PythonPluginInstance: extract failed for " + manifest.id, e);
                return false;
            }
        }
    }

    static class CppPluginInstance extends BasePlugin {
        private NativePluginBridge.NativeHandle nativeHandle;

        CppPluginInstance(
                @NonNull PluginManifest manifest,
                @NonNull File pluginDir,
                @NonNull File runtimeDir
        ) {
            super(manifest, pluginDir, runtimeDir);
        }

        @Override
        public void onPluginLoad() {
            super.onPluginLoad();
            if (!NativePluginBridge.isAvailable()) {
                FileLog.e("CppPluginInstance: native bridge not available for " + manifest.id);
                return;
            }
            File extractedSo = extractCppLibrary();
            if (extractedSo == null) {
                FileLog.e("CppPluginInstance: failed to extract native lib for " + manifest.id);
                return;
            }
            nativeHandle = NativePluginBridge.loadPlugin(
                    extractedSo.getAbsolutePath(), manifest.id, manifest.version);
            if (nativeHandle == null) {
                FileLog.e("CppPluginInstance: failed to load native plugin " + manifest.id);
                return;
            }
            if (NativePluginBridge.callOnLoad(nativeHandle)) {
                active = true;
                FileLog.d("CppPluginInstance loaded: " + manifest.id);
            } else {
                FileLog.e("CppPluginInstance: onLoad failed for " + manifest.id);
            }
        }

        @Nullable
        private File extractCppLibrary() {
            String libName = manifest.cppLibrary;
            if (libName == null) {
                libName = "lib" + manifest.id + ".so";
            }
            String arch = System.getProperty("os.arch");
            String abi;
            if (arch != null) {
                String lower = arch.toLowerCase();
                if (lower.contains("aarch64") || lower.contains("arm64")) {
                    abi = "arm64-v8a";
                } else if (lower.contains("arm")) {
                    abi = "armeabi-v7a";
                } else if (lower.contains("x86_64")) {
                    abi = "x86_64";
                } else {
                    abi = "x86";
                }
            } else {
                abi = "arm64-v8a";
            }
            String[] candidates = {
                    "lib/" + abi + "/" + libName,
                    "libs/" + abi + "/" + libName,
                    libName
            };
            try (ZipFile zipFile = new ZipFile(pluginDir)) {
                for (String candidate : candidates) {
                    ZipEntry entry = zipFile.getEntry(candidate);
                    if (entry != null) {
                        File outFile = new File(runtimeDir, libName);
                        outFile.getParentFile().mkdirs();
                        try (InputStream in = zipFile.getInputStream(entry);
                             FileOutputStream out = new FileOutputStream(outFile)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                        outFile.setExecutable(true, false);
                        FileLog.d("CppPluginInstance: extracted " + libName + " for " + manifest.id);
                        return outFile;
                    }
                }
                FileLog.e("CppPluginInstance: no native lib found in " + pluginDir.getName()
                        + " (tried " + String.join(", ", candidates) + ")");
            } catch (Exception e) {
                FileLog.e("CppPluginInstance: extract failed for " + manifest.id, e);
            }
            return null;
        }

        @Override
        public void onPluginUnload() {
            active = false;
            if (nativeHandle != null) {
                NativePluginBridge.callOnUnload(nativeHandle);
                NativePluginBridge.unloadPlugin(nativeHandle);
                nativeHandle = null;
            }
            super.onPluginUnload();
            FileLog.d("CppPluginInstance unloaded: " + manifest.id);
        }
    }
}
