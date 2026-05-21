package com.lumigram.messenger.plugins;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class PluginLifecycleTest {

    @Test
    public void testLifecycleValues() {
        assertEquals("installed", PluginLifecycle.INSTALLED.value);
        assertEquals("validating", PluginLifecycle.VALIDATING.value);
        assertEquals("loading", PluginLifecycle.LOADING.value);
        assertEquals("running", PluginLifecycle.RUNNING.value);
        assertEquals("error", PluginLifecycle.ERROR.value);
        assertEquals("disabled", PluginLifecycle.DISABLED.value);
        assertEquals("uninstalled", PluginLifecycle.UNINSTALLED.value);
    }

    @Test
    public void testFromString() {
        assertEquals(PluginLifecycle.INSTALLED, PluginLifecycle.fromString("installed"));
        assertEquals(PluginLifecycle.RUNNING, PluginLifecycle.fromString("running"));
        assertEquals(PluginLifecycle.ERROR, PluginLifecycle.fromString("error"));
        assertEquals(PluginLifecycle.DISABLED, PluginLifecycle.fromString("disabled"));
        assertEquals(PluginLifecycle.UNINSTALLED, PluginLifecycle.fromString("uninstalled"));
    }

    @Test
    public void testFromStringReturnsDefaultForUnknown() {
        assertEquals(PluginLifecycle.INSTALLED, PluginLifecycle.fromString("bogus_state"));
    }

    @Test
    public void testAllStatesCovered() {
        PluginLifecycle[] values = PluginLifecycle.values();
        assertEquals(7, values.length);
    }
}
