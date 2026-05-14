package de.dlr.shepard.storage;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * FS1a registry — discovers every {@link FileStorage} CDI bean and
 * resolves which one is "active" via the deploy-time
 * {@code shepard.storage.provider} key.
 *
 * <p>Designed in {@code aidocs/45 §3.2}. Mirror of
 * {@link de.dlr.shepard.publish.minter.MinterRegistry} (post-KIP1h
 * optional posture): an in-tree CDI registry that walks the
 * discovered beans and surfaces the matching one via a
 * deploy-time-pinned id.
 *
 * <p><strong>Optional posture.</strong> The registry degrades
 * cleanly when no provider matches the configured id:
 *
 * <ul>
 *   <li><strong>Configured id matches an enabled bean</strong> →
 *       that storage is active, {@link #activeStorage()} returns it
 *       wrapped in an {@code Optional}.</li>
 *   <li><strong>Configured id unset (or {@code none})</strong> →
 *       no active storage. WARN logged at startup; the file-payload
 *       endpoints return 503 {@code storage.provider.not-installed}.</li>
 *   <li><strong>Configured id set but no matching bean</strong> →
 *       no active storage. WARN logged at startup with the
 *       operator-actionable hint ("install
 *       shepard-plugin-file-{id} or set shepard.storage.provider to
 *       a registered id"). File endpoints return the same 503.
 *       <em>Not</em> fail-fast — an operator who's mid-swap (FS1e
 *       migration in flight) shouldn't lose their entire backend on
 *       a stale config typo; the 503 envelope is the safety net.</li>
 *   <li><strong>Configured id matches a disabled bean</strong> →
 *       no active storage. WARN logged. Same 503 path.</li>
 * </ul>
 *
 * <p>Duplicate ids ({@code two beans return id()="gridfs"}) log a
 * WARN and resolve to the first bean the iterator surfaces — that's
 * defensive rather than fail-fast because the plugin-first shape
 * means an operator running with both a stock and a forked GridFS
 * adapter should still boot (with the warning); the first-wins rule
 * matches {@code MinterRegistry}'s behaviour.
 *
 * <p>{@link #list()} surfaces every discovered adapter for the
 * future {@code GET /v2/admin/storage} (FS1d) and for the
 * {@code shepard-admin storage status} CLI command (L1).
 */
@ApplicationScoped
public class FileStorageRegistry {

  /**
   * Sentinel value an operator can set to explicitly disable the
   * file-payload endpoints without uninstalling any adapter.
   * Treated as "no active storage" identically to the unset / empty
   * case.
   */
  public static final String NONE = "none";

  /**
   * Deploy-time key. Default {@code "gridfs"} matches the in-core
   * {@code GridFsFileStorage} id; {@code "s3"} (FS1b) arrives with
   * the {@code shepard-plugin-file-s3} Maven module. Set to
   * {@code "none"} (or leave blank) to disable file payloads
   * without uninstalling adapters.
   *
   * <p>Documented as deploy-time-only per {@code CLAUDE.md}
   * "cluster identity / topology" exception — switching the active
   * storage backend re-points the bytes pipeline, which is not
   * safely runtime-flippable (it would orphan in-flight writes and
   * break every existing-row read until the FS1e migration sweep
   * completes).
   */
  @Inject
  @ConfigProperty(name = "shepard.storage.provider", defaultValue = "gridfs")
  String configuredProviderId;

  @Inject
  Instance<FileStorage> storages;

  private volatile FileStorage activeStorage;
  private volatile Map<String, FileStorage> byId = Map.of();

  /** Constructor for CDI. */
  public FileStorageRegistry() {}

  /** Visible for testing. */
  FileStorageRegistry(String configuredProviderId, Instance<FileStorage> storages) {
    this.configuredProviderId = configuredProviderId;
    this.storages = storages;
    resolve();
  }

  /**
   * Quarkus startup hook — resolves the active storage adapter once
   * all CDI beans are constructed.
   *
   * <p>This method <strong>never throws</strong>: missing /
   * disabled providers degrade to "no active storage" + a WARN,
   * and the file-payload endpoints emit 503
   * {@code storage.provider.not-installed} on demand.
   */
  void onStartup(@Observes StartupEvent ev) {
    resolve();
  }

  /**
   * Idempotent resolve. Builds the by-id map, picks the configured
   * adapter or leaves {@link #activeStorage} null if the install is
   * storage-less.
   */
  void resolve() {
    Map<String, FileStorage> map = new LinkedHashMap<>();
    List<FileStorage> discovered = new ArrayList<>();
    if (storages != null) {
      for (FileStorage s : storages) {
        discovered.add(s);
        if (s == null) continue;
        String id = s.id();
        if (id == null || id.isBlank()) {
          Log.warnf("FileStorageRegistry: skipping %s — id() returned null/blank", s.getClass().getName());
          continue;
        }
        FileStorage prior = map.putIfAbsent(id, s);
        if (prior != null) {
          Log.warnf(
            "FileStorageRegistry: duplicate FileStorage id '%s' — keeping %s, ignoring %s",
            id,
            prior.getClass().getName(),
            s.getClass().getName()
          );
        }
      }
    }
    this.byId = Map.copyOf(map);

    String available = byId.isEmpty() ? "<none>" : String.join(", ", byId.keySet());
    Log.infof("FileStorageRegistry: discovered %d storage adapter(s): [%s]", byId.size(), available);

    String want = configuredProviderId == null ? "" : configuredProviderId.trim();

    if (want.isEmpty() || NONE.equalsIgnoreCase(want)) {
      this.activeStorage = null;
      Log.warnf(
        "FileStorageRegistry: shepard.storage.provider is unset (or 'none') — file-payload endpoints will return 503 " +
        "until shepard.storage.provider is set + the matching adapter is installed."
      );
      return;
    }

    FileStorage picked = byId.get(want);
    if (picked == null) {
      this.activeStorage = null;
      Log.warnf(
        "FileStorageRegistry: shepard.storage.provider=%s — no matching FileStorage bean on the classpath. " +
        "Available: %s. Install shepard-plugin-file-%s (or another storage adapter and " +
        "set shepard.storage.provider to its id). File-payload endpoints will return 503.",
        want,
        available,
        want
      );
      return;
    }
    if (!picked.isEnabled()) {
      this.activeStorage = null;
      Log.warnf(
        "FileStorageRegistry: shepard.storage.provider=%s is registered but reports isEnabled()=false. " +
        "Check its configuration. File-payload endpoints will return 503.",
        want
      );
      return;
    }
    this.activeStorage = picked;
    Log.infof(
      "FileStorageRegistry: active storage '%s' (%s); %d adapter(s) discovered.",
      picked.id(),
      picked.getClass().getName(),
      discovered.size()
    );
  }

  /**
   * @return the currently-active storage adapter, or
   *         {@code Optional.empty()} when the install has no
   *         provider wired (unset config, missing adapter, or
   *         disabled bean). Callers in the file-payload path
   *         translate empty into a 503 RFC 7807 problem response
   *         via {@link StorageNotInstalledException}.
   */
  public Optional<FileStorage> activeStorage() {
    return Optional.ofNullable(activeStorage);
  }

  /**
   * @return the configured provider id (verbatim, post-trim), or
   *         {@code "<unset>"} when no provider is active.
   *         Diagnostic / log surface; the {@link #activeStorage()}
   *         optional is the authoritative readiness signal.
   */
  public String activeStorageId() {
    return activeStorage == null ? "<unset>" : activeStorage.id();
  }

  /**
   * @return all discovered adapters, ordered by discovery (CDI
   *         iterator order). Empty when no adapters are on the
   *         classpath. Surface for the future
   *         {@code GET /v2/admin/storage} (FS1d) listing endpoint
   *         and for the {@code shepard-admin storage status} CLI
   *         command.
   */
  public List<FileStorage> list() {
    return List.copyOf(byId.values());
  }

  /**
   * @return the {@link #activeStorage()} adapter or throw
   *         {@link StorageNotInstalledException} with an
   *         operator-actionable hint. Convenience for the
   *         file-payload code path that maps the exception to RFC
   *         7807 {@code storage.provider.not-installed} (503).
   */
  public FileStorage requireActive() {
    FileStorage active = activeStorage;
    if (active != null) return active;
    throw new StorageNotInstalledException(
      "No file-payload storage adapter is active. Set shepard.storage.provider to one of " +
      "the discovered adapters [" +
      (byId.isEmpty() ? "<none>" : String.join(", ", byId.keySet())) +
      "] (default 'gridfs' ships in core; FS1b adds 'shepard-plugin-file-s3' under plugins/storage-s3/), " +
      "or install the matching adapter JAR."
    );
  }
}
