package com.lumigram.messenger.plugins;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class PluginLogger {

    public enum Level {
        DEBUG(0, "DEBUG"),
        INFO(1, "INFO"),
        WARNING(2, "WARNING"),
        ERROR(3, "ERROR"),
        FATAL(4, "FATAL");

        public final int value;
        public final String label;

        Level(int value, String label) {
            this.value = value;
            this.label = label;
        }

        @NonNull
        public static Level fromInt(int value) {
            for (Level l : values()) {
                if (l.value == value) return l;
            }
            return INFO;
        }
    }

    public enum Category {
        PLUGIN_LOADER(0, "PLUGIN_LOADER"),
        PLUGIN_EXECUTION(1, "PLUGIN_EXECUTION"),
        PERMISSIONS(2, "PERMISSIONS"),
        UI(3, "UI"),
        NETWORK(4, "NETWORK"),
        SUDO(5, "SUDO"),
        SECURITY(6, "SECURITY"),
        GENERAL(7, "GENERAL");

        public final int value;
        public final String label;

        Category(int value, String label) {
            this.value = value;
            this.label = label;
        }

        @NonNull
        public static Category fromInt(int value) {
            for (Category c : values()) {
                if (c.value == value) return c;
            }
            return GENERAL;
        }
    }

    public static final class LogEntry {
        public final long id;
        public final long timestamp;
        @NonNull
        public final Level level;
        @NonNull
        public final Category category;
        @Nullable
        public final String pluginId;
        @NonNull
        public final String message;
        @Nullable
        public final String stackTrace;

        LogEntry(long id, long timestamp, @NonNull Level level, @NonNull Category category,
                 @Nullable String pluginId, @NonNull String message, @Nullable String stackTrace) {
            this.id = id;
            this.timestamp = timestamp;
            this.level = level;
            this.category = category;
            this.pluginId = pluginId;
            this.message = message;
            this.stackTrace = stackTrace;
        }
    }

    private static final int MAX_ENTRIES = 10000;
    private static final String DB_NAME = "lumigram_plugin_logs.db";

    private static PluginLogger instance;
    private SQLiteDatabase database;
    private volatile boolean initialized;

    private PluginLogger() {
    }

    @NonNull
    public static synchronized PluginLogger getInstance() {
        if (instance == null) {
            instance = new PluginLogger();
        }
        return instance;
    }

    public synchronized void initialize(@NonNull Context context) {
        if (initialized) return;

        try {
            File dbFile = new File(context.getFilesDir(), DB_NAME);
            database = new SQLiteDatabase(dbFile.getAbsolutePath());
            ensureTable();
            initialized = true;
            FileLog.d("PluginLogger: initialized at " + dbFile.getAbsolutePath());
        } catch (SQLiteException e) {
            FileLog.e("PluginLogger: failed to initialize", e);
        }
    }

    private void ensureTable() throws SQLiteException {
        database.executeFast("CREATE TABLE IF NOT EXISTS plugin_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "timestamp INTEGER NOT NULL," +
                "level INTEGER NOT NULL," +
                "category INTEGER NOT NULL," +
                "plugin_id TEXT," +
                "message TEXT NOT NULL," +
                "stack_trace TEXT" +
                ")").step();
    }

    public void log(@NonNull Level level, @NonNull Category category,
                    @Nullable String pluginId, @NonNull String message,
                    @Nullable Throwable throwable) {
        if (!initialized || database == null) {
            FileLog.d("PluginLogger: " + level.label + " [" + category.label + "] " +
                    (pluginId != null ? "(" + pluginId + ") " : "") + message);
            return;
        }

        try {
            database.beginTransaction();
            try {
                String stackTrace = throwable != null ? getStackTraceString(throwable) : null;
                org.telegram.SQLite.SQLitePreparedStatement stmt = database.executeFast(
                        "INSERT INTO plugin_logs (timestamp, level, category, plugin_id, message, stack_trace) " +
                        "VALUES (?, ?, ?, ?, ?, ?)");
                stmt.bindLong(1, System.currentTimeMillis());
                stmt.bindInteger(2, level.value);
                stmt.bindInteger(3, category.value);
                if (pluginId != null) {
                    stmt.bindString(4, pluginId);
                } else {
                    stmt.bindNull(4);
                }
                stmt.bindString(5, message);
                if (stackTrace != null) {
                    stmt.bindString(6, stackTrace);
                } else {
                    stmt.bindNull(6);
                }
                stmt.step();
                stmt.dispose();

                trimExcess();
            } finally {
                database.commitTransaction();
            }
        } catch (SQLiteException e) {
            FileLog.e("PluginLogger: write failed", e);
        }
    }

    public void d(@Nullable String pluginId, @NonNull String message) {
        log(Level.DEBUG, Category.GENERAL, pluginId, message, null);
    }

    public void i(@Nullable String pluginId, @NonNull String message) {
        log(Level.INFO, Category.GENERAL, pluginId, message, null);
    }

    public void w(@Nullable String pluginId, @NonNull String message) {
        log(Level.WARNING, Category.GENERAL, pluginId, message, null);
    }

    public void e(@Nullable String pluginId, @NonNull String message, @Nullable Throwable t) {
        log(Level.ERROR, Category.GENERAL, pluginId, message, t);
    }

    private void trimExcess() throws SQLiteException {
        SQLiteCursor cursor = database.queryFinalized("SELECT COUNT(*) FROM plugin_logs");
        int count = 0;
        if (cursor.next()) {
            count = cursor.intValue(0);
        }
        cursor.dispose();

        if (count > MAX_ENTRIES) {
            int toDelete = count - MAX_ENTRIES;
            org.telegram.SQLite.SQLitePreparedStatement stmt = database.executeFast(
                    "DELETE FROM plugin_logs WHERE id IN (" +
                    "SELECT id FROM plugin_logs ORDER BY id ASC LIMIT ?" +
                    ")");
            stmt.bindInteger(1, toDelete);
            stmt.step();
            stmt.dispose();
        }
    }

    @NonNull
    public List<LogEntry> getLogs(@Nullable Level minLevel, @Nullable Category category,
                                  @Nullable String pluginId, int limit, int offset) {
        List<LogEntry> result = new ArrayList<>();
        if (!initialized || database == null) return result;

        try {
            StringBuilder sql = new StringBuilder("SELECT id, timestamp, level, category, plugin_id, message, stack_trace FROM plugin_logs WHERE 1=1");
            List<Object> args = new ArrayList<>();

            if (minLevel != null) {
                sql.append(" AND level >= ?");
                args.add(minLevel.value);
            }
            if (category != null) {
                sql.append(" AND category = ?");
                args.add(category.value);
            }
            if (pluginId != null) {
                sql.append(" AND plugin_id = ?");
                args.add(pluginId);
            }

            sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
            args.add(limit);
            args.add(offset);

            SQLiteCursor cursor = database.queryFinalized(sql.toString(), args.toArray());
            while (cursor.next()) {
                result.add(new LogEntry(
                        cursor.longValue(0),
                        cursor.longValue(1),
                        Level.fromInt(cursor.intValue(2)),
                        Category.fromInt(cursor.intValue(3)),
                        cursor.stringValue(4),
                        cursor.stringValue(5),
                        cursor.stringValue(6)
                ));
            }
            cursor.dispose();
        } catch (SQLiteException e) {
            FileLog.e("PluginLogger: query failed", e);
        }
        return result;
    }

    public void clearAll() {
        if (!initialized || database == null) return;
        try {
            database.executeFast("DELETE FROM plugin_logs").step();
        } catch (SQLiteException e) {
            FileLog.e("PluginLogger: clear failed", e);
        }
    }

    public void close() {
        if (database != null) {
            database.close();
            database = null;
        }
        initialized = false;
    }

    @NonNull
    private static String getStackTraceString(@NonNull Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("\tat ").append(el.toString()).append('\n');
        }
        if (t.getCause() != null) {
            sb.append("Caused by: ");
            appendCause(sb, t.getCause());
        }
        return sb.toString();
    }

    private static void appendCause(@NonNull StringBuilder sb, @NonNull Throwable t) {
        sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("\tat ").append(el.toString()).append('\n');
        }
        if (t.getCause() != null) {
            sb.append("Caused by: ");
            appendCause(sb, t.getCause());
        }
    }

    public static String formatEntry(@NonNull LogEntry entry) {
        return "[" + entry.level.label + "][" + entry.category.label + "] " +
                (entry.pluginId != null ? "(" + entry.pluginId + ") " : "") +
                entry.message +
                (entry.stackTrace != null ? "\n" + entry.stackTrace : "");
    }
}
