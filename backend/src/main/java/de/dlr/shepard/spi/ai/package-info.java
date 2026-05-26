/**
 * AI1a SPI seam — the in-tree contract every {@code shepard-plugin-ai*}
 * module compiles against, designed in
 * {@code aidocs/platform/86-ai-plugin-design.md}.
 *
 * <p>The package follows the same "SPI in core, adapters in plugins"
 * pattern established by {@link de.dlr.shepard.spi.payload.PayloadKind}
 * (P-series) and {@link de.dlr.shepard.publish.minter.Minter} (KIP1a):
 * every type here is a stability contract for consumers and a vendor
 * extension point for adapters.
 *
 * <p><b>Two SPI layers</b> (per doc 86 §2 + §13):
 *
 * <ul>
 *   <li><b>{@link de.dlr.shepard.spi.ai.LlmProvider}</b> — the
 *       <i>consumer-facing</i> API. The wiki-writer, anomaly
 *       detection, channel quality scoring, and every other
 *       dependent plugin calls {@code llmProvider.complete(request)}
 *       and never sees a vendor wire shape. Capability ({@code TEXT},
 *       {@code STRUCTURED}, {@code IMAGE_GEN}, …) is the only
 *       knob consumers turn.</li>
 *   <li><b>{@link de.dlr.shepard.spi.ai.Transport}</b> — the
 *       <i>vendor sub-SPI</i> declared as doc 86 §13's extension
 *       point. Adapters for OpenAI-compat, Anthropic Messages,
 *       Google Vertex, Ollama, or operator-supplied {@code CUSTOM}
 *       proxies each implement this interface. When the OpenAI wire
 *       shape changes or a reasoning-model family requires a
 *       fundamentally different call pattern, only the {@code Transport}
 *       adapter is touched — consumer plugins are insulated.</li>
 * </ul>
 *
 * <p>The {@link de.dlr.shepard.spi.ai.AiCapability} enum drives both
 * layers: consumers declare what they need, admins map capabilities
 * to {@code Transport} instances + endpoints at runtime, the
 * registry dispatches.
 *
 * <p><b>What lives here vs. what lives in {@code shepard-plugin-ai}:</b>
 *
 * <ul>
 *   <li><i>Here (backend SPI):</i> the interfaces, the request/response
 *       DTOs, the capability enum, the f(ai)²r predicate-name
 *       constants reserved for later TPL9 binding, the
 *       {@link de.dlr.shepard.spi.ai.AiRegistry} CDI dispatcher.</li>
 *   <li><i>In the plugin module:</i> every concrete
 *       {@code Transport} (the canonical {@code LocalEchoTransport}
 *       reference impl, then OpenAI-compat, Anthropic, …), the
 *       {@code :AiCapabilityConfig} + {@code :AiGlobalConfig} Neo4j
 *       entities and their DAOs, the {@code /v2/admin/ai/*} REST
 *       resources.</li>
 * </ul>
 *
 * @see de.dlr.shepard.spi.payload.PayloadKind P-series SPI prior art
 * @see de.dlr.shepard.publish.minter.Minter KIP1a SPI prior art
 */
package de.dlr.shepard.spi.ai;
