package de.dlr.shepard.spi.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * AI1a — pins the {@link AiCapability} enum shape against the
 * decision-3 union: TEXT, FAST_TEXT, STRUCTURED, IMAGE_GEN, VISION,
 * EMBEDDING, TRANSCRIPTION, MODERATION.
 *
 * <p>The test fails fast if a future contributor renames or removes a
 * value — every consumer plugin compiles against these names.
 */
class AiCapabilityTest {

  @Test
  void enumContainsExactlyTheEightDecisionThreeValues() {
    assertThat(AiCapability.values()).hasSize(8);
    Set<String> declared = Set.of(
      "TEXT", "FAST_TEXT", "STRUCTURED",
      "IMAGE_GEN", "VISION", "EMBEDDING",
      "TRANSCRIPTION", "MODERATION"
    );
    Set<String> actual = new java.util.LinkedHashSet<>();
    for (AiCapability c : AiCapability.values()) {
      actual.add(c.name());
    }
    assertThat(actual).isEqualTo(declared);
  }

  @Test
  void imageGenAndVisionAreSeparateValues() {
    // Doc 86 explicitly honours this split — "generating an image"
    // and "analysing an image" need different slot configurations.
    assertThat(AiCapability.IMAGE_GEN).isNotEqualTo(AiCapability.VISION);
  }

  @Test
  void transcriptionAndModerationArePresent() {
    // AI1a reconciliation — these two were missing from the thin 63dcbf7c sketch.
    assertThat(AiCapability.TRANSCRIPTION).isNotNull();
    assertThat(AiCapability.MODERATION).isNotNull();
  }
}
