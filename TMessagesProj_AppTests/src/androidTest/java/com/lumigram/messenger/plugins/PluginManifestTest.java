package com.lumigram.messenger.plugins;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class PluginManifestTest {

    @Test
    public void testPluginTypeFromString() {
        assertEquals(PluginManifest.PluginType.PYTHON, PluginManifest.PluginType.fromString("python"));
        assertEquals(PluginManifest.PluginType.CPP, PluginManifest.PluginType.fromString("cpp"));
        assertEquals(PluginManifest.PluginType.PYTHON, PluginManifest.PluginType.fromString("unknown"));
    }

    @Test
    public void testManifestFields() {
        List<String> permissions = Arrays.asList("telegram.read_messages", "lumigram.ghost_mode");
        List<String> optionalPermissions = Collections.singletonList("sudo.root_command");
        List<String> modules = Collections.singletonList("libs/helper.py");
        List<String> requirements = Arrays.asList("cryptography", "requests");

        PluginManifest m = new PluginManifest(
                "test_plugin", "Test Plugin", "1.2.3",
                "@author", "A test plugin",
                26, "1.0.0",
                PluginManifest.PluginType.PYTHON,
                "main.py", null, "requirements.txt",
                5,
                "lumigram_official", "ecdsa-p256",
                permissions, optionalPermissions, modules, requirements
        );

        assertEquals("test_plugin", m.id);
        assertEquals("Test Plugin", m.name);
        assertEquals("1.2.3", m.version);
        assertEquals("@author", m.author);
        assertEquals("A test plugin", m.description);
        assertEquals(26, m.minSdk);
        assertEquals("1.0.0", m.minLumigramVersion);
        assertEquals(PluginManifest.PluginType.PYTHON, m.type);
        assertEquals("main.py", m.entryPoint);
        assertEquals("requirements.txt", m.requirementsFile);
        assertEquals(5, m.priority);
        assertEquals("lumigram_official", m.signer);
        assertEquals("ecdsa-p256", m.signatureAlgorithm);
        assertEquals(2, m.permissions.size());
        assertTrue(m.permissions.contains("telegram.read_messages"));
        assertEquals(1, m.optionalPermissions.size());
        assertEquals(1, m.modules.size());
        assertEquals(2, m.pythonRequirements.size());
        assertTrue(m.pythonRequirements.contains("cryptography"));
    }

    @Test
    public void testManifestDefaultPriority() {
        PluginManifest m = new PluginManifest(
                "no_priority", "No Priority", "1.0",
                null, null,
                26, "1.0.0",
                PluginManifest.PluginType.PYTHON,
                null, null, null,
                0, null, null,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );
        assertEquals(0, m.priority);
    }

    @Test
    public void testPluginTypeFromValue() {
        assertEquals("python", PluginManifest.PluginType.PYTHON.value);
        assertEquals("cpp", PluginManifest.PluginType.CPP.value);
    }

    @Test
    public void testManifestPermissionsAreUnmodifiable() {
        List<String> perms = new java.util.ArrayList<>();
        perms.add("telegram.read_messages");

        PluginManifest m = new PluginManifest(
                "p", "P", "1.0",
                null, null, 26, "1.0.0",
                PluginManifest.PluginType.PYTHON,
                null, null, null,
                0, null, null,
                perms, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        assertThrows(UnsupportedOperationException.class, () -> m.permissions.add("new_perm"));
    }
}
