package com.lumigram.messenger.plugins;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LumiStore {

    public static class PluginListing {
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
        @NonNull
        public final String downloadUrl;
        public final long fileSize;
        @Nullable
        public final String signer;

        PluginListing(@NonNull String id, @NonNull String name, @NonNull String version,
                      @Nullable String author, @Nullable String description,
                      @NonNull String downloadUrl, long fileSize, @Nullable String signer) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.downloadUrl = downloadUrl;
            this.fileSize = fileSize;
            this.signer = signer;
        }
    }

    public interface StoreCallback {
        void onResult(@NonNull List<PluginListing> plugins, @Nullable String error);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(@Nullable File pluginFile, @Nullable String error);
    }

    private static final LumiStore INSTANCE = new LumiStore();
    private static final String DEFAULT_STORE_URL = "https://plugins.lumigram.app/api/v1/list";

    private String storeUrl = DEFAULT_STORE_URL;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lumigram-store");
        t.setDaemon(true);
        return t;
    });

    private LumiStore() {
    }

    @NonNull
    public static LumiStore getInstance() {
        return INSTANCE;
    }

    public void setStoreUrl(@NonNull String url) {
        this.storeUrl = url;
    }

    public void fetchListings(@NonNull StoreCallback callback) {
        executor.execute(() -> {
            try {
                HttpURLConnection conn = openConnection(storeUrl);
                try {
                    int code = conn.getResponseCode();
                    if (code != 200) {
                        callback.onResult(Collections.emptyList(), "Server returned " + code);
                        return;
                    }

                    String json;
                    try (InputStream in = conn.getInputStream()) {
                        json = new String(in.readAllBytes(), "UTF-8");
                    }

                    JSONObject root = new JSONObject(json);
                    JSONArray items = root.getJSONArray("plugins");
                    List<PluginListing> listings = new ArrayList<>(items.length());

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        listings.add(new PluginListing(
                                item.getString("id"),
                                item.optString("name", item.getString("id")),
                                item.optString("version", "0.0.0"),
                                item.optString("author", null),
                                item.optString("description", null),
                                item.getString("download_url"),
                                item.optLong("file_size", 0),
                                item.optString("signer", null)
                        ));
                    }

                    callback.onResult(listings, null);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                FileLog.e("LumiStore: fetch failed", e);
                callback.onResult(Collections.emptyList(), e.getMessage());
            }
        });
    }

    public void downloadPlugin(@NonNull Context context, @NonNull PluginListing listing,
                               @NonNull DownloadCallback callback) {
        executor.execute(() -> {
            try {
                HttpURLConnection conn = openConnection(listing.downloadUrl);
                try {
                    int code = conn.getResponseCode();
                    if (code != 200) {
                        callback.onComplete(null, "Download failed: " + code);
                        return;
                    }

                    long totalSize = conn.getContentLengthLong();
                    File tmpDir = new File(context.getCacheDir(), "lumigram_store");
                    if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                        callback.onComplete(null, "Failed to create temp dir");
                        return;
                    }

                    File tmpFile = new File(tmpDir, listing.id + ".plugin");
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(tmpFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        long downloaded = 0;
                        int lastPercent = -1;

                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                            downloaded += len;
                            if (totalSize > 0) {
                                int percent = (int) (downloaded * 100 / totalSize);
                                if (percent != lastPercent) {
                                    lastPercent = percent;
                                    callback.onProgress(percent);
                                }
                            }
                        }
                    }

                    PluginManager.getInstance().installPlugin(tmpFile);
                    callback.onComplete(tmpFile, null);

                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                FileLog.e("LumiStore: download failed", e);
                callback.onComplete(null, e.getMessage());
            }
        });
    }

    @NonNull
    private static HttpURLConnection openConnection(@NonNull String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Lumigram/" + android.os.Build.VERSION.RELEASE);
        conn.connect();
        return conn;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
