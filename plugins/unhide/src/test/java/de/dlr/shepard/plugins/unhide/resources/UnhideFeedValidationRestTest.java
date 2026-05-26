package de.dlr.shepard.plugins.unhide.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.FeedIO;
import de.dlr.shepard.plugins.unhide.io.UnhideValidationReportIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideFeedService;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UH1e — REST-level tests for {@code ?validate=true} on
 * {@code GET /v2/unhide/feed.jsonld}.
 *
 * <p>Focuses on: auth-gate enforcement, content-type of the
 * validation response, and delegation to
 * {@link UnhideFeedService#validateFeed(FeedIO)}.
 */
class UnhideFeedValidationRestTest {

  private UnhideConfigService configService;
  private UnhideFeedService feedService;
  private UnhideFeedRest rest;

  private HttpHeaders headers;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    configService = mock(UnhideConfigService.class);
    feedService = mock(UnhideFeedService.class);
    rest = new UnhideFeedRest();
    rest.configService = configService;
    rest.feedService = feedService;

    headers = mock(HttpHeaders.class);
    uriInfo = mock(UriInfo.class);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("https://shepard.example.dlr.de/"));
  }

  // ─── validate=true still blocked by disabled toggle ─────────────────────

  @Test
  void validateTrue_returns503_whenDisabled() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(false);
    when(configService.current()).thenReturn(cfg);

    Response r = rest.feed(null, null, true, headers, uriInfo);

    assertEquals(503, r.getStatus());
    verify(feedService, never()).buildFeed(any(), anyString(), anyInt(), anyInt());
    verify(feedService, never()).validateFeed(any());
  }

  // ─── validate=true still blocked by private feed + no key ───────────────

  @Test
  void validateTrue_returns401_whenPrivate_noApiKey() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(false);
    when(configService.current()).thenReturn(cfg);
    when(headers.getHeaderString(Constants.API_KEY_HEADER)).thenReturn(null);
    when(configService.verifyHarvestKey(null)).thenReturn(false);

    Response r = rest.feed(null, null, true, headers, uriInfo);

    assertEquals(401, r.getStatus());
    verify(feedService, never()).buildFeed(any(), anyString(), anyInt(), anyInt());
    verify(feedService, never()).validateFeed(any());
  }

  // ─── validate=true returns application/json, not ld+json ────────────────

  @Test
  void validateTrue_returnsApplicationJson_notLdJson() {
    UnhideConfig cfg = enabledPublic();
    when(configService.current()).thenReturn(cfg);
    FeedIO feed = emptyFeed();
    when(feedService.buildFeed(any(), anyString(), anyInt(), anyInt())).thenReturn(feed);
    when(feedService.validateFeed(feed)).thenReturn(new UnhideValidationReportIO(true, 0, List.of()));

    Response r = rest.feed(null, null, true, headers, uriInfo);

    assertEquals(200, r.getStatus());
    String ct = r.getMediaType().toString();
    assertTrue(ct.contains(MediaType.APPLICATION_JSON),
      "validate=true must return application/json, not ld+json, got: " + ct);
  }

  // ─── validate=false (default) still returns ld+json ─────────────────────

  @Test
  void validateFalse_returnsLdJson() {
    UnhideConfig cfg = enabledPublic();
    when(configService.current()).thenReturn(cfg);
    when(feedService.buildFeed(any(), anyString(), anyInt(), anyInt())).thenReturn(emptyFeed());

    Response r = rest.feed(null, null, false, headers, uriInfo);

    assertEquals(200, r.getStatus());
    String ct = r.getMediaType().toString();
    assertTrue(ct.contains("ld+json"),
      "validate=false must return application/ld+json, got: " + ct);
    verify(feedService, never()).validateFeed(any());
  }

  // ─── validate=true returns UnhideValidationReportIO ─────────────────────

  @Test
  void validateTrue_returnsValidationReportEntity() {
    UnhideConfig cfg = enabledPublic();
    when(configService.current()).thenReturn(cfg);
    FeedIO feed = emptyFeed();
    when(feedService.buildFeed(any(), anyString(), anyInt(), anyInt())).thenReturn(feed);
    UnhideValidationReportIO expected = new UnhideValidationReportIO(true, 0, List.of());
    when(feedService.validateFeed(feed)).thenReturn(expected);

    Response r = rest.feed(null, null, true, headers, uriInfo);

    assertEquals(200, r.getStatus());
    assertInstanceOf(UnhideValidationReportIO.class, r.getEntity());
    UnhideValidationReportIO report = (UnhideValidationReportIO) r.getEntity();
    assertTrue(report.valid());
    assertEquals(0, report.errorCount());
  }

  // ─── validate=true passes page params to buildFeed ──────────────────────

  @Test
  void validateTrue_passesPaginationToFeedBuilder() {
    UnhideConfig cfg = enabledPublic();
    when(configService.current()).thenReturn(cfg);
    FeedIO feed = emptyFeed();
    when(feedService.buildFeed(any(), anyString(), anyInt(), anyInt())).thenReturn(feed);
    when(feedService.validateFeed(feed)).thenReturn(new UnhideValidationReportIO(true, 0, List.of()));

    rest.feed(3, 50, true, headers, uriInfo);

    verify(feedService).buildFeed(cfg, "https://shepard.example.dlr.de/", 3, 50);
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private static UnhideConfig enabledPublic() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(true);
    return cfg;
  }

  private static FeedIO emptyFeed() {
    return new FeedIO(FeedIO.defaultContext(), List.of(), Map.of());
  }
}
