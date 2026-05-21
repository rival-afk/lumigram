package com.lumigram.messenger.plugins;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PluginManifest {

    @NonNull
    public final String id;
    @NonNull
    public final String name;
    @NonNull
    public final String version;

    @Nullable
    public final String author;
    @Nullable
    public final String description;

    public final int minSdk;
    @NonNull
    public final String minLumigramVersion;

    @NonNull
    public final PluginType type;

    @Nullable
    public final String entryPoint;
    @Nullable
    public final String cppLibrary;
    @Nullable
    public final String requirementsFile;

    public final int priority;

    @Nullable
    public final String signer;

    @Nullable
    public final String signatureAlgorithm;

    @NonNull
    public final List<String> permissions;
    @NonNull
    public final List<String> optionalPermissions;
    @NonNull
    public final List<String> modules;
    @NonNull
    public final List<String> pythonRequirements;

    public PluginManifest(
            @NonNull String id,
            @NonNull String name,
            @NonNull String version,
            @Nullable String author,
            @Nullable String description,
            int minSdk,
            @NonNull String minLumigramVersion,
            @NonNull PluginType type,
            @Nullable String entryPoint,
            @Nullable String cppLibrary,
            @Nullable String requirementsFile,
            int priority,
            @Nullable String signer,
            @Nullable String signatureAlgorithm,
            @NonNull List<String> permissions,
            @NonNull List<String> optionalPermissions,
            @NonNull List<String> modules,
            @NonNull List<String> pythonRequirements
    ) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.minSdk = minSdk;
        this.minLumigramVersion = minLumigramVersion;
        this.type = type;
        this.entryPoint = entryPoint;
        this.cppLibrary = cppLibrary;
        this.requirementsFile = requirementsFile;
        this.priority = priority;
        this.signer = signer;
        this.signatureAlgorithm = signatureAlgorithm;
        this.permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
        this.optionalPermissions = Collections.unmodifiableList(new ArrayList<>(optionalPermissions));
        this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
        this.pythonRequirements = Collections.unmodifiableList(new ArrayList<>(pythonRequirements));
    }

    public enum PluginType {
        PYTHON("python"),
        CPP("cpp");

        @NonNull
        public final String value;

        PluginType(@NonNull String value) {
            this.value = value;
        }

        @NonNull
        public static PluginType fromString(@NonNull String s) {
            for (PluginType t : values()) {
                if (t.value.equals(s)) {
                    return t;
                }
            }
            return PYTHON;
        }
    }
}
