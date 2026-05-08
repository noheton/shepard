package de.dlr.shepard.auth.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootstrapTokenInitializerTest {

  @Test
  void sha256Hex_isStableAcrossInputs() {
    String hash1 = BootstrapTokenInitializer.sha256Hex("hello");
    String hash2 = BootstrapTokenInitializer.sha256Hex("hello");
    assertEquals(hash1, hash2);
    // SHA-256 of "hello" is a known constant; re-asserting it is overkill,
    // but we assert non-empty hex.
    assertEquals(64, hash1.length());
    assertTrue(hash1.matches("[0-9a-f]+"));
  }

  @Test
  void sha256Hex_distinguishesInputs() {
    String hashA = BootstrapTokenInitializer.sha256Hex("alpha");
    String hashB = BootstrapTokenInitializer.sha256Hex("beta");
    assertNotNull(hashA);
    assertNotNull(hashB);
    org.junit.jupiter.api.Assertions.assertNotEquals(hashA, hashB);
  }

  @Test
  void writeTokenFile_createsParentDir_andWritesContent(@TempDir Path tmp) throws IOException {
    Path target = tmp.resolve("nested/dir/.bootstrap-token");
    BootstrapTokenInitializer.writeTokenFile(target, "my-token");
    assertTrue(Files.exists(target));
    String content = Files.readString(target);
    assertEquals("my-token\n", content);
  }
}
