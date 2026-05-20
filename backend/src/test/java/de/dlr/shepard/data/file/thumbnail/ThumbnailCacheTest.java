package de.dlr.shepard.data.file.thumbnail;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThumbnailCacheTest {

  @TempDir
  Path tempDir;

  ThumbnailCache cache;

  @BeforeEach
  void setUp() throws Exception {
    cache = new ThumbnailCache();
    // inject cacheDir via reflection (replaces @ConfigProperty)
    Field f = ThumbnailCache.class.getDeclaredField("cacheDir");
    f.setAccessible(true);
    f.set(cache, tempDir.toString());

    Field ttlF = ThumbnailCache.class.getDeclaredField("ttlHours");
    ttlF.setAccessible(true);
    ttlF.setLong(cache, 72L);

    Field maxF = ThumbnailCache.class.getDeclaredField("maxCacheMb");
    maxF.setAccessible(true);
    maxF.setLong(cache, 2048L);
  }

  @Test
  void putAndGetReturnsSameBytes() {
    byte[] data = new byte[]{1, 2, 3, 4, 5};
    cache.put("containerA", "oid1", 200, data);
    byte[] retrieved = cache.get("containerA", "oid1", 200);
    assertNotNull(retrieved);
    assertArrayEquals(data, retrieved);
  }

  @Test
  void getMissReturnsNull() {
    assertNull(cache.get("noSuchContainer", "noSuchOid", 200));
  }

  @Test
  void evictRemovesAllSizeVariants() {
    byte[] data = new byte[]{9, 8, 7};
    cache.put("c1", "oid2", 64, data);
    cache.put("c1", "oid2", 200, data);
    cache.put("c1", "oid2", 400, data);

    cache.evict("c1", "oid2");

    assertNull(cache.get("c1", "oid2", 64));
    assertNull(cache.get("c1", "oid2", 200));
    assertNull(cache.get("c1", "oid2", 400));
  }

  @Test
  void evictContainerRemovesDirectory() {
    byte[] data = new byte[]{1};
    cache.put("myContainer", "oid3", 200, data);
    cache.evictContainer("myContainer");

    assertNull(cache.get("myContainer", "oid3", 200));
    // directory should no longer exist
    assert !Files.exists(tempDir.resolve("myContainer"));
  }

  @Test
  void putDifferentContainersAreIsolated() {
    byte[] a = new byte[]{0x0A};
    byte[] b = new byte[]{0x0B};
    cache.put("cA", "shared-oid", 200, a);
    cache.put("cB", "shared-oid", 200, b);

    assertArrayEquals(a, cache.get("cA", "shared-oid", 200));
    assertArrayEquals(b, cache.get("cB", "shared-oid", 200));
  }

  @Test
  void expiredEntryIsNotReturned() throws Exception {
    byte[] data = new byte[]{1, 2, 3};
    cache.put("c2", "oid4", 200, data);

    // backdate the file's modification time to beyond TTL
    Path p = tempDir.resolve("c2").resolve("oid4-200.png");
    Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.from(
      java.time.Instant.now().minus(java.time.Duration.ofHours(73))));

    assertNull(cache.get("c2", "oid4", 200));
  }

  @Test
  void sanitizeHandlesSpecialCharacters() throws IOException {
    // container appId with slash-like characters should not escape the temp dir
    byte[] data = new byte[]{5};
    cache.put("safe_id-123", "oid/with/slashes", 64, data);
    // should not throw and should be retrievable
    byte[] result = cache.get("safe_id-123", "oid/with/slashes", 64);
    assertNotNull(result);
    assertArrayEquals(data, result);
    // verify no escape: file must be inside tempDir
    assert Files.walk(tempDir).anyMatch(p -> p.toString().endsWith(".png"));
  }
}
