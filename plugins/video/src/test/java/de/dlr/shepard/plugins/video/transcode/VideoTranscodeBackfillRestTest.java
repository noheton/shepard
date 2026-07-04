package de.dlr.shepard.plugins.video.transcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * VIDEO-HEVC-TRANSCODE-BACKFILL-2026-06-30 — unit tests for the admin
 * re-submit endpoint. The DAO + orchestrator are mocked; we verify the
 * payload shape, dry-run behaviour, and codec/limit forwarding.
 */
class VideoTranscodeBackfillRestTest {

  @Mock
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Mock
  VideoTranscodeOrchestrator orchestrator;

  VideoTranscodeBackfillRest rest;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    rest = new VideoTranscodeBackfillRest();
    rest.videoStreamReferenceDAO = videoStreamReferenceDAO;
    rest.orchestrator = orchestrator;
  }

  private VideoStreamReference candidate(String appId, String codec) {
    VideoStreamReference r = new VideoStreamReference(1L);
    r.setAppId(appId);
    r.setName("vid-" + appId);
    r.setVideoCodec(codec);
    r.setProxyStatus(null);
    r.setStorageLocator("gridfs:" + appId);
    return r;
  }

  @Test
  void backfill_noBody_queriesWithNoFilter() {
    when(videoStreamReferenceDAO.findBackfillCandidates(eq(null), eq(0))).thenReturn(List.of());

    Response resp = rest.backfill(null);

    assertThat(resp.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) resp.getEntity();
    assertThat(body.get("total")).isEqualTo(0);
    assertThat(body.get("submitted")).isEqualTo(0);
    assertThat(body.get("dryRun")).isEqualTo(false);
  }

  @Test
  void backfill_withCodecAndLimit_forwardsToDao() {
    when(videoStreamReferenceDAO.findBackfillCandidates(eq("hevc"), eq(5))).thenReturn(List.of());

    Response resp = rest.backfill(Map.of(
      "filter", Map.of("codec", "hevc"),
      "limit", 5
    ));

    assertThat(resp.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) resp.getEntity();
    assertThat(body.get("codecFilter")).isEqualTo("hevc");
  }

  @Test
  void backfill_submitsEachCandidate() {
    VideoStreamReference a = candidate("a", "hevc");
    VideoStreamReference b = candidate("b", "hevc");
    when(videoStreamReferenceDAO.findBackfillCandidates(any(), any(Integer.class))).thenReturn(List.of(a, b));
    when(orchestrator.submit(any())).thenReturn(a, b);

    Response resp = rest.backfill(Map.of("filter", Map.of("codec", "hevc")));

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) resp.getEntity();
    assertThat(body.get("submitted")).isEqualTo(2);
    assertThat(body.get("total")).isEqualTo(2);
    verify(orchestrator).submit(a);
    verify(orchestrator).submit(b);
  }

  @Test
  void backfill_dryRun_skipsSubmit() {
    VideoStreamReference a = candidate("a", "hevc");
    when(videoStreamReferenceDAO.findBackfillCandidates(any(), any(Integer.class))).thenReturn(List.of(a));

    Response resp = rest.backfill(Map.of("dryRun", true));

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) resp.getEntity();
    assertThat(body.get("submitted")).isEqualTo(0);
    assertThat(body.get("skipped")).isEqualTo(1);
    assertThat(body.get("dryRun")).isEqualTo(true);
    verify(orchestrator, never()).submit(any());
  }

  @Test
  void backfill_submitFailureCountedAsSkipped() {
    VideoStreamReference a = candidate("a", "hevc");
    when(videoStreamReferenceDAO.findBackfillCandidates(any(), any(Integer.class))).thenReturn(List.of(a));
    when(orchestrator.submit(a)).thenThrow(new RuntimeException("ffmpeg unavailable"));

    Response resp = rest.backfill(null);

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) resp.getEntity();
    assertThat(body.get("submitted")).isEqualTo(0);
    assertThat(body.get("skipped")).isEqualTo(1);
  }
}
