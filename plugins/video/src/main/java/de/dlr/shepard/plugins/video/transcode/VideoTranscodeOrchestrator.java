package de.dlr.shepard.plugins.video.transcode;

import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import de.dlr.shepard.spi.transcode.TranscodeRequest;
import de.dlr.shepard.spi.transcode.TranscodeResult;
import de.dlr.shepard.spi.transcode.TranscodingProvider;
import de.dlr.shepard.spi.transcode.TranscodingProviderRegistry;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageLocator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — orchestrates the post-upload
 * transcode lifecycle.
 *
 * <p>Wiring:
 *
 * <ol>
 *   <li>{@link de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService#attachBytes}
 *       calls {@link #submit(VideoStreamReference)} once the source bytes
 *       have been committed to the active {@link FileStorage} adapter.
 *       The reference's {@code proxyStatus} is stamped {@code PENDING}
 *       synchronously so the wire IO reflects the in-flight state
 *       immediately.
 *   <li>This orchestrator submits a job to the bundled executor — the
 *       upload's HTTP response returns straight away (per the
 *       {@code CLAUDE.md} fire-and-forget secondary-writes rule).
 *   <li>The worker resolves the active {@link TranscodingProvider}, runs
 *       the transcode, and stamps either {@code proxyStatus=READY +
 *       proxyStorageLocator=<new locator>} or {@code proxyStatus=FAILED}.
 *   <li>The wire IO toggles {@code proxyAvailable=true} once
 *       {@code proxyStatus=READY} so the {@code VideoPlayer} can switch
 *       to the proxy URL.
 * </ol>
 *
 * <p>The executor is a daemon-thread pool sized by
 * {@code shepard.plugins.video.transcode.workers} (default 2 — keeps the
 * dev box responsive while still letting two uploads transcode in
 * parallel). For production deployments with NVENC this can be set
 * higher.
 *
 * <p>Fail-soft posture: missing provider, missing storage adapter, null
 * source locator all log WARN and leave the entity in {@code FAILED}
 * (or {@code PENDING} if the worker never fired) — the upload itself
 * is unaffected.
 */
@ApplicationScoped
public class VideoTranscodeOrchestrator {

  @Inject
  TranscodingProviderRegistry providerRegistry;

  @Inject
  FileStorageRegistry storageRegistry;

  @Inject
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.plugins.video.transcode.enabled", defaultValue = "true")
  boolean transcodeEnabled;

  @ConfigProperty(name = "shepard.plugins.video.transcode.workers", defaultValue = "2")
  int workerCount;

  @ConfigProperty(name = "shepard.plugins.video.transcode.bitrate-kbps", defaultValue = "3000")
  int bitrateKbps;

  @ConfigProperty(name = "shepard.plugins.video.transcode.max-height", defaultValue = "1080")
  int maxHeight;

  @ConfigProperty(name = "shepard.plugins.video.transcode.timeout-seconds", defaultValue = "3600")
  long timeoutSeconds;

  private volatile ExecutorService executor;

  /**
   * Lazy executor accessor so tests can swap in a synchronous executor
   * via {@link #setExecutorForTests(ExecutorService)}.
   */
  ExecutorService executor() {
    if (executor == null) {
      synchronized (this) {
        if (executor == null) {
          int workers = workerCount > 0 ? workerCount : 2;
          executor = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "shepard-video-transcode");
            t.setDaemon(true);
            return t;
          });
        }
      }
    }
    return executor;
  }

  /** Visible for testing — replace the executor with a synchronous one. */
  void setExecutorForTests(ExecutorService ex) {
    this.executor = ex;
  }

  /**
   * Fire-and-forget submit. The caller has just persisted the upload;
   * we stamp {@code PENDING} synchronously and queue the actual transcode
   * for a background worker.
   *
   * <p>Returns the (possibly updated) entity so the caller can pass it
   * through to {@code toIO()} without re-fetching.
   *
   * @param ref the just-persisted reference (with non-null
   *            {@link VideoStreamReference#getStorageLocator()})
   * @return the entity stamped with {@code proxyStatus=PENDING} (or
   *         left alone when the transcoder is disabled / unavailable)
   */
  public VideoStreamReference submit(VideoStreamReference ref) {
    if (ref == null) return null;
    if (!transcodeEnabled) {
      Log.debugf("VID-FFMPEG-TRANSCODE: disabled via shepard.plugins.video.transcode.enabled=false; skipping");
      return ref;
    }
    String locatorRaw = ref.getStorageLocator();
    if (locatorRaw == null || locatorRaw.isBlank()) {
      Log.warnf("VID-FFMPEG-TRANSCODE: ref %s has no storageLocator yet — skipping submit", ref.getAppId());
      return ref;
    }
    Optional<TranscodingProvider> providerOpt = providerRegistry.active();
    if (providerOpt.isEmpty()) {
      Log.warnf(
        "VID-FFMPEG-TRANSCODE: no provider registered for slot '%s' — leaving proxyStatus=NONE on ref %s",
        providerRegistry.activeId(), ref.getAppId()
      );
      return ref;
    }
    Optional<FileStorage> storageOpt = storageRegistry.activeStorage();
    if (storageOpt.isEmpty()) {
      Log.warnf("VID-FFMPEG-TRANSCODE: no active FileStorage; skipping transcode for ref %s", ref.getAppId());
      return ref;
    }

    // CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD: stamp PENDING on the ref object
    // in-place and persist via createOrUpdate, but do NOT store the DAO return value
    // in a VideoStreamReference local variable.  Declaring a VSR local in a CDI bean
    // method triggers ClassTransformingBuildStep.getCommonSuperClass(VSR, …) which
    // tries to load BasicReference from the narrowed transformation classloader →
    // NoClassDefFoundError.  Returning `ref` (already mutated with PENDING) is safe
    // because callers only need the proxyStatus field for the upload response IO.
    ref.setProxyStatus(TranscodeStatus.PENDING.name());
    videoStreamReferenceDAO.createOrUpdate(ref);  // persist PENDING; ignore return

    // Capture the data the worker needs as immutable scalars (no Neo4j
    // entity instance leaks across thread boundaries).
    String appId = ref.getAppId();
    String sourceLocatorRaw = ref.getStorageLocator();
    String name = ref.getName() != null ? ref.getName() : "video";
    FileStorage storage = storageOpt.get();
    TranscodingProvider provider = providerOpt.get();
    int br = bitrateKbps > 0 ? bitrateKbps : TranscodeRequest.DEFAULT_BITRATE_KBPS;
    int mh = maxHeight >= 0 ? maxHeight : TranscodeRequest.DEFAULT_MAX_HEIGHT;
    long to = timeoutSeconds > 0 ? timeoutSeconds : TranscodeRequest.DEFAULT_TIMEOUT_SECONDS;

    executor().submit(() -> runWorker(appId, sourceLocatorRaw, name, provider, storage, br, mh, to));
    return ref;
  }

  /**
   * Worker body — exposed package-private so the synchronous-executor
   * test path can step through it.
   */
  void runWorker(
    String appId,
    String sourceLocatorRaw,
    String name,
    TranscodingProvider provider,
    FileStorage storage,
    int br,
    int mh,
    long to
  ) {
    boolean activated = requestContextController.activate();
    try {
      StorageLocator sourceLocator = parseLocator(sourceLocatorRaw);
      if (sourceLocator == null) {
        Log.warnf("VID-FFMPEG-TRANSCODE: malformed sourceLocator '%s' on ref %s", sourceLocatorRaw, appId);
        stampFailed(appId, "malformed sourceLocator");
        return;
      }
      String targetFileName = proxyFileNameFor(name);
      // Use the same container the source lives in. For VID1a this is
      // VideoStreamReferenceService.VIDEO_CONTAINER on the GridFS adapter
      // or the S3 bucket on the s3 adapter — both are FileStorage-resolved.
      String targetContainer = VideoStreamReferenceService.VIDEO_CONTAINER;
      TranscodeRequest req = new TranscodeRequest(sourceLocator, targetContainer, targetFileName, br, mh, to);
      TranscodeResult result;
      try {
        result = provider.transcode(req, storage);
      } catch (Exception ex) {
        Log.warnf(ex, "VID-FFMPEG-TRANSCODE: provider '%s' threw on ref %s", provider.id(), appId);
        stampFailed(appId, "provider exception: " + ex.getMessage());
        return;
      }
      if (result.isSuccess()) {
        stampReady(appId, result.locator());
      } else {
        stampFailed(appId, result.errorMessage());
      }
    } finally {
      if (activated) requestContextController.deactivate();
    }
  }

  private void stampReady(String appId, StorageLocator proxyLocator) {
    // CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD: use stampProxy (Cypher) instead of
    // findByAppId + mutate + createOrUpdate so no VideoStreamReference local lives in
    // this CDI bean method — avoids getCommonSuperClass(VSR,…) → NoClassDefFoundError.
    videoStreamReferenceDAO.stampProxy(
      appId,
      TranscodeStatus.READY.name(),
      proxyLocator.providerId() + ":" + proxyLocator.locator()
    );
    Log.infof("VID-FFMPEG-TRANSCODE: ref %s proxy READY (%s)", appId, proxyLocator);
  }

  private void stampFailed(String appId, String reason) {
    // CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD: same Cypher-only approach.
    videoStreamReferenceDAO.stampProxy(appId, TranscodeStatus.FAILED.name(), null);
    Log.warnf("VID-FFMPEG-TRANSCODE: ref %s proxy FAILED (%s)", appId, reason);
  }

  static StorageLocator parseLocator(String raw) {
    if (raw == null) return null;
    int colon = raw.indexOf(':');
    if (colon <= 0 || colon == raw.length() - 1) return null;
    return new StorageLocator(raw.substring(0, colon), raw.substring(colon + 1));
  }

  static String proxyFileNameFor(String sourceName) {
    if (sourceName == null || sourceName.isBlank()) return "video_proxy.mp4";
    int dot = sourceName.lastIndexOf('.');
    String stem = dot > 0 ? sourceName.substring(0, dot) : sourceName;
    return stem + "_proxy.mp4";
  }
}
