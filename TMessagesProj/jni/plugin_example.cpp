#include "lumigram_plugin_api.h"

#include <android/log.h>
#include <cstring>

#define LOG_TAG "LumigramPlugin"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char *PLUGIN_ID = nullptr;
static bool loaded = false;

extern "C" int lumigram_plugin_init(const LumigramPluginInfo *info) {
    if (!info || info->api_version != LUMIGRAM_PLUGIN_API_VERSION) {
        LOGE("API version mismatch");
        return -1;
    }
    PLUGIN_ID = info->plugin_id;
    LOGI("Plugin %s v%s initialized", info->plugin_id, info->plugin_version);
    return 0;
}

extern "C" int lumigram_plugin_on_load() {
    if (loaded) return 0;
    loaded = true;
    LOGI("Plugin %s loaded", PLUGIN_ID);
    return 0;
}

extern "C" int lumigram_plugin_on_unload() {
    if (!loaded) return 0;
    loaded = false;
    LOGI("Plugin %s unloaded", PLUGIN_ID);
    return 0;
}

extern "C" int lumigram_plugin_on_update(const char *update_name, int account, const char *update_json) {
    if (!update_name) return -1;
    LOGI("Plugin %s received update: %s (account %d)", PLUGIN_ID, update_name, account);
    return 0;
}
