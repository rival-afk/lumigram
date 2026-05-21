#ifndef LUMIGRAM_PLUGIN_API_H
#define LUMIGRAM_PLUGIN_API_H

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

#define LUMIGRAM_PLUGIN_EXPORT __attribute__((visibility("default")))

LUMIGRAM_PLUGIN_EXPORT int lumigram_plugin_init(const LumigramPluginInfo *info);
LUMIGRAM_PLUGIN_EXPORT int lumigram_plugin_on_load(void);
LUMIGRAM_PLUGIN_EXPORT int lumigram_plugin_on_unload(void);
LUMIGRAM_PLUGIN_EXPORT int lumigram_plugin_on_update(const char *update_name, int account, const char *update_json);

#ifdef __cplusplus
}
#endif

#endif
