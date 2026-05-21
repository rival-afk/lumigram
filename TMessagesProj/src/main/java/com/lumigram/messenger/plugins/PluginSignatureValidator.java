package com.lumigram.messenger.plugins;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PluginSignatureValidator {

    public enum SignatureAlgorithm {
        ECDSA_P256("ecdsa-p256", "SHA256withECDSA", "EC");

        @NonNull
        public final String value;
        @NonNull
        public final String jcaAlgorithm;
        @NonNull
        public final String jcaKeyType;

        SignatureAlgorithm(@NonNull String value, @NonNull String jcaAlgorithm, @NonNull String jcaKeyType) {
            this.value = value;
            this.jcaAlgorithm = jcaAlgorithm;
            this.jcaKeyType = jcaKeyType;
        }

        @NonNull
        public static SignatureAlgorithm fromString(@Nullable String s) {
            if (s != null) {
                for (SignatureAlgorithm a : values()) {
                    if (a.value.equals(s)) {
                        return a;
                    }
                }
            }
            return ECDSA_P256;
        }
    }

    private static final Map<String, PublicKey> TRUSTED_KEYS = new HashMap<>();

    static {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            byte[] keyBytes = Base64.getDecoder().decode(
                    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEOx1j1iitovJMGwqM9k8Px" +
                    "WvGfbIbH0xK3a5f3Kj0z0m7gXcQ8rY5sH0zFzFv0m7gXcQ8rY5sH0"
            );
            TRUSTED_KEYS.put("lumigram_official", keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes)));
        } catch (Exception e) {
            FileLog.e("PluginSignatureValidator: failed to load built-in key", e);
        }
    }

    private PluginSignatureValidator() {
    }

    @Nullable
    public static PublicKey parsePublicKey(@NonNull String pemData) {
        try {
            String cleaned = pemData
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(cleaned);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            FileLog.e("PluginSignatureValidator: failed to parse public key", e);
            return null;
        }
    }

    @Nullable
    public static PublicKey getTrustedKey(@NonNull String keyId) {
        PublicKey key = TRUSTED_KEYS.get(keyId);
        if (key != null) {
            return key;
        }
        FileLog.w("PluginSignatureValidator: unknown trusted key " + keyId);
        return null;
    }

    public static boolean verify(
            @NonNull byte[] data,
            @NonNull byte[] signatureBytes,
            @NonNull PublicKey publicKey,
            @NonNull SignatureAlgorithm algorithm
    ) {
        try {
            Signature sig = Signature.getInstance(algorithm.jcaAlgorithm);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            FileLog.e("PluginSignatureValidator: verification failed", e);
            return false;
        }
    }

    @NonNull
    public static VerifyResult verifyPlugin(@NonNull File pluginFile, @NonNull PluginManifest manifest) {
        String signer = manifest.signer;
        if (signer == null || signer.isEmpty()) {
            return new VerifyResult(false, "No signer in manifest");
        }

        PublicKey publicKey = getTrustedKey(signer);
        if (publicKey == null) {
            if (signer.startsWith("-----BEGIN")) {
                publicKey = parsePublicKey(signer);
            }
            if (publicKey == null) {
                return new VerifyResult(false, "Unknown signer: " + signer);
            }
        }

        SignatureAlgorithm algorithm = SignatureAlgorithm.fromString(manifest.signatureAlgorithm);

        try (ZipFile zipFile = new ZipFile(pluginFile)) {
            ZipEntry sigEntry = zipFile.getEntry("signature.sig");
            if (sigEntry == null) {
                return new VerifyResult(false, "Missing signature.sig in plugin archive");
            }

            byte[] signatureBytes;
            try (InputStream in = zipFile.getInputStream(sigEntry)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    bos.write(buf, 0, len);
                }
                signatureBytes = bos.toByteArray();
            }

            ZipEntry manifestEntry = zipFile.getEntry("manifest.json");
            if (manifestEntry == null) {
                manifestEntry = zipFile.getEntry("plugin.json");
            }
            if (manifestEntry == null) {
                manifestEntry = zipFile.getEntry("lumigram-plugin.json");
            }
            if (manifestEntry == null) {
                return new VerifyResult(false, "No manifest found for signature verification");
            }

            byte[] manifestBytes;
            try (InputStream in = zipFile.getInputStream(manifestEntry)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    bos.write(buf, 0, len);
                }
                manifestBytes = bos.toByteArray();
            }

            boolean valid = verify(manifestBytes, signatureBytes, publicKey, algorithm);
            return new VerifyResult(valid, valid ? "Signature valid" : "Signature mismatch");
        } catch (Exception e) {
            FileLog.e("PluginSignatureValidator: verifyPlugin failed", e);
            return new VerifyResult(false, "Verification error: " + e.getMessage());
        }
    }

    public static final class VerifyResult {
        public final boolean valid;
        @Nullable
        public final String message;

        VerifyResult(boolean valid, @Nullable String message) {
            this.valid = valid;
            this.message = message;
        }
    }
}
