package de.dlr.shepard.spi.ai;

import java.util.Set;

/**
 * AI1a — the <strong>vendor sub-SPI</strong> declared as the
 * extension point in {@code aidocs/platform/86-ai-plugin-design.md §13}.
 * One {@code Transport} per provider wire shape: OpenAI-compat
 * (covers LiteLLM, OpenAI direct, Ollama, Azure OpenAI),
 * Anthropic Messages, Google Vertex, operator-supplied {@code CUSTOM}
 * adapters.
 *
 * <p><b>Separation of concerns vs. {@link LlmProvider}:</b>
 *
 * <ul>
 *   <li>{@link LlmProvider} is consumer-facing: wiki-writer +
 *       friends call {@code provider.complete(request)} and never
 *       touch a {@code Transport}. Capability is the routing key.</li>
 *   <li>{@code Transport} is vendor-facing: each implementation
 *       knows the wire format of one provider family. It receives
 *       a fully-assembled {@link LlmRequest} (already guarded,
 *       call-stack-isolated, capability-resolved) and is
 *       responsible only for "talk to the upstream and return the
 *       generated content + token counts."</li>
 *   <li>Each capability slot in the {@code :AiCapabilityConfig}
 *       singleton names a {@code Transport} by id + endpoint +
 *       model + apiKey. The active provider plugin resolves the
 *       {@code Transport} bean by id at call time.</li>
 * </ul>
 *
 * <p>Discovery shape follows the KIP1a
 * {@link de.dlr.shepard.publish.minter.Minter} registry idiom:
 * {@code Transport}s are discovered as CDI beans, the
 * {@link AiRegistry} walks them at startup, and a per-capability
 * slot pins one transport id as active.
 *
 * <p><b>Why this matters long-term.</b> Doc 86 §13 explains: when
 * the OpenAI wire shape changes, a new dominant API emerges, or
 * reasoning models require a different call pattern, <i>only</i>
 * the {@code Transport} is touched. Every consumer plugin remains
 * unchanged because {@link LlmProvider#complete} is stable.
 *
 * <p><b>What's intentionally NOT here:</b>
 *
 * <ul>
 *   <li>No HTTP client — implementations choose Quarkus REST Client /
 *       Mutiny HTTP / standard {@link java.net.http.HttpClient}; the
 *       SPI doesn't constrain.</li>
 *   <li>No streaming API in AI1a. SSE-aggregation lives inside each
 *       {@code Transport}'s {@code send} implementation with the
 *       result surfaced through {@link LlmResponse}.</li>
 *   <li>No retry / fault tolerance — implementations decorate with
 *       {@code microprofile-fault-tolerance} where needed; the
 *       SPI stays minimal.</li>
 * </ul>
 */
public interface Transport {

  /**
   * Stable identifier for this transport — e.g. {@code "openai-compat"},
   * {@code "anthropic"}, {@code "google-vertex"}, {@code "local-echo"}.
   * Matches the {@code transport} field on the per-capability slot
   * config; the registry uses this to dispatch.
   *
   * <p>Conflicting ids (two beans return the same {@code id()}) log a
   * WARN and the first-discovered bean wins, matching the
   * {@link de.dlr.shepard.publish.minter.MinterRegistry} idiom.
   */
  String id();

  /**
   * Capabilities this transport can serve. The registry refuses to
   * dispatch a {@code STRUCTURED} call through a transport that
   * doesn't list it; admins see the constraint surfaced via
   * {@code GET /v2/admin/ai/providers}.
   *
   * <p>A real adapter typically returns most of the enum — gating
   * by model is the slot config's responsibility, not the
   * transport's. The canonical {@code LocalEchoTransport}
   * reference impl returns only {@code TEXT} so it serves as an
   * obvious unconfigured-but-dispatchable signpost.
   */
  Set<AiCapability> supportedCapabilities();

  /**
   * Whether this transport is currently usable (deploy-time
   * config + classpath dependencies present). Returning
   * {@code false} keeps the bean in the registry but prevents the
   * dispatcher from routing through it.
   */
  boolean isEnabled();

  /**
   * Perform the upstream call. The {@link LlmRequest} arrives
   * already assembled by the calling {@link LlmProvider}: prompts
   * structurally isolated, guardrails wrapped. The transport's
   * responsibility is reduced to "translate this to the provider's
   * wire format, send, parse the response."
   *
   * @param request  the assembled request (capability + layered prompt)
   * @param context  per-call resolution: endpoint, model, apiKey, limits
   * @return the provider response
   * @throws AiException when the upstream call cannot complete
   */
  LlmResponse send(LlmRequest request, TransportContext context) throws AiException;

  /**
   * Per-call resolution shape: which model name to send, which
   * endpoint to hit, which key to authenticate with. Resolved by
   * the {@link LlmProvider} from the slot config and handed to the
   * transport.
   *
   * <p>The transport itself doesn't read the
   * {@code :AiCapabilityConfig} entity — that decoupling lets the
   * registry stay the single owner of slot resolution.
   *
   * @param endpointUrl  full URL of the upstream provider
   * @param model        provider-side model identifier
   * @param apiKey       authentication credential, plaintext at the
   *                     call boundary; never logged
   * @param maxTokens    slot-default max tokens
   * @param temperature  slot-default temperature
   */
  record TransportContext(
    String endpointUrl,
    String model,
    String apiKey,
    Integer maxTokens,
    Double temperature
  ) {}
}
