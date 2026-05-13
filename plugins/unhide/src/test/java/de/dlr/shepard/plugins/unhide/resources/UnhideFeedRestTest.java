package de.dlr.shepard.plugins.unhide.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.FeedIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideFeedService;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnhideFeedRestTest {

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

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesPermitAll() {
    PermitAll gate = UnhideFeedRest.class.getAnnotation(PermitAll.class);
    assertNotNull(gate,
      "UnhideFeedRest is @PermitAll because the access model is runtime-mutable; " +
      "auth is enforced inside the resource against :UnhideConfig.");
  }

  @Test
  void pathIsV2() {
    Path p = UnhideFeedRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/unhide", p.value());
  }

  // ─── disabled-toggle path ────────────────────────────────────────────────

  @Test
  void feed_returns503_whenDisabled() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(false);
    when(configService.current()).thenReturn(cfg);

    Response r = rest.feed(null, null, headers, uriInfo);

    assertEquals(503, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertEquals(UnhideFeedRest.PROBLEM_TYPE_FEED_DISABLED, p.type());
    verify(feedService, never()).buildFeed(any(), anyString(), anyInt(), anyInt());
  }

  // ─── public-feed path ────────────────────────────────────────────────────

  @Test
  void feed_returns200_whenPublic_noApiKey() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(true);
    when(configService.current()).thenReturn(cfg);
    when(feedService.buildFeed(any(), anyString(), anyInt(), anyInt()))
      .thenReturn(new FeedIO(FeedIO.defaultContext(), List.of(), Map.of()));

    Response r = rest.feed(null, null, headers, uriInfo);

    assertEquals(200, r.getStatus());
    assertTrue(r.getMediaType().toString().contains("ld+json"));
  }

  // ─── private-feed paths ──────────────────────────────────────────────────

  @Test
  void feed_returns401_whenPrivate_noApiKey() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(false);
    when(configService.current()).thenReturn(cfg);
    when(headers.getHeaderString(Constants.API_KEY_HEADER)).thenReturn(null);
    when(configService.verifyHarvestKey(null)).thenReturn(false);

    Response r = rest.feed(null, null, headers, uriInfo);

    assertEquals(401, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertEquals(UnhideFeedRest.PROBLEM_TYPE_HARVEST_KEY_ABSENT, p.type());
    verify(feedService, never()).buildFeed(any(), anyString(), anyInt(), anyInt());
  }

  @Test
  void feed_returns401_whenPrivate_wrongApiKey() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(false);
    when(configService.current()).thenReturn(cfg);
    when(headers.getHeaderString(Constants.API_KEY_HEADER)).thenReturn("wrong-key");
    when(configService.verifyHarvestKey("wrong-key")).thenReturn(false);

    Response r = rest.feed(null, null, headers, uriInfo);

    assertEquals(401, r.getStatus());
    verify(feedService, never()).buildFeed(any(), anyString(), anyInt(), anyInt());
  }

  @Test
  void feed_returns200_whenPrivate_correctApiKey() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(false);
    when(configService.current()).thenReturn(cfg);
    when(headers.getHeaderString(Constants.API_KEY_HEADER)).thenReturn("correct-key");
    when(configService.verifyHarvestKey("correct-key")).thenReturn(true);
    when(feedService.buildFeed(any(), anyString(), anyInt(), anyInt()))
      .thenReturn(new FeedIO(FeedIO.defaultContext(), List.of(), Map.of()));

    Response r = rest.feed(null, null, headers, uriInfo);

    assertEquals(200, r.getStatus());
    verify(feedService).buildFeed(any(), anyString(), anyInt(), anyInt());
  }

  @Test
  void feed_passesPaginationQueryParams() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(true);
    when(configService.current()).thenReturn(cfg);
    when(feedService.buildFeed(any(), anyString(), anyInt(), anyInt()))
      .thenReturn(new FeedIO(FeedIO.defaultContext(), List.of(), Map.of()));

    rest.feed(2, 50, headers, uriInfo);

    verify(feedService).buildFeed(cfg, "https://shepard.example.dlr.de/", 2, 50);
  }

  @Test
  void feed_defaultsToPageZero_defaultPageSize_whenAbsent() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(true);
    when(configService.current()).thenReturn(cfg);
    when(feedService.buildFeed(any(), anyString(), anyInt(), anyInt()))
      .thenReturn(new FeedIO(FeedIO.defaultContext(), List.of(), Map.of()));

    rest.feed(null, null, headers, uriInfo);

    verify(feedService).buildFeed(cfg, "https://shepard.example.dlr.de/", 0, UnhideFeedService.DEFAULT_PAGE_SIZE);
  }
}
