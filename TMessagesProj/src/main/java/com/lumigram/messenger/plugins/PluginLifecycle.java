package com.lumigram.messenger.plugins;

import androidx.annotation.NonNull;

public enum PluginLifecycle {
    INSTALLED("installed"),
    VALIDATING("validating"),
    LOADING("loading"),
    RUNNING("running"),
    ERROR("error"),
    DISABLED("disabled"),
    UNINSTALLED("uninstalled");

    @NonNull
    public final String value;

    PluginLifecycle(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static PluginLifecycle fromString(@NonNull String s) {
        for (PluginLifecycle state : values()) {
            if (state.value.equals(s)) {
                return state;
            }
        }
        return INSTALLED;
    }
}
