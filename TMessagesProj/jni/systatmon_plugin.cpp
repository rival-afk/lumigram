#include "lumigram_plugin_api.h"

#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <cinttypes>
#include <fstream>
#include <string>
#include <sstream>

#define LOG_TAG "SysMonPlugin"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char *PLUGIN_ID = nullptr;
static const char *PLUGIN_VER = nullptr;
static bool loaded = false;

static long getFileValue(const char *path, const char *key) {
    std::ifstream f(path);
    std::string line;
    while (std::getline(f, line)) {
        if (line.find(key) == 0) {
            auto colon = line.find(':');
            if (colon != std::string::npos) {
                return std::atol(line.c_str() + colon + 1);
            }
        }
    }
    return -1;
}

static std::string getMemInfo() {
    long total = getFileValue("/proc/meminfo", "MemTotal");
    long avail = getFileValue("/proc/meminfo", "MemAvailable");
    long free = getFileValue("/proc/meminfo", "MemFree");

    if (total <= 0) return "{\"error\":\"cannot read /proc/meminfo\"}";

    char buf[256];
    std::snprintf(buf, sizeof(buf),
        "{\"total_kb\":%ld,\"avail_kb\":%ld,\"free_kb\":%ld,\"used_pct\":%.1f}",
        total, avail, free,
        100.0 * (total - avail) / total);
    return std::string(buf);
}

static std::string getCpuUsage() {
    static long prev_idle = 0, prev_total = 0;

    std::ifstream f("/proc/stat");
    std::string line;
    if (!std::getline(f, line)) return "{\"error\":\"cannot read /proc/stat\"}";

    long user, nice, sys, idle, iowait, irq, softirq, steal;
    std::sscanf(line.c_str(), "cpu %ld %ld %ld %ld %ld %ld %ld %ld",
                &user, &nice, &sys, &idle, &iowait, &irq, &softirq, &steal);

    long total = user + nice + sys + idle + iowait + irq + softirq + steal;
    long idle_all = idle + iowait;

    float pct = 0.0f;
    if (prev_total > 0) {
        long delta_total = total - prev_total;
        long delta_idle = idle_all - prev_idle;
        if (delta_total > 0) {
            pct = 100.0f * (delta_total - delta_idle) / delta_total;
        }
    }

    prev_idle = idle_all;
    prev_total = total;

    char buf[128];
    std::snprintf(buf, sizeof(buf), "{\"cpu_pct\":%.1f}", pct);
    return std::string(buf);
}

static std::string getUptime() {
    std::ifstream f("/proc/uptime");
    double uptime_sec = 0.0;
    if (f.is_open()) {
        f >> uptime_sec;
    }
    if (uptime_sec <= 0) return "{\"error\":\"cannot read /proc/uptime\"}";

    long hours = (long)(uptime_sec / 3600);
    long mins = (long)((uptime_sec - hours * 3600) / 60);
    long secs = (long)uptime_sec % 60;

    char buf[128];
    std::snprintf(buf, sizeof(buf), "{\"uptime_sec\":%.0f,\"uptime_str\":\"%02ld:%02ld:%02ld\"}",
                  uptime_sec, hours, mins, secs);
    return std::string(buf);
}

extern "C" int lumigram_plugin_init(const LumigramPluginInfo *info) {
    if (!info || info->api_version != LUMIGRAM_PLUGIN_API_VERSION) {
        LOGE("API version mismatch");
        return -1;
    }
    PLUGIN_ID = info->plugin_id;
    PLUGIN_VER = info->plugin_version;
    LOGI("SysMonPlugin %s v%s initialized", PLUGIN_ID, PLUGIN_VER);
    return 0;
}

extern "C" int lumigram_plugin_on_load() {
    if (loaded) return 0;
    loaded = true;

    std::string mem = getMemInfo();
    std::string cpu = getCpuUsage();
    std::string up = getUptime();

    LOGI("SysMonPlugin loaded: mem=%s cpu=%s uptime=%s", mem.c_str(), cpu.c_str(), up.c_str());
    return 0;
}

extern "C" int lumigram_plugin_on_unload() {
    if (!loaded) return 0;
    loaded = false;
    LOGI("SysMonPlugin %s unloaded", PLUGIN_ID);
    return 0;
}

extern "C" int lumigram_plugin_on_update(const char *update_name, int account, const char *update_json) {
    if (!update_name) return -1;
    LOGI("SysMonPlugin update: %s (account %d)", update_name, account);
    return 0;
}
