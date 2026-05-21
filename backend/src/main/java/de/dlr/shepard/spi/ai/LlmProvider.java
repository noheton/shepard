package de.dlr.shepard.spi.ai;

/**
 * SPI contract for LLM provider implementations.
 *
 * <p>Implementations live in {@code shepard-plugin-ai} and are CDI
 * {@code @ApplicationScoped} beans. The backend core never depends on
 * the implementation directly — it injects this interface via CDI.
 *
 * <p>The provider is responsible for:
 * <ul>
 *   <li>Assembling the layered prompt (system → trusted context →
 *       untrusted documents → user instruction)</li>
 *   <li>Writing the {@code :AiActivity} provenance node and returning
 *       its appId in {@link LlmResponse#activityAppId()}</li>
 *   <li>Enforcing the per-capability {@code :AiCapabilityConfig}
 *       guardrails prefix/suffix</li>
 * </ul>
 *
 * <p>Callers must not assume the provider is present — guard with
 * {@code jakarta.enterprise.inject.Instance<LlmProvider>} and check
 * {@code isResolvable()} before use.
 */
public interface LlmProvider {

  /**
   * Synchronously complete an LLM request.
   *
   * @param request the assembled request (capability + layered prompt)
   * @return the model's reply plus provenance metadata
   * @throws LlmException if the capability is not configured, the
   *   upstream call fails, or guardrails reject the output
   */
  LlmResponse complete(LlmRequest request) throws LlmException;

  /**
   * Returns {@code true} if the given capability slot is configured
   * and enabled at runtime (i.e. a call to {@link #complete} for this
   * capability would not throw immediately due to missing config).
   */
  boolean isAvailable(AiCapability capability);
}
