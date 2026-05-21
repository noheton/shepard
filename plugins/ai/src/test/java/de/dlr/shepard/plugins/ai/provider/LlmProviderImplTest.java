package de.dlr.shepard.plugins.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.ai.daos.AiActivityDAO;
import de.dlr.shepard.plugins.ai.entities.AiCapabilityConfig;
import de.dlr.shepard.plugins.ai.services.AiCapabilityConfigService;
import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.LlmException;
import de.dlr.shepard.spi.ai.LlmRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AI1 — unit tests for {@link LlmProviderImpl}.
 *
 * <p>Tests the guard behaviour of {@link LlmProviderImpl#complete} and
 * {@link LlmProviderImpl#isAvailable} without a live LLM endpoint.
 * The REST client is not exercised here (that requires an integration test).
 */
class LlmProviderImplTest {

  private AiCapabilityConfigService configService;
  private AiActivityDAO activityDAO;
  private LlmProviderImpl provider;

  @BeforeEach
  void setUp() {
    configService = mock(AiCapabilityConfigService.class);
    activityDAO = mock(AiActivityDAO.class);
    provider = new LlmProviderImpl();
    // Inject mocks via field access (CDI fields, not constructor-injected).
    provider.configService = configService;
    provider.activityDAO = activityDAO;
  }

  // ─── complete() guard tests ───────────────────────────────────────

  @Test
  void complete_throwsLlmException_whenCapabilityNotConfigured() {
    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.empty());

    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("Summarise this dataset")
      .build();

    assertThatThrownBy(() -> provider.complete(request))
      .isInstanceOf(LlmException.class)
      .hasMessageContaining("not configured");
  }

  @Test
  void complete_throwsLlmException_whenCapabilityDisabled() {
    AiCapabilityConfig cfg = new AiCapabilityConfig();
    cfg.setCapability("TEXT");
    cfg.setEnabled(Boolean.FALSE);
    cfg.setEndpointUrl("https://api.example.com/v1");
    cfg.setModel("gpt-4o");

    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.of(cfg));

    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("Summarise this dataset")
      .build();

    assertThatThrownBy(() -> provider.complete(request))
      .isInstanceOf(LlmException.class)
      .hasMessageContaining("not enabled");
  }

  @Test
  void complete_throwsLlmException_whenEndpointUrlMissing() {
    AiCapabilityConfig cfg = new AiCapabilityConfig();
    cfg.setCapability("TEXT");
    cfg.setEnabled(Boolean.TRUE);
    cfg.setEndpointUrl(null);
    cfg.setModel("gpt-4o");

    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.of(cfg));

    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("Summarise this dataset")
      .build();

    assertThatThrownBy(() -> provider.complete(request))
      .isInstanceOf(LlmException.class)
      .hasMessageContaining("endpointUrl");
  }

  @Test
  void complete_throwsLlmException_whenModelMissing() {
    AiCapabilityConfig cfg = new AiCapabilityConfig();
    cfg.setCapability("TEXT");
    cfg.setEnabled(Boolean.TRUE);
    cfg.setEndpointUrl("https://api.example.com/v1");
    cfg.setModel(null);

    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.of(cfg));

    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("Summarise this dataset")
      .build();

    assertThatThrownBy(() -> provider.complete(request))
      .isInstanceOf(LlmException.class)
      .hasMessageContaining("model");
  }

  // ─── isAvailable() tests ──────────────────────────────────────────

  @Test
  void isAvailable_returnsFalse_whenNoConfig() {
    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.empty());

    assertThat(provider.isAvailable(AiCapability.TEXT)).isFalse();
  }

  @Test
  void isAvailable_returnsFalse_whenDisabled() {
    AiCapabilityConfig cfg = new AiCapabilityConfig();
    cfg.setEnabled(Boolean.FALSE);
    cfg.setEndpointUrl("https://api.example.com/v1");
    cfg.setModel("gpt-4o");

    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.of(cfg));

    assertThat(provider.isAvailable(AiCapability.TEXT)).isFalse();
  }

  @Test
  void isAvailable_returnsFalse_whenEnabledButNoEndpoint() {
    AiCapabilityConfig cfg = new AiCapabilityConfig();
    cfg.setEnabled(Boolean.TRUE);
    cfg.setEndpointUrl(null);
    cfg.setModel("gpt-4o");

    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.of(cfg));

    assertThat(provider.isAvailable(AiCapability.TEXT)).isFalse();
  }

  @Test
  void isAvailable_returnsFalse_whenEnabledButNoModel() {
    AiCapabilityConfig cfg = new AiCapabilityConfig();
    cfg.setEnabled(Boolean.TRUE);
    cfg.setEndpointUrl("https://api.example.com/v1");
    cfg.setModel("");

    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.of(cfg));

    assertThat(provider.isAvailable(AiCapability.TEXT)).isFalse();
  }

  @Test
  void isAvailable_returnsTrue_whenFullyConfigured() {
    AiCapabilityConfig cfg = new AiCapabilityConfig();
    cfg.setEnabled(Boolean.TRUE);
    cfg.setEndpointUrl("https://api.example.com/v1");
    cfg.setModel("gpt-4o");

    when(configService.findConfig(AiCapability.TEXT)).thenReturn(Optional.of(cfg));

    assertThat(provider.isAvailable(AiCapability.TEXT)).isTrue();
  }

  // ─── sha256Hex tests ──────────────────────────────────────────────

  @Test
  void sha256Hex_producesValidHexString() {
    String hash = LlmProviderImpl.sha256Hex("hello world");
    assertThat(hash)
      .hasSize(64)
      .matches("[0-9a-f]+");
  }

  @Test
  void sha256Hex_isDeterministic() {
    String input = "test input";
    assertThat(LlmProviderImpl.sha256Hex(input))
      .isEqualTo(LlmProviderImpl.sha256Hex(input));
  }

  @Test
  void sha256Hex_differsByInput() {
    assertThat(LlmProviderImpl.sha256Hex("a"))
      .isNotEqualTo(LlmProviderImpl.sha256Hex("b"));
  }
}
