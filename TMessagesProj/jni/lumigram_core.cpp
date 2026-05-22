#include "lumigram_core.h"

#include <dlfcn.h>
#include <android/log.h>
#include <cstdlib>
#include <cstring>

#define LOG_TAG "LumigramCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static const char *LUMIGRAM_NATIVE_DIR = nullptr;

struct PluginHandle {
    void *dl_handle;
    LumigramPluginApi api;
    char plugin_id[128];
    bool initialized;
};

static void *findSymbol(void *handle, const char *name) {
    if (!handle) return nullptr;
    return dlsym(handle, name);
}

static bool loadPluginApi(void *dl_handle, LumigramPluginApi *api) {
    if (!dl_handle || !api) return false;

    api->init = (lumigram_plugin_init_t)findSymbol(dl_handle, "lumigram_plugin_init");
    api->on_load = (lumigram_plugin_on_load_t)findSymbol(dl_handle, "lumigram_plugin_on_load");
    api->on_unload = (lumigram_plugin_on_unload_t)findSymbol(dl_handle, "lumigram_plugin_on_unload");
    api->on_update = (lumigram_plugin_on_update_t)findSymbol(dl_handle, "lumigram_plugin_on_update");

    if (!api->init) {
        LOGE("Plugin missing lumigram_plugin_init symbol");
        return false;
    }
    return true;
}

JNIEXPORT jboolean JNICALL
Java_com_lumigram_messenger_plugins_NativePluginBridge_nativePing(
    JNIEnv *env, jclass clazz)
{
    LOGI("nativePing called - JNI bridge is alive");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_lumigram_messenger_plugins_NativePluginBridge_nativeLoadPlugin(
    JNIEnv *env, jclass clazz,
    jstring plugin_path, jstring plugin_id, jstring plugin_version)
{
    const char *path = env->GetStringUTFChars(plugin_path, nullptr);
    const char *id = env->GetStringUTFChars(plugin_id, nullptr);
    const char *version = env->GetStringUTFChars(plugin_version, nullptr);

    LOGI("Loading plugin: %s (%s) from %s", id, version, path);

    void *dl_handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!dl_handle) {
        LOGE("dlopen failed for %s: %s", path, dlerror());
        env->ReleaseStringUTFChars(plugin_path, path);
        env->ReleaseStringUTFChars(plugin_id, id);
        env->ReleaseStringUTFChars(plugin_version, version);
        return 0;
    }

    PluginHandle *handle = (PluginHandle *)calloc(1, sizeof(PluginHandle));
    if (!handle) {
        LOGE("malloc failed for PluginHandle");
        dlclose(dl_handle);
        env->ReleaseStringUTFChars(plugin_path, path);
        env->ReleaseStringUTFChars(plugin_id, id);
        env->ReleaseStringUTFChars(plugin_version, version);
        return 0;
    }

    handle->dl_handle = dl_handle;
    strncpy(handle->plugin_id, id, sizeof(handle->plugin_id) - 1);
    handle->initialized = false;

    if (!loadPluginApi(dl_handle, &handle->api)) {
        LOGE("Failed to load plugin API for %s", id);
        dlclose(dl_handle);
        free(handle);
        env->ReleaseStringUTFChars(plugin_path, path);
        env->ReleaseStringUTFChars(plugin_id, id);
        env->ReleaseStringUTFChars(plugin_version, version);
        return 0;
    }

    LumigramPluginInfo info;
    info.api_version = LUMIGRAM_PLUGIN_API_VERSION;
    info.plugin_id = handle->plugin_id;
    info.plugin_version = version;

    if (handle->api.init(&info) != 0) {
        LOGE("Plugin %s init failed", id);
        dlclose(dl_handle);
        free(handle);
        env->ReleaseStringUTFChars(plugin_path, path);
        env->ReleaseStringUTFChars(plugin_id, id);
        env->ReleaseStringUTFChars(plugin_version, version);
        return 0;
    }

    handle->initialized = true;
    LOGI("Plugin %s loaded successfully (handle=%p)", id, (void*)handle);

    env->ReleaseStringUTFChars(plugin_path, path);
    env->ReleaseStringUTFChars(plugin_id, id);
    env->ReleaseStringUTFChars(plugin_version, version);

    return (jlong)(intptr_t)handle;
}

JNIEXPORT jboolean JNICALL
Java_com_lumigram_messenger_plugins_NativePluginBridge_nativeCallOnLoad(
    JNIEnv *env, jclass clazz, jlong handle_ptr)
{
    PluginHandle *handle = (PluginHandle *)(intptr_t)handle_ptr;
    if (!handle || !handle->initialized) {
        LOGE("nativeCallOnLoad: invalid handle");
        return JNI_FALSE;
    }
    if (!handle->api.on_load) {
        LOGI("Plugin %s has no on_load hook, skipping", handle->plugin_id);
        return JNI_TRUE;
    }
    int result = handle->api.on_load();
    LOGI("Plugin %s on_load returned %d", handle->plugin_id, result);
    return (result == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_lumigram_messenger_plugins_NativePluginBridge_nativeCallOnUnload(
    JNIEnv *env, jclass clazz, jlong handle_ptr)
{
    PluginHandle *handle = (PluginHandle *)(intptr_t)handle_ptr;
    if (!handle || !handle->initialized) {
        LOGE("nativeCallOnUnload: invalid handle");
        return JNI_FALSE;
    }
    if (!handle->api.on_unload) {
        return JNI_TRUE;
    }
    int result = handle->api.on_unload();
    LOGI("Plugin %s on_unload returned %d", handle->plugin_id, result);
    return (result == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_lumigram_messenger_plugins_NativePluginBridge_nativeUnloadPlugin(
    JNIEnv *env, jclass clazz, jlong handle_ptr)
{
    PluginHandle *handle = (PluginHandle *)(intptr_t)handle_ptr;
    if (!handle) {
        return;
    }
    LOGI("Unloading plugin %s", handle->plugin_id);
    if (handle->dl_handle) {
        dlclose(handle->dl_handle);
    }
    free(handle);
}
