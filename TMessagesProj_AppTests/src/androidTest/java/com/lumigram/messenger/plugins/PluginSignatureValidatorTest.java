package com.lumigram.messenger.plugins;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class PluginSignatureValidatorTest {

    @Test
    public void testSignatureAlgorithmFromString() {
        assertEquals(
                PluginSignatureValidator.SignatureAlgorithm.ECDSA_P256,
                PluginSignatureValidator.SignatureAlgorithm.fromString("ecdsa-p256")
        );
    }

    @Test
    public void testSignatureAlgorithmFromStringDefault() {
        assertEquals(
                PluginSignatureValidator.SignatureAlgorithm.ECDSA_P256,
                PluginSignatureValidator.SignatureAlgorithm.fromString("unknown")
        );
    }

    @Test
    public void testSignatureAlgorithmFromStringNull() {
        assertEquals(
                PluginSignatureValidator.SignatureAlgorithm.ECDSA_P256,
                PluginSignatureValidator.SignatureAlgorithm.fromString(null)
        );
    }

    @Test
    public void testSignatureAlgorithmProperties() {
        assertEquals("ecdsa-p256", PluginSignatureValidator.SignatureAlgorithm.ECDSA_P256.value);
        assertEquals("SHA256withECDSA", PluginSignatureValidator.SignatureAlgorithm.ECDSA_P256.jcaAlgorithm);
        assertEquals("EC", PluginSignatureValidator.SignatureAlgorithm.ECDSA_P256.jcaKeyType);
    }

    @Test
    public void testParsePublicKeyInvalidData() {
        assertNull(PluginSignatureValidator.parsePublicKey("not a valid key"));
    }

    @Test
    public void testGetTrustedKeyUnknown() {
        assertNull(PluginSignatureValidator.getTrustedKey("nonexistent_key"));
    }

    @Test
    public void testVerifyResult() {
        PluginSignatureValidator.VerifyResult ok = new PluginSignatureValidator.VerifyResult(true, "OK");
        assertTrue(ok.valid);
        assertEquals("OK", ok.message);

        PluginSignatureValidator.VerifyResult fail = new PluginSignatureValidator.VerifyResult(false, "Failed");
        assertFalse(fail.valid);
        assertEquals("Failed", fail.message);
    }
}
