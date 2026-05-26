package de.dlr.shepard.plugins.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.LlmRequest;
import de.dlr.shepard.spi.ai.LlmResponse;
import de.dlr.shepard.spi.ai.Transport.TransportContext;
import org.junit.jupiter.api.Test;

/**
 * AI1a — exercises {@link LocalEchoTransport} as the canonical
 * {@link de.dlr.shepard.spi.ai.Transport} reference impl.
 *
 * <p>The echo transport's deterministic response makes it easy to
 * assert on the full dispatch path without needing external
 * provider credentials.
 */
class LocalEchoTransportTest {

  private final LocalEchoTransport transport = new LocalEchoTransport();

  @Test
  void idIsStable() {
    assertThat(transport.id()).isEqualTo(LocalEchoTransport.ID);
    assertThat(transport.id()).isEqualTo("local-echo");
  }

  @Test
  void supportsOnlyText() {
    assertThat(transport.supportedCapabilities())
      .containsExactly(AiCapability.TEXT);
  }

  @Test
  void isAlwaysEnabled() {
    assertThat(transport.isEnabled()).isTrue();
  }

  @Test
  void sendEchoesUserInstruction() throws Exception {
    LlmRequest req = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("hello world")
      .build();
    TransportContext ctx = new TransportContext("http://localhost", "echo-noop-v1", null, null, null);

    LlmResponse resp = transport.send(req, ctx);

    assertThat(resp.text()).contains("hello world");
    assertThat(resp.text()).contains("[local-echo capability=TEXT]");
  }

  @Test
  void sendUsesModelFromContext() throws Exception {
    LlmRequest req = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("test")
      .build();
    TransportContext ctx = new TransportContext("http://localhost", "custom-model", null, null, null);

    LlmResponse resp = transport.send(req, ctx);

    // Response text doesn't embed model name, but token counts are set
    assertThat(resp.inputTokens()).isGreaterThan(0);
    assertThat(resp.outputTokens()).isGreaterThan(0);
  }

  @Test
  void sendWithNullContextUsesDefaultModel() throws Exception {
    LlmRequest req = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("test")
      .build();

    LlmResponse resp = transport.send(req, null);

    assertThat(resp.text()).contains("test");
    assertThat(resp.inputTokens()).isGreaterThan(0);
  }

  @Test
  void sendWithEmptyInstructionHandlesGracefully() throws Exception {
    LlmRequest req = LlmRequest.builder(AiCapability.TEXT).build();
    TransportContext ctx = new TransportContext("http://localhost", "m", null, null, null);

    LlmResponse resp = transport.send(req, ctx);

    assertThat(resp.text()).isNotNull();
    assertThat(resp.inputTokens()).isGreaterThanOrEqualTo(1);
    assertThat(resp.outputTokens()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void sendWithUntrustedDocumentsIncludesCount() throws Exception {
    LlmRequest req = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("summarise")
      .addUntrustedDocument("doc one")
      .addUntrustedDocument("doc two")
      .build();
    TransportContext ctx = new TransportContext("http://localhost", "m", null, null, null);

    LlmResponse resp = transport.send(req, ctx);

    assertThat(resp.text()).contains("untrusted-docs=2");
  }

  @Test
  void sendThrowsOnNullRequest() {
    assertThatThrownBy(() -> transport.send(null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("request");
  }

  @Test
  void activityAppIdIsNullForEchoTransport() throws Exception {
    LlmRequest req = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("x")
      .build();
    TransportContext ctx = new TransportContext("http://localhost", "m", null, null, null);

    LlmResponse resp = transport.send(req, ctx);

    // The echo transport doesn't write provenance — that's the provider's job
    assertThat(resp.activityAppId()).isNull();
  }
}
