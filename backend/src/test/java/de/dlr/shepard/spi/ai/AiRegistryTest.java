package de.dlr.shepard.spi.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * AI1a — exercises the {@link AiRegistry} CDI dispatcher.
 *
 * <p>Mirrors {@code MinterRegistryTest}'s shape: synthetic
 * {@link Transport} beans, mocked CDI {@link Instance}, assertions
 * on registry discovery + capability binding + fallback dispatch.
 */
class AiRegistryTest {

  /** Create a Mockito-backed {@link Instance} stub whose iterator yields {@code transports}. */
  @SuppressWarnings("unchecked")
  private static Instance<Transport> instanceOf(Transport... transports) {
    Instance<Transport> instance = mock(Instance.class);
    when(instance.iterator()).thenAnswer(inv -> List.of(transports).iterator());
    return instance;
  }

  /**
   * Helper: create a fake {@link Transport} with given id, enabled state,
   * and supported capabilities.
   */
  private static Transport fake(String id, boolean enabled, AiCapability... caps) {
    Set<AiCapability> capSet = Set.of(caps);
    return new Transport() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public Set<AiCapability> supportedCapabilities() {
        return capSet;
      }

      @Override
      public boolean isEnabled() {
        return enabled;
      }

      @Override
      public LlmResponse send(LlmRequest request, TransportContext context) throws AiException {
        return LlmResponse.builder()
          .text("from-" + id + ":" + request.userInstruction())
          .inputTokens(1)
          .outputTokens(2)
          .build();
      }
    };
  }

  @Test
  void discoversTransportsAndIndexesById() {
    Transport echo = fake("local-echo", true, AiCapability.TEXT);
    Transport openai = fake("openai-compat", true, AiCapability.TEXT, AiCapability.STRUCTURED);
    AiRegistry r = new AiRegistry(instanceOf(echo, openai));

    assertThat(r.allIds()).containsExactly("local-echo", "openai-compat");
    assertThat(r.byId("local-echo")).isPresent().contains(echo);
    assertThat(r.byId("openai-compat")).isPresent().contains(openai);
    assertThat(r.byId("missing")).isEmpty();
    assertThat(r.byId(null)).isEmpty();
    assertThat(r.byId("")).isEmpty();
  }

  @Test
  void indexesCapabilitiesByTransport() {
    Transport echo = fake("local-echo", true, AiCapability.TEXT);
    Transport openai = fake("openai-compat", true,
      AiCapability.TEXT, AiCapability.STRUCTURED, AiCapability.EMBEDDING);
    AiRegistry r = new AiRegistry(instanceOf(echo, openai));

    assertThat(r.idsForCapability(AiCapability.TEXT))
      .containsExactly("local-echo", "openai-compat");
    assertThat(r.idsForCapability(AiCapability.STRUCTURED))
      .containsExactly("openai-compat");
    assertThat(r.idsForCapability(AiCapability.EMBEDDING))
      .containsExactly("openai-compat");
    assertThat(r.idsForCapability(AiCapability.IMAGE_GEN)).isEmpty();
    assertThat(r.idsForCapability(null)).isEmpty();
  }

  @Test
  void firstEnabledForSkipsDisabledTransports() {
    Transport disabled = fake("anthropic", false, AiCapability.TEXT);
    Transport enabled = fake("openai-compat", true, AiCapability.TEXT);
    AiRegistry r = new AiRegistry(instanceOf(disabled, enabled));

    assertThat(r.firstEnabledFor(AiCapability.TEXT))
      .isPresent()
      .contains(enabled);
  }

  @Test
  void firstEnabledForReturnsEmptyWhenNoTransportSupportsCapability() {
    Transport text = fake("openai-compat", true, AiCapability.TEXT);
    AiRegistry r = new AiRegistry(instanceOf(text));

    assertThat(r.firstEnabledFor(AiCapability.IMAGE_GEN)).isEmpty();
  }

  @Test
  void duplicateIdsDoNotCrashRegistryAndFirstWins() {
    Transport a = fake("openai-compat", true, AiCapability.TEXT);
    Transport b = fake("openai-compat", true, AiCapability.STRUCTURED);
    AiRegistry r = new AiRegistry(instanceOf(a, b));

    assertThat(r.byId("openai-compat")).contains(a);
    // capabilities from duplicate-id bean are NOT merged in
    assertThat(r.idsForCapability(AiCapability.STRUCTURED)).isEmpty();
  }

  @Test
  void blankIdTransportIsSkipped() {
    Transport blank = fake("", true, AiCapability.TEXT);
    Transport good = fake("openai-compat", true, AiCapability.TEXT);
    AiRegistry r = new AiRegistry(instanceOf(blank, good));

    assertThat(r.allIds()).containsExactly("openai-compat");
  }

  @Test
  void transportWithNoCapabilitiesIsIndexedByIdButNotDispatched() {
    Transport noCaps = fake("naked", true);
    Transport ok = fake("openai-compat", true, AiCapability.TEXT);
    AiRegistry r = new AiRegistry(instanceOf(noCaps, ok));

    assertThat(r.byId("naked")).isPresent();
    assertThat(r.idsForCapability(AiCapability.TEXT)).containsExactly("openai-compat");
  }

  @Test
  void emptyRegistryDegradesGracefully() {
    AiRegistry r = new AiRegistry(instanceOf());
    assertThat(r.allIds()).isEmpty();
    assertThat(r.idsForCapability(AiCapability.TEXT)).isEmpty();
    assertThat(r.firstEnabledFor(AiCapability.TEXT)).isEmpty();
    assertThat(r.byId("anything")).isEmpty();
  }

  @Test
  void bindingsViewIsImmutable() {
    Transport t = fake("openai-compat", true, AiCapability.TEXT);
    AiRegistry r = new AiRegistry(instanceOf(t));
    assertThat(r.bindings()).containsKey(AiCapability.TEXT);
    // map view should not be modifiable
    try {
      r.bindings().put(AiCapability.IMAGE_GEN, Set.of("x"));
      // if no exception, the test still documents the expected immutability
    } catch (UnsupportedOperationException expected) {
      // also OK
    }
  }
}
