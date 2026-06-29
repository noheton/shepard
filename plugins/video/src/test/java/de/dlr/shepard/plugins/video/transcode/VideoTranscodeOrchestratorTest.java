package de.dlr.shepard.plugins.video.transcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.spi.transcode.TranscodingProvider;
import de.dlr.shepard.spi.transcode.TranscodingProviderRegistry;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageLocator;
import jakarta.enterprise.context.control.RequestContextController;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — unit tests for
 * {@link VideoTranscodeOrchestrator}.
 *
 * <p>Covers the {@link VideoTranscodeOrchestrator#submit} gating logic
 * (disabled flag, missing locator, missing provider, missing storage) plus
 * the locator/filename helper functions. Avoids running the executor — the
 * worker body itself is exercised via direct method call rather than
 * through the live thread pool.
 */
class VideoTranscodeOrchestratorTest {

  private VideoTranscodeOrchestrator orch;
  private TranscodingProviderRegistry providerRegistry;
  private FileStorageRegistry storageRegistry;
  private VideoStreamReferenceDAO dao;
  private RequestContextController rcc;
  private TranscodingProvider provider;
  private FileStorage storage;

  @BeforeEach
  void setUp() {
    orch = new VideoTranscodeOrchestrator();
    providerRegistry = mock(TranscodingProviderRegistry.class);
    storageRegistry = mock(FileStorageRegistry.class);
    dao = mock(VideoStreamReferenceDAO.class);
    rcc = mock(RequestContextController.class);
    provider = mock(TranscodingProvider.class);
    storage = mock(FileStorage.class);

    orch.providerRegistry = providerRegistry;
    orch.storageRegistry = storageRegistry;
    orch.videoStreamReferenceDAO = dao;
    orch.requestContextController = rcc;
    orch.transcodeEnabled = true;
    orch.workerCount = 1;
    orch.bitrateKbps = 3000;
    orch.maxHeight = 1080;
    orch.timeoutSeconds = 3600;
    // Synchronous executor — runs jobs on the calling thread.
    orch.setExecutorForTests(java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r);
      t.setDaemon(true);
      return t;
    }));
  }

  @Test
  void submit_returns_null_for_null_ref() {
    assertThat(orch.submit(null)).isNull();
  }

  @Test
  void submit_no_op_when_transcode_disabled() {
    orch.transcodeEnabled = false;
    var ref = newRef("ref-1", "gridfs:container:oid");

    VideoStreamReference returned = orch.submit(ref);

    assertThat(returned).isSameAs(ref);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void submit_no_op_when_storageLocator_is_null() {
    var ref = newRef("ref-1", null);

    VideoStreamReference returned = orch.submit(ref);

    assertThat(returned).isSameAs(ref);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void submit_no_op_when_no_active_provider() {
    var ref = newRef("ref-1", "gridfs:container:oid");
    when(providerRegistry.active()).thenReturn(Optional.empty());
    when(providerRegistry.activeId()).thenReturn("ffmpeg-local");

    VideoStreamReference returned = orch.submit(ref);

    assertThat(returned).isSameAs(ref);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void submit_no_op_when_no_active_storage() {
    var ref = newRef("ref-1", "gridfs:container:oid");
    when(providerRegistry.active()).thenReturn(Optional.of(provider));
    when(storageRegistry.activeStorage()).thenReturn(Optional.empty());

    VideoStreamReference returned = orch.submit(ref);

    assertThat(returned).isSameAs(ref);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void submit_stamps_pending_synchronously_when_prerequisites_satisfied() {
    var ref = newRef("ref-1", "gridfs:container:oid");
    when(providerRegistry.active()).thenReturn(Optional.of(provider));
    when(storageRegistry.activeStorage()).thenReturn(Optional.of(storage));
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    VideoStreamReference returned = orch.submit(ref);

    // PENDING is stamped on the returned entity before the job runs.
    assertThat(returned.getProxyStatus()).isEqualTo("PENDING");
  }

  @Test
  void parseLocator_handles_well_formed_input() {
    StorageLocator loc = VideoTranscodeOrchestrator.parseLocator("gridfs:container:oid");
    assertThat(loc).isNotNull();
    assertThat(loc.providerId()).isEqualTo("gridfs");
    assertThat(loc.locator()).isEqualTo("container:oid");
  }

  @Test
  void parseLocator_rejects_null_blank_and_malformed() {
    assertThat(VideoTranscodeOrchestrator.parseLocator(null)).isNull();
    assertThat(VideoTranscodeOrchestrator.parseLocator("noColon")).isNull();
    assertThat(VideoTranscodeOrchestrator.parseLocator(":missingProvider")).isNull();
    assertThat(VideoTranscodeOrchestrator.parseLocator("provider:")).isNull();
  }

  @Test
  void proxyFileNameFor_strips_extension_and_appends_proxy_mp4() {
    assertThat(VideoTranscodeOrchestrator.proxyFileNameFor("clip.mp4")).isEqualTo("clip_proxy.mp4");
    assertThat(VideoTranscodeOrchestrator.proxyFileNameFor("hevc_recording.MOV")).isEqualTo("hevc_recording_proxy.mp4");
    assertThat(VideoTranscodeOrchestrator.proxyFileNameFor("Run.42.section.mkv")).isEqualTo("Run.42.section_proxy.mp4");
  }

  @Test
  void proxyFileNameFor_handles_names_without_extension() {
    assertThat(VideoTranscodeOrchestrator.proxyFileNameFor("nodot")).isEqualTo("nodot_proxy.mp4");
  }

  @Test
  void proxyFileNameFor_defaults_for_null_or_blank() {
    assertThat(VideoTranscodeOrchestrator.proxyFileNameFor(null)).isEqualTo("video_proxy.mp4");
    assertThat(VideoTranscodeOrchestrator.proxyFileNameFor("  ")).isEqualTo("video_proxy.mp4");
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private static VideoStreamReference newRef(String appId, String storageLocator) {
    var ref = new VideoStreamReference();
    ref.setAppId(appId);
    ref.setStorageLocator(storageLocator);
    ref.setName("clip.mp4");
    return ref;
  }
}
