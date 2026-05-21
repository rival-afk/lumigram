package com.lumigram.messenger.plugins;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class PluginManagerCompatibilityTest {

    private PluginManifest makeManifest(int minSdk, String minVersion) {
        return new PluginManifest(
                "test", "Test", "1.0",
                null, null,
                minSdk, minVersion,
                PluginManifest.PluginType.PYTHON,
                null, null, null,
                0, null, null,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );
    }

    @Test
    public void testCompatibleMinSdkLow() {
        assertNull(PluginManager.checkPluginCompatibility(makeManifest(1, "1.0.0")));
    }

    @Test
    public void testCompatibleVersionMatch() {
        assertNull(PluginManager.checkPluginCompatibility(makeManifest(1, "12.6.4")));
    }

    @Test
    public void testCompatibleVersionOlder() {
        assertNull(PluginManager.checkPluginCompatibility(makeManifest(1, "1.0.0")));
    }

    @Test
    public void testIncompatibleMinSdkTooHigh() {
        String result = PluginManager.checkPluginCompatibility(makeManifest(9999, "1.0.0"));
        assertNotNull(result);
        assertTrue(result.contains("Requires Android API"));
    }

    @Test
    public void testNullVersion() {
        assertNull(PluginManager.checkPluginCompatibility(makeManifest(1, null)));
    }
}
