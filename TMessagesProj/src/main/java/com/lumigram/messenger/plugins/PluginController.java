package com.lumigram.messenger.plugins;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lumigram.messenger.plugins.proxy.JavaDynamicProxyFactory;
import com.lumigram.messenger.plugins.proxy.StaticProxyRegistry;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.util.List;

public final class PluginController {

    private static final PluginController INSTANCE = new PluginController();

    private volatile boolean initialized;
    private PluginManager pluginManager;

    private PluginController() {
    }

    @NonNull
    public static PluginController getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(@NonNull Context context) {
        if (initialized) {
            return;
        }

        pluginManager = PluginManager.getInstance();
        pluginManager.initialize(context);
        initialized = true;
        FileLog.d("PluginController initialized (delegating to PluginManager)");
    }

    @NonNull
    public List<PluginManifest> getInstalledPlugins() {
        return pluginManager.getInstalledPlugins();
    }

    @Nullable
    public File getPluginsDir() {
        return pluginManager.getPluginsDir();
    }

    @NonNull
    public <T> T createDynamicProxy(@NonNull Class<T> iface, @NonNull InvocationHandler handler) {
        return pluginManager.createDynamicProxy(iface, handler);
    }

    public void registerStaticProxy(@NonNull String className, @NonNull StaticProxyRegistry.MethodInterceptor interceptor) {
        pluginManager.registerStaticProxy(className, interceptor);
    }

    @NonNull
    public List<String> getRequestedPythonRequirements() {
        return pluginManager.getRequestedPythonRequirements();
    }
}
