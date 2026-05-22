#ifndef LUMIGRAM_CORE_H
#define LUMIGRAM_CORE_H

#include <jni.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define LUMIGRAM_PLUGIN_API_VERSION 1

typedef struct {
    int api_version;
    const char *plugin_id;
    const char *plugin_version;
} LumigramPluginInfo;

typedef int (*lumigram_plugin_init_t)(const LumigramPluginInfo *info);
typedef int (*lumigram_plugin_on_load_t)(void);
typedef int (*lumigram_plugin_on_unload_t)(void);
typedef int (*lumigram_plugin_on_update_t)(const char *update_name, int account, const char *update_json);

typedef struct {
    lumigram_plugin_init_t init;
    lumigram_plugin_on_load_t on_load;
    lumigram_plugin_on_unload_t on_unload;
    lumigram_plugin_on_update_t on_update;
} LumigramPluginApi;

JNIEXPORT jboolean Java_com_lumigram_messenger_plugins_NativePluginBridge_nativePing(
    JNIEnv *env, jclass clazz);

JNIEXPORT jlong Java_com_lumigram_messenger_plugins_NativePluginBridge_nativeLoadPlugin(
    JNIEnv *env, jclass clazz, jstring plugin_path, jstring plugin_id, jstring plugin_version);

JNIEXPORT jboolean Java_com_lumigram_messenger_plugins_NativePluginBridge_nativeCallOnLoad(
    JNIEnv *env, jclass clazz, jlong handle);

JNIEXPORT jboolean Java_com_lumigram_messenger_plugins_NativePluginBridge_nativeCallOnUnload(
    JNIEnv *env, jclass clazz, jlong handle);

JNIEXPORT void Java_com_lumigram_messenger_plugins_NativePluginBridge_nativeUnloadPlugin(
    JNIEnv *env, jclass clazz, jlong handle);

#ifdef __cplusplus
}
#endif

#endif
