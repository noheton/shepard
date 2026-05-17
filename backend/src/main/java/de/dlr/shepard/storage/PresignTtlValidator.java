package de.dlr.shepard.storage;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * P23 — Presign-vs-cache TTL invariant enforcer.
 *
 * <p>A permission revoked in shepard takes effect within one
 * {@code shepard.permissions.cache.ttl} window (default 5 min). If a
 * presigned URL lives longer than the cache TTL, a caller whose access was
 * revoked can still complete the transfer during the overlap window — bypassing
 * the revocation entirely because S3 authenticates the URL, not shepard.
 *
 * <p>This bean caps every effective presigned TTL at the permissions cache TTL
 * and emits a {@code WARN} at startup for any configured TTL that would have
 * been capped. To allow longer presigned URLs, increase
 * {@code shepard.permissions.cache.ttl} (the cache TTL, not the presign TTL)
 * — both values move together.
 *
 * <p>Config keys (all optional, defaults mirror the FS1c / FS1g hardcoded
 * values before P23 shipped):
 * <ul>
 *   <li>{@code shepard.storage.presign.upload-ttl} (default {@code PT15M})</li>
 *   <li>{@code shepard.storage.presign.download-ttl} (default {@code PT5M})</li>
 *   <li>{@code shepard.storage.presign.export-ttl} (default {@code PT30M})</li>
 * </ul>
 */
@ApplicationScoped
public class PresignTtlValidator {

  @ConfigProperty(name = "shepard.storage.presign.upload-ttl", defaultValue = "PT15M")
  Duration configuredUploadTtl;

  @ConfigProperty(name = "shepard.storage.presign.download-ttl", defaultValue = "PT5M")
  Duration configuredDownloadTtl;

  @ConfigProperty(name = "shepard.storage.presign.export-ttl", defaultValue = "PT30M")
  Duration configuredExportTtl;

  @ConfigProperty(name = "shepard.permissions.cache.ttl", defaultValue = "PT5M")
  Duration permissionsCacheTtl;

  void onStart(@Observes StartupEvent ignored) {
    warnIfCapped("upload", configuredUploadTtl);
    warnIfCapped("download", configuredDownloadTtl);
    warnIfCapped("export", configuredExportTtl);
  }

  /** Returns the upload presigned-URL TTL, capped at the permissions cache TTL. */
  public Duration effectiveUploadTtl() {
    return cap(configuredUploadTtl);
  }

  /** Returns the download presigned-URL TTL, capped at the permissions cache TTL. */
  public Duration effectiveDownloadTtl() {
    return cap(configuredDownloadTtl);
  }

  /** Returns the RO-Crate export presigned-URL TTL, capped at the permissions cache TTL. */
  public Duration effectiveExportTtl() {
    return cap(configuredExportTtl);
  }

  Duration cap(Duration ttl) {
    return ttl.compareTo(permissionsCacheTtl) <= 0 ? ttl : permissionsCacheTtl;
  }

  private void warnIfCapped(String name, Duration configured) {
    if (configured.compareTo(permissionsCacheTtl) > 0) {
      Log.warnf(
          "P23: shepard.storage.presign.%s-ttl (%s) exceeds shepard.permissions.cache.ttl (%s). " +
          "Effective TTL capped to %s. A permission revoked while the URL is live " +
          "could be honoured for up to the cache TTL window. " +
          "To allow a longer %s TTL, raise shepard.permissions.cache.ttl to match.",
          name, configured, permissionsCacheTtl, permissionsCacheTtl, name);
    }
  }
}
