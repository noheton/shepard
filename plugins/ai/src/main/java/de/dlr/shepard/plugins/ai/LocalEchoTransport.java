package de.dlr.shepard.plugins.ai;

import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.AiException;
import de.dlr.shepard.spi.ai.LlmRequest;
import de.dlr.shepard.spi.ai.LlmResponse;
import de.dlr.shepard.spi.ai.Transport;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * AI1a — the canonical {@link Transport} reference impl.
 *
 * <p><b>For testing / onboarding only — NOT a production model.</b>
 *
 * <p>Purpose:
 *
 * <ul>
 *   <li><b>Smoke-test the registry.</b> A fresh shepard install with no
 *       real provider configured can still exercise the full call path:
 *       dispatch through {@link de.dlr.shepard.spi.ai.AiRegistry},
 *       capability resolution, transport invocation, response shape.
 *       Failures here mean the SPI is broken; failures in a real
 *       provider transport are vendor-specific.</li>
 *   <li><b>Onboarding example.</b> A new contributor wiring a custom
 *       provider reads this class to see the minimum shape of a
 *       {@code Transport}: a CDI-scoped bean, capability declaration,
 *       deterministic {@link #send} returning an {@link LlmResponse}.</li>
 *   <li><b>Deterministic CI.</b> The {@code TEXT}-only echo response is
 *       reproducible — no flaky external HTTP calls in the test suite.</li>
 * </ul>
 *
 * <p><b>Why TEXT only.</b> An echo impl can't meaningfully synthesise
 * structured JSON, embeddings, or images — claiming
 * {@link AiCapability#STRUCTURED} or {@link AiCapability#IMAGE_GEN}
 * would make it look like a real adapter to a misconfigured admin.
 * Returning {@code Set.of(TEXT)} keeps the canonical-example role
 * narrow and obvious.
 *
 * <p><b>What this is NOT.</b> Do not point a production deployment at
 * this transport. The "model" is a deterministic string transformation
 * of the user's instruction; it has no LLM intelligence.
 */
@ApplicationScoped
public class LocalEchoTransport implements Transport {

  /**
   * Stable id matching the {@code transport} field on the
   * per-capability slot config. An admin that sets
   * {@code /v2/admin/ai/capabilities/TEXT { "transport": "local-echo" }}
   * dispatches through this bean.
   */
  public static final String ID = "local-echo";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public Set<AiCapability> supportedCapabilities() {
    return Set.of(AiCapability.TEXT);
  }

  @Override
  public boolean isEnabled() {
    // Always enabled — no external dependencies, no credentials,
    // no rate limits. The whole point is "this works without
    // configuration so the dispatch path is testable".
    return true;
  }

  @Override
  public LlmResponse send(LlmRequest request, TransportContext context) throws AiException {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
    // Deterministic synthetic response. The text echoes the user
    // instruction with a labelled prefix so a test fixture can
    // pattern-match on the wire format.
    String instr = request.userInstruction() == null ? "" : request.userInstruction();
    StringBuilder body = new StringBuilder();
    body.append("[local-echo capability=").append(request.capability().name()).append("] ");
    body.append(instr);

    String trusted = request.trustedContext();
    if (trusted != null && !trusted.isBlank()) {
      body.append(" {trusted=").append(trusted.length()).append("ch}");
    }
    if (!request.untrustedDocuments().isEmpty()) {
      body.append(" {untrusted-docs=").append(request.untrustedDocuments().size()).append("}");
    }

    String model = context == null || context.model() == null
      ? "echo-noop-v1"
      : context.model();

    Log.debugf(
      "LocalEchoTransport.send: capability=%s, instruction-length=%d, " +
      "untrusted-docs=%d -> model=%s",
      request.capability(),
      instr.length(),
      request.untrustedDocuments().size(),
      model
    );

    // Token counts are heuristic — input tokens roughly the
    // character-length of the assembled instruction / 4, output
    // tokens the response body / 4. Not accurate; sufficient to
    // exercise the LlmResponse field plumbing end-to-end.
    int inputTokens = Math.max(1, instr.length() / 4);
    int outputTokens = Math.max(1, body.length() / 4);

    return LlmResponse.builder()
      .text(body.toString())
      .inputTokens(inputTokens)
      .outputTokens(outputTokens)
      // activityAppId — provenance writer in LlmProviderImpl attaches this
      .build();
  }
}
