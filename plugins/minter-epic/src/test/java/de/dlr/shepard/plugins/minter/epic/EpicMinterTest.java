package de.dlr.shepard.plugins.minter.epic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient.EpicHttpResponse;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService;
import de.dlr.shepard.publish.minter.MintRequest;
import de.dlr.shepard.publish.minter.MintResult;
import de.dlr.shepard.publish.minter.MinterException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * KIP1c — EpicMinter unit tests. Mocks {@link EpicHttpClient}
 * and {@link EpicMinterConfigService} so we can exercise the
 * request body builder, the retry hook, and the enabled/disabled
 * guard in isolation.
 */
class EpicMinterTest {

  private EpicMinterConfigService configService;
  private EpicHttpClient http;
  private EpicMinter minter;

  @BeforeEach
  void setUp() {
    configService = mock(EpicMinterConfigService.class);
    http = mock(EpicHttpClient.class);
    minter = new EpicMinter();
    minter.configService = configService;
    minter.http = http;
  }

  private EpicMinterConfig fullyConfiguredCfg() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    cfg.setEnabled(true);
    cfg.setApiBaseUrl("https://handle.argo.grnet.gr/api");
    cfg.setHandlePrefix("21.T11148");
    cfg.setCredentialKey("gcm1:fake-cipher");
    cfg.setCredentialHash("a".repeat(64));
    return cfg;
  }

  // ─── id / isEnabled ─────────────────────────────────────────────────────

  @Test
  void id_isStable() {
    assertThat(minter.id()).isEqualTo("epic");
    assertThat(EpicMinter.ID).isEqualTo("epic");
  }

  @Test
  void isEnabled_falseWhenDisabledOrMissingFields() {
    EpicMinterConfig disabled = fullyConfiguredCfg();
    disabled.setEnabled(false);
    when(configService.current()).thenReturn(disabled);
    assertThat(minter.isEnabled()).isFalse();

    EpicMinterConfig noPrefix = fullyConfiguredCfg();
    noPrefix.setHandlePrefix(null);
    when(configService.current()).thenReturn(noPrefix);
    assertThat(minter.isEnabled()).isFalse();

    EpicMinterConfig noCred = fullyConfiguredCfg();
    noCred.setCredentialKey(null);
    when(configService.current()).thenReturn(noCred);
    assertThat(minter.isEnabled()).isFalse();

    EpicMinterConfig noUrl = fullyConfiguredCfg();
    noUrl.setApiBaseUrl(null);
    when(configService.current()).thenReturn(noUrl);
    assertThat(minter.isEnabled()).isFalse();
  }

  @Test
  void isEnabled_trueWhenFullyConfigured() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    assertThat(minter.isEnabled()).isTrue();
  }

  @Test
  void isEnabled_falseOnRuntimeException() {
    when(configService.current()).thenThrow(new RuntimeException("boom"));
    assertThat(minter.isEnabled()).isFalse();
  }

  // ─── mint happy path ────────────────────────────────────────────────────

  @Test
  void mint_returnsHandleUrl() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:secret");
    when(http.put(anyString(), anyString(), anyString())).thenReturn(new EpicHttpResponse(201, "{}"));

    MintRequest req = new MintRequest(
      "data-objects",
      "01950000-aaaa-bbbb-cccc-000000000001",
      "https://shepard.example.org/v2/data-objects/01950000-aaaa-bbbb-cccc-000000000001",
      1,
      Map.of("name", "Test dataset")
    );

    MintResult result = minter.mint(req);

    assertThat(result.pid()).startsWith("https://hdl.handle.net/21.T11148/");
    assertThat(result.minterId()).isEqualTo("epic");
    assertThat(result.mintedAt()).isNotNull();
  }

  @Test
  void mint_putsToCorrectEpicApiUrl() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:secret");
    when(http.put(anyString(), anyString(), anyString())).thenReturn(new EpicHttpResponse(200, "{}"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    minter.mint(req);

    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(http).put(urlCaptor.capture(), anyString(), anyString());
    assertThat(urlCaptor.getValue()).startsWith("https://handle.argo.grnet.gr/api/handles/21.T11148/");
  }

  @Test
  void mint_throwsWhenDisabled() {
    EpicMinterConfig cfg = fullyConfiguredCfg();
    cfg.setEnabled(false);
    when(configService.current()).thenReturn(cfg);

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("disabled");

    verify(http, never()).put(anyString(), anyString(), anyString());
  }

  @Test
  void mint_throwsWhenHandlePrefixUnset() {
    EpicMinterConfig cfg = fullyConfiguredCfg();
    cfg.setHandlePrefix(null);
    when(configService.current()).thenReturn(cfg);

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("handlePrefix");
  }

  @Test
  void mint_throwsOnNon200Or201Response() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:pwd");
    when(http.put(anyString(), anyString(), anyString()))
      .thenReturn(new EpicHttpResponse(403, "{\"error\":\"forbidden\"}"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("HTTP 403");
  }

  @Test
  void mint_accepts200AsSuccess() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:secret");
    when(http.put(anyString(), anyString(), anyString())).thenReturn(new EpicHttpResponse(200, "{}"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    MintResult result = minter.mint(req);
    assertThat(result.pid()).startsWith("https://hdl.handle.net/");
  }

  // ─── retry behaviour ────────────────────────────────────────────────────

  @Test
  void mint_retriesOnce_on5xx() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:pwd");
    when(http.put(anyString(), anyString(), anyString()))
      .thenReturn(new EpicHttpResponse(503, "service unavailable"))
      .thenReturn(new EpicHttpResponse(201, "{}"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    MintResult result = minter.mint(req);

    assertThat(result.pid()).startsWith("https://hdl.handle.net/");
    verify(http, times(2)).put(anyString(), anyString(), anyString());
  }

  @Test
  void mint_retriesOnNetworkExceptionThenSucceeds() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:pwd");
    when(http.put(anyString(), anyString(), anyString()))
      .thenThrow(new RuntimeException("transient network error"))
      .thenReturn(new EpicHttpResponse(201, "{}"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    MintResult result = minter.mint(req);

    assertThat(result.pid()).startsWith("https://hdl.handle.net/");
    verify(http, times(2)).put(anyString(), anyString(), anyString());
  }

  @Test
  void mint_doesNotRetryOn4xx() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:pwd");
    when(http.put(anyString(), anyString(), anyString()))
      .thenReturn(new EpicHttpResponse(400, "bad"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("HTTP 400");

    verify(http, times(1)).put(anyString(), anyString(), anyString());
  }

  @Test
  void mint_failsAfterSecondNetworkException() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:pwd");
    when(http.put(anyString(), anyString(), anyString()))
      .thenThrow(new RuntimeException("error 1"))
      .thenThrow(new RuntimeException("error 2"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("retry");

    verify(http, times(2)).put(anyString(), anyString(), anyString());
  }

  // ─── request body ────────────────────────────────────────────────────────

  @Test
  void buildRequestBody_containsUrlRecord() throws Exception {
    MintRequest req = new MintRequest(
      "data-objects",
      "appid-42",
      "https://shepard.example.org/v2/data-objects/appid-42",
      1,
      Map.of()
    );

    String body = minter.buildRequestBody(req);
    JsonNode tree = new ObjectMapper().readTree(body);

    assertThat(tree.isArray()).isTrue();
    JsonNode urlRecord = tree.get(0);
    assertThat(urlRecord.path("type").asText()).isEqualTo("URL");
    assertThat(urlRecord.path("parsed_data").asText())
      .isEqualTo("https://shepard.example.org/v2/data-objects/appid-42");
  }

  @Test
  void buildRequestBody_includesNameWhenPresent() throws Exception {
    MintRequest req = new MintRequest(
      "data-objects",
      "appid-42",
      "https://x/y",
      1,
      Map.of("name", "My dataset")
    );

    String body = minter.buildRequestBody(req);
    JsonNode tree = new ObjectMapper().readTree(body);

    boolean hasNameRecord = false;
    for (JsonNode node : tree) {
      if ("NAME".equals(node.path("type").asText())) {
        assertThat(node.path("parsed_data").asText()).isEqualTo("My dataset");
        hasNameRecord = true;
      }
    }
    assertThat(hasNameRecord).isTrue();
  }

  @Test
  void buildRequestBody_includesVersionRecord() throws Exception {
    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", 3, Map.of());

    String body = minter.buildRequestBody(req);
    JsonNode tree = new ObjectMapper().readTree(body);

    boolean hasVersionRecord = false;
    for (JsonNode node : tree) {
      if ("VERSION".equals(node.path("type").asText())) {
        assertThat(node.path("parsed_data").asText()).isEqualTo("v3");
        hasVersionRecord = true;
      }
    }
    assertThat(hasVersionRecord).isTrue();
  }

  @Test
  void buildRequestBody_omitsNameRecordWhenAbsent() throws Exception {
    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", 1, Map.of());

    String body = minter.buildRequestBody(req);
    JsonNode tree = new ObjectMapper().readTree(body);

    for (JsonNode node : tree) {
      assertThat(node.path("type").asText()).isNotEqualTo("NAME");
    }
  }

  // ─── basicAuth helper ────────────────────────────────────────────────────

  @Test
  void basicAuth_producesBasicHeader() {
    String header = EpicMinter.basicAuth("user:secret");
    assertThat(header).startsWith("Basic ");
    // Verify it's valid base64
    assertThat(header.substring(6)).isNotBlank();
  }

  @Test
  void basicAuth_handlesNullCredentialGracefully() {
    String header = EpicMinter.basicAuth(null);
    assertThat(header).startsWith("Basic ");
    // base64("") = ""
    assertThat(header).isEqualTo("Basic ");
  }

  // ─── URL helpers ────────────────────────────────────────────────────────

  @Test
  void stripTrailingSlash_idempotent() {
    assertThat(EpicMinter.stripTrailingSlash("https://x/")).isEqualTo("https://x");
    assertThat(EpicMinter.stripTrailingSlash("https://x")).isEqualTo("https://x");
    assertThat(EpicMinter.stripTrailingSlash(null)).isEqualTo("");
  }

  @Test
  void truncate_capsLongStrings() {
    assertThat(EpicMinter.truncate("hello world", 5)).isEqualTo("hello" + "…");
    assertThat(EpicMinter.truncate("short", 100)).isEqualTo("short");
    assertThat(EpicMinter.truncate(null, 10)).isEqualTo("");
  }

  @Test
  void hdlResolverBase_isGlobalStandardResolver() {
    assertThat(EpicMinter.HDL_RESOLVER_BASE).isEqualTo("https://hdl.handle.net/");
  }

  @Test
  void mint_pidUsesHdlResolver() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("user:secret");
    when(http.put(anyString(), anyString(), anyString())).thenReturn(new EpicHttpResponse(201, "{}"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());
    MintResult result = minter.mint(req);

    assertThat(result.pid()).startsWith("https://hdl.handle.net/21.T11148/");
  }
}
