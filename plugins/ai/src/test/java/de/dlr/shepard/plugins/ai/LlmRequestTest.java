package de.dlr.shepard.plugins.ai;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.LlmRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI1 — unit tests for {@link LlmRequest} builder.
 *
 * <p>Verifies that the builder correctly assembles and exposes all
 * field values, including defaults for optional fields.
 */
class LlmRequestTest {

  @Test
  void builder_setsCapability() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT).build();
    assertThat(request.capability()).isEqualTo(AiCapability.TEXT);
  }

  @Test
  void builder_setsCapability_structured() {
    LlmRequest request = LlmRequest.builder(AiCapability.STRUCTURED).build();
    assertThat(request.capability()).isEqualTo(AiCapability.STRUCTURED);
  }

  @Test
  void builder_defaultsToEmptyStrings() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT).build();
    assertThat(request.pluginSystemPrompt()).isEqualTo("");
    assertThat(request.trustedContext()).isEqualTo("");
    assertThat(request.userInstruction()).isEqualTo("");
  }

  @Test
  void builder_defaultsToEmptyUntrustedDocuments() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT).build();
    assertThat(request.untrustedDocuments()).isNotNull().isEmpty();
  }

  @Test
  void builder_defaultsMaxTokensTo2048() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT).build();
    assertThat(request.maxTokens()).isEqualTo(2048);
  }

  @Test
  void builder_defaultsTemperatureTo0Point7() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT).build();
    assertThat(request.temperature()).isEqualTo(0.7);
  }

  @Test
  void builder_setsPluginSystemPrompt() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .pluginSystemPrompt("You are a research data summariser.")
      .build();
    assertThat(request.pluginSystemPrompt()).isEqualTo("You are a research data summariser.");
  }

  @Test
  void builder_setsTrustedContext() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .trustedContext("Collection: TR-004, DataObject: anomaly-investigation")
      .build();
    assertThat(request.trustedContext()).isEqualTo("Collection: TR-004, DataObject: anomaly-investigation");
  }

  @Test
  void builder_addsUntrustedDocuments() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .addUntrustedDocument("First PDF content")
      .addUntrustedDocument("Second PDF content")
      .build();
    assertThat(request.untrustedDocuments())
      .hasSize(2)
      .containsExactly("First PDF content", "Second PDF content");
  }

  @Test
  void builder_setsUserInstruction() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .userInstruction("Summarise the anomaly findings.")
      .build();
    assertThat(request.userInstruction()).isEqualTo("Summarise the anomaly findings.");
  }

  @Test
  void builder_setsMaxTokens() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .maxTokens(512)
      .build();
    assertThat(request.maxTokens()).isEqualTo(512);
  }

  @Test
  void builder_setsTemperature() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .temperature(0.2)
      .build();
    assertThat(request.temperature()).isEqualTo(0.2);
  }

  @Test
  void builder_untrustedDocumentsList_isImmutable() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .addUntrustedDocument("doc1")
      .build();
    assertThat(request.untrustedDocuments())
      .isUnmodifiable();
  }

  @Test
  void builder_allFieldsSet_correctValues() {
    LlmRequest request = LlmRequest.builder(AiCapability.EMBEDDING)
      .pluginSystemPrompt("sys")
      .trustedContext("ctx")
      .addUntrustedDocument("doc")
      .userInstruction("instruction")
      .maxTokens(1024)
      .temperature(0.5)
      .build();

    assertThat(request.capability()).isEqualTo(AiCapability.EMBEDDING);
    assertThat(request.pluginSystemPrompt()).isEqualTo("sys");
    assertThat(request.trustedContext()).isEqualTo("ctx");
    assertThat(request.untrustedDocuments()).containsExactly("doc");
    assertThat(request.userInstruction()).isEqualTo("instruction");
    assertThat(request.maxTokens()).isEqualTo(1024);
    assertThat(request.temperature()).isEqualTo(0.5);
  }

  @Test
  void builder_multipleDocuments_preservesOrder() {
    LlmRequest request = LlmRequest.builder(AiCapability.TEXT)
      .addUntrustedDocument("alpha")
      .addUntrustedDocument("beta")
      .addUntrustedDocument("gamma")
      .build();
    assertThat(request.untrustedDocuments())
      .containsExactly("alpha", "beta", "gamma");
  }

  @Test
  void twoBuilders_areIndependent() {
    LlmRequest r1 = LlmRequest.builder(AiCapability.TEXT)
      .addUntrustedDocument("doc-a")
      .build();
    LlmRequest r2 = LlmRequest.builder(AiCapability.FAST_TEXT)
      .addUntrustedDocument("doc-b")
      .addUntrustedDocument("doc-c")
      .build();

    assertThat(r1.untrustedDocuments()).containsExactly("doc-a");
    assertThat(r2.untrustedDocuments()).containsExactly("doc-b", "doc-c");
  }
}
