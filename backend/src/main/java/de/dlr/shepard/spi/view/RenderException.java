package de.dlr.shepard.spi.view;

/**
 * VIS-S1 — typed exception a {@link ViewRecipeRenderer} throws for an
 * unrecoverable failure that the dispatcher should translate into an
 * HTTP 4xx/5xx (rather than a 200 with per-binding status codes).
 *
 * <p>The {@link #code()} maps to an RFC 7807 {@code type} URN. Common
 * codes the dispatcher recognises:
 *
 * <ul>
 *   <li>{@code render.body.invalid} — the template body doesn't parse
 *       under this renderer's contract; surfaced as 422.</li>
 *   <li>{@code render.focus.not-found} — the focus DataObject
 *       referenced doesn't exist; surfaced as 404.</li>
 *   <li>{@code render.unit-mismatch.fatal} — a renderer that refuses
 *       to project on unit mismatch rather than degrade; surfaced as
 *       422. Most renderers should emit per-binding
 *       {@code UNIT_MISMATCH} status codes instead.</li>
 * </ul>
 *
 * <p>Unknown codes fall through to HTTP 500 with the
 * {@code render.internal-error} type URN.
 */
public class RenderException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * @param code    short error code (see class docs for the
   *                vocabulary the dispatcher recognises)
   * @param message human-readable message
   */
  public RenderException(String code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * @param code    short error code
   * @param message human-readable message
   * @param cause   underlying cause
   */
  public RenderException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /** @return the typed error code (e.g. {@code render.body.invalid}) */
  public String code() {
    return code;
  }
}
