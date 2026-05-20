package de.dlr.shepard.data.file.thumbnail;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * TH1a — filesystem thumbnail cache.
 *
 * <p>Layout: {@code {cache-dir}/{containerAppId}/{oid}-{sizePx}.png}
 *
 * <p>TTL: entries are considered expired when their {@code lastModified}
 * timestamp is older than {@code shepard.thumbnail.ttl-hours}. Expired
 * entries are regenerated on the next request (lazy) and also swept
 * nightly by {@link #runNightlySweep()}.
 *
 * <p>Orphan eviction: {@link FileContainerService} calls
 * {@link #evict(String, String)} on file delete and
 * {@link #evictContainer(String)} on container delete.
 */
@ApplicationScoped
public class ThumbnailCache {

  @ConfigProperty(name = "shepard.thumbnail.cache-dir", defaultValue = "/var/lib/shepard/thumbnail-cache")
  String cacheDir;

  @ConfigProperty(name = "shepard.thumbnail.ttl-hours", defaultValue = "72")
  long ttlHours;

  @ConfigProperty(name = "shepard.thumbnail.max-cache-mb", defaultValue = "2048")
  long maxCacheMb;

  Path cacheRoot() {
    return Path.of(cacheDir);
  }

  Path entryPath(String containerAppId, String oid, int sizePx) {
    return cacheRoot().resolve(sanitize(containerAppId)).resolve(sanitize(oid) + "-" + sizePx + ".png");
  }

  /** Returns cached PNG bytes if present and not expired; null otherwise. */
  public byte[] get(String containerAppId, String oid, int sizePx) {
    Path p = entryPath(containerAppId, oid, sizePx);
    if (!Files.exists(p)) return null;
    try {
      BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
      Instant modified = attrs.lastModifiedTime().toInstant();
      if (modified.isBefore(Instant.now().minus(Duration.ofHours(ttlHours)))) {
        Files.deleteIfExists(p);
        return null;
      }
      return Files.readAllBytes(p);
    } catch (IOException e) {
      Log.warnf("ThumbnailCache.get failed for %s/%s@%d: %s", containerAppId, oid, sizePx, e.getMessage());
      return null;
    }
  }

  /** Write PNG bytes into the cache. Silently skips on I/O errors. */
  public void put(String containerAppId, String oid, int sizePx, byte[] pngBytes) {
    Path p = entryPath(containerAppId, oid, sizePx);
    try {
      Files.createDirectories(p.getParent());
      Files.write(p, pngBytes);
    } catch (IOException e) {
      Log.warnf("ThumbnailCache.put failed for %s/%s@%d: %s", containerAppId, oid, sizePx, e.getMessage());
    }
  }

  /** Remove all size variants for a single file (called on file delete). */
  public void evict(String containerAppId, String oid) {
    Path dir = cacheRoot().resolve(sanitize(containerAppId));
    String prefix = sanitize(oid) + "-";
    if (!Files.exists(dir)) return;
    try (var stream = Files.list(dir)) {
      stream
        .filter(p -> p.getFileName().toString().startsWith(prefix))
        .forEach(p -> {
          try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        });
    } catch (IOException e) {
      Log.warnf("ThumbnailCache.evict failed for %s/%s: %s", containerAppId, oid, e.getMessage());
    }
  }

  /** Remove the entire container directory (called on container delete). */
  public void evictContainer(String containerAppId) {
    Path dir = cacheRoot().resolve(sanitize(containerAppId));
    if (!Files.exists(dir)) return;
    try (var stream = Files.walk(dir)) {
      stream.sorted(Comparator.reverseOrder()).forEach(p -> {
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
      });
    } catch (IOException e) {
      Log.warnf("ThumbnailCache.evictContainer failed for %s: %s", containerAppId, e.getMessage());
    }
  }

  /** Nightly sweep: remove expired entries, then cap total size to max-cache-mb (LRU). */
  @Scheduled(cron = "0 17 2 * * ?")
  public void runNightlySweep() {
    Path root = cacheRoot();
    if (!Files.exists(root)) return;

    Instant cutoff = Instant.now().minus(Duration.ofHours(ttlHours));
    AtomicLong totalBytes = new AtomicLong(0);

    try (var stream = Files.walk(root)) {
      stream.filter(Files::isRegularFile).forEach(p -> {
        try {
          BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
          if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
            Files.deleteIfExists(p);
          } else {
            totalBytes.addAndGet(attrs.size());
          }
        } catch (IOException ignored) {}
      });
    } catch (IOException e) {
      Log.warnf("ThumbnailCache sweep walk failed: %s", e.getMessage());
      return;
    }

    long maxBytes = maxCacheMb * 1024L * 1024L;
    if (totalBytes.get() > maxBytes) {
      evictLruUntilUnderCap(root, maxBytes);
    }

    Log.debugf("ThumbnailCache nightly sweep done; retained ~%d MB", totalBytes.get() / 1024 / 1024);
  }

  private void evictLruUntilUnderCap(Path root, long maxBytes) {
    record Entry(Path path, Instant lastModified, long size) {}
    List<Entry> entries = new java.util.ArrayList<>();
    try (var stream = Files.walk(root)) {
      stream.filter(Files::isRegularFile).forEach(p -> {
        try {
          BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
          entries.add(new Entry(p, a.lastModifiedTime().toInstant(), a.size()));
        } catch (IOException ignored) {}
      });
    } catch (IOException e) {
      return;
    }
    entries.sort(Comparator.comparing(Entry::lastModified));
    long total = entries.stream().mapToLong(Entry::size).sum();
    for (Entry e : entries) {
      if (total <= maxBytes) break;
      try { Files.deleteIfExists(e.path()); total -= e.size(); } catch (IOException ignored) {}
    }
  }

  /** Strip path-traversal characters from cache key segments. */
  private static String sanitize(String s) {
    return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
  }
}
