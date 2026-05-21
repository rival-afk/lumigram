package com.lumigram.messenger.plugins;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public final class PythonRuntime {

    private static boolean chaquopyAvailable;

    static {
        try {
            Class.forName("com.chaquo.python.Python");
            chaquopyAvailable = true;
        } catch (ClassNotFoundException e) {
            chaquopyAvailable = false;
        }
    }

    private PythonRuntime() {
    }

    public static boolean isAvailable() {
        return chaquopyAvailable;
    }

    @Nullable
    public static PythonSession createSession(
            @NonNull Context context,
            @NonNull String pluginId,
            @NonNull File extractedDir,
            @NonNull String entryPoint,
            @Nullable List<String> requirements
    ) {
        if (!chaquopyAvailable) {
            FileLog.w("PythonRuntime: Chaquopy not available");
            return null;
        }

        try {
            File venvDir = new File(extractedDir.getParentFile(), pluginId + "_venv");
            if (requirements != null && !requirements.isEmpty()) {
                if (!venvDir.exists() && !venvDir.mkdirs()) {
                    FileLog.e("PythonRuntime: failed to create venv dir for " + pluginId);
                    return null;
                }
                installRequirements(context, venvDir, requirements);
            }

            Class<?> pythonClass = Class.forName("com.chaquo.python.Python");
            Object pythonInstance = pythonClass.getMethod("getInstance").invoke(null);

            Class<?> pyObjectClass = Class.forName("com.chaquo.python.PyObject");

            injectPluginPath(pythonInstance, pyObjectClass, extractedDir, venvDir);

            Object module = pythonClass
                    .getMethod("getModule", String.class)
                    .invoke(pythonInstance, entryPoint);

            return new PythonSession(module, pyObjectClass);
        } catch (Exception e) {
            FileLog.e("PythonRuntime: failed to create session for " + pluginId, e);
            return null;
        }
    }

    private static void injectPluginPath(
            @NonNull Object pythonInstance,
            @NonNull Class<?> pyObjectClass,
            @NonNull File extractedDir,
            @NonNull File venvDir
    ) throws Exception {
        Object sys = pythonInstance.getClass()
                .getMethod("getModule", String.class)
                .invoke(pythonInstance, "sys");

        Object path = sys.getClass()
                .getMethod("getAttr", String.class)
                .invoke(sys, "path");

        path.getClass()
                .getMethod("callAttr", String.class, Object[].class)
                .invoke(path, "insert", new Object[]{0, extractedDir.getAbsolutePath()});

        if (venvDir.exists()) {
            path.getClass()
                    .getMethod("callAttr", String.class, Object[].class)
                    .invoke(path, "insert", new Object[]{0, venvDir.getAbsolutePath()});
        }
    }

    private static void installRequirements(
            @NonNull Context context,
            @NonNull File venvDir,
            @NonNull List<String> requirements
    ) {
        try {
            Class<?> pythonClass = Class.forName("com.chaquo.python.Python");
            Object pythonInstance = pythonClass.getMethod("getInstance").invoke(null);

            Class<?> pyObjectClass = Class.forName("com.chaquo.python.PyObject");
            Object pip = pythonClass
                    .getMethod("getModule", String.class)
                    .invoke(pythonInstance, "pip");

            for (String req : requirements) {
                pip.getClass()
                        .getMethod("callAttr", String.class, Object[].class)
                        .invoke(pip, "main", new Object[]{
                                new String[]{"install", "--target", venvDir.getAbsolutePath(), req}
                        });
            }
        } catch (Exception e) {
            FileLog.e("PythonRuntime: pip install failed", e);
        }
    }

    public static final class PythonSession {
        private final Object module;
        private final Class<?> pyObjectClass;

        PythonSession(@NonNull Object module, @NonNull Class<?> pyObjectClass) {
            this.module = module;
            this.pyObjectClass = pyObjectClass;
        }

        @Nullable
        public Object call(@NonNull String methodName, @NonNull Object... args) {
            try {
                return module.getClass()
                        .getMethod("callAttr", String.class, Object[].class)
                        .invoke(module, methodName, args);
            } catch (Exception e) {
                FileLog.e("PythonRuntime: call " + methodName + " failed", e);
                return null;
            }
        }

        @Nullable
        public Object getAttr(@NonNull String name) {
            try {
                return module.getClass()
                        .getMethod("getAttr", String.class)
                        .invoke(module, name);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
