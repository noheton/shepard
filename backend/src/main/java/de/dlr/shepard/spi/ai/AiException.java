package de.dlr.shepard.spi.ai;

/**
 * AI1a — checked-style runtime exception thrown by {@link Transport}
 * implementations when an upstream call cannot complete (network
 * failure, provider rejection, credential expiry, rate-limit
 * exhaustion after back-off).
 *
 * <p>Distinct from {@link LlmException} in the two-layer SPI design:
 * {@link Transport} implementations throw {@code AiException} for
 * vendor-wire-level failures; the {@link LlmProvider} wraps these
 * (and adds capability-routing failures) and surfaces them as
 * {@link LlmException} to consumer plugins. Consumer plugins
 * therefore only need to handle {@link LlmException}.
 *
 * <p>Mirrors the shape of
 * {@link de.dlr.shepard.publish.minter.MinterException} from the
 * KIP1a SPI: operator-readable message + optional cause.
 *
 * <p>Sub-cases worth distinguishing at the caller layer:
 *
 * <ul>
 *   <li>{@code capability unconfigured} — the slot has no active
 *       {@link Transport}. Translates to HTTP 503
 *       {@code ai.capability.unconfigured} per the BYOK resolution
 *       chain (doc 86 §4).</li>
 *   <li>{@code rate-limited} — HTTP 429 from the upstream provider;
 *       the consumer should respect any {@code retry-after} signal
 *       surfaced via this exception's message.</li>
 *   <li>{@code injection-blocked} — the pre-flight scan flagged
 *       content and {@code blockOnSuspiciousContent=true}. The
 *       provenance node was still written with
 *       {@code injectionFlagged=true}.</li>
 * </ul>
 */
public class AiException extends RuntimeException {

  /** Serial version uid — exception class evolution stays opt-in. */
  private static final long serialVersionUID = 1L;

  public AiException(String message) {
    super(message);
  }

  public AiException(String message, Throwable cause) {
    super(message, cause);
  }
}
