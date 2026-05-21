package com.lumigram.messenger.plugins;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureVault {

    private static final SecureVault INSTANCE = new SecureVault();
    private static final String KEYSTORE_ALIAS = "lumigram_secure_vault";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private volatile boolean initialized;
    private File vaultDir;
    private SecretKey secretKey;

    private SecureVault() {
    }

    @NonNull
    public static SecureVault getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(@NonNull Context context) {
        if (initialized) return;

        vaultDir = new File(context.getFilesDir(), "lumigram/vault");
        if (!vaultDir.exists() && !vaultDir.mkdirs()) {
            FileLog.e("SecureVault: failed to create vault directory");
            return;
        }

        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                secretKey = (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
            } else {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
                );
                keyGenerator.init(new KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                secretKey = keyGenerator.generateKey();
            }

            initialized = true;
            FileLog.d("SecureVault: initialized with Android Keystore");
        } catch (Exception e) {
            FileLog.e("SecureVault: initialization failed, falling back to file-based", e);
            initializeFallback();
        }
    }

    private void initializeFallback() {
        try {
            File keyFile = new File(vaultDir, ".vault_key");
            if (keyFile.exists()) {
                byte[] encoded;
                try (FileInputStream in = new FileInputStream(keyFile)) {
                    encoded = in.readAllBytes();
                }
                javax.crypto.spec.SecretKeySpec spec = new javax.crypto.spec.SecretKeySpec(encoded, "AES");
                secretKey = spec;
            } else {
                KeyGenerator kg = KeyGenerator.getInstance("AES");
                kg.init(256);
                secretKey = kg.generateKey();
                try (FileOutputStream out = new FileOutputStream(keyFile)) {
                    out.write(secretKey.getEncoded());
                }
            }
            initialized = true;
            FileLog.d("SecureVault: initialized with fallback file-based key");
        } catch (Exception e) {
            FileLog.e("SecureVault: fallback initialization failed", e);
        }
    }

    public boolean isAvailable() {
        return initialized;
    }

    @Nullable
    public String get(@NonNull String pluginId, @NonNull String key) {
        if (!initialized) return null;

        try {
            JSONObject data = loadVaultFile(pluginId);
            return data.has(key) ? data.getString(key) : null;
        } catch (Exception e) {
            FileLog.e("SecureVault: get failed for " + pluginId + "/" + key, e);
            return null;
        }
    }

    public boolean set(@NonNull String pluginId, @NonNull String key, @NonNull String value) {
        if (!initialized) return false;

        try {
            JSONObject data = loadVaultFile(pluginId);
            data.put(key, value);
            return saveVaultFile(pluginId, data);
        } catch (Exception e) {
            FileLog.e("SecureVault: set failed for " + pluginId + "/" + key, e);
            return false;
        }
    }

    public boolean delete(@NonNull String pluginId, @NonNull String key) {
        if (!initialized) return false;

        try {
            JSONObject data = loadVaultFile(pluginId);
            if (data.has(key)) {
                data.remove(key);
                return saveVaultFile(pluginId, data);
            }
            return false;
        } catch (Exception e) {
            FileLog.e("SecureVault: delete failed for " + pluginId + "/" + key, e);
            return false;
        }
    }

    public boolean clearPluginData(@NonNull String pluginId) {
        if (!initialized) return false;

        File vaultFile = getVaultFile(pluginId);
        if (vaultFile.exists()) {
            return vaultFile.delete();
        }
        return true;
    }

    @NonNull
    private JSONObject loadVaultFile(@NonNull String pluginId) throws Exception {
        File vaultFile = getVaultFile(pluginId);
        if (!vaultFile.exists()) {
            return new JSONObject();
        }

        byte[] encrypted;
        try (FileInputStream in = new FileInputStream(vaultFile)) {
            encrypted = in.readAllBytes();
        }

        byte[] decrypted = decrypt(encrypted);
        return new JSONObject(new String(decrypted, StandardCharsets.UTF_8));
    }

    private boolean saveVaultFile(@NonNull String pluginId, @NonNull JSONObject data) throws Exception {
        File vaultFile = getVaultFile(pluginId);
        byte[] plaintext = data.toString(2).getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = encrypt(plaintext);

        try (FileOutputStream out = new FileOutputStream(vaultFile)) {
            out.write(encrypted);
        }
        return true;
    }

    @NonNull
    private byte[] encrypt(@NonNull byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] combined = new byte[IV_LENGTH + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
        System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);
        return combined;
    }

    @NonNull
    private byte[] decrypt(@NonNull byte[] encrypted) throws Exception {
        if (encrypted.length < IV_LENGTH) {
            throw new SecurityException("Invalid encrypted data");
        }

        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[encrypted.length - IV_LENGTH];
        System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encrypted, IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        return cipher.doFinal(ciphertext);
    }

    @NonNull
    private File getVaultFile(@NonNull String pluginId) {
        return new File(vaultDir, pluginId + ".vault");
    }
}
