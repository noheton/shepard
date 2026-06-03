package de.dlr.shepard.spi.transform;

/**
 * V2CONV-B3 — typed exception a {@link TransformExecutor} throws for an
 * unrecoverable failure that the dispatcher should translate into an HTTP
 * 4xx/5xx. Sibling of {@link de.dlr.shepard.spi.view.RenderException}.
 *
 * <p>The {@link #code()} maps to an RFC 7807 {@code type} URN. Codes the
 * {@code POST /v2/mappings/{templateAppId}/materialize} dispatcher recognises:
 *
 * <ul>
 *   <li>{@code transform.body.invalid} — the template body doesn't parse under
 *       this executor's contract; surfaced as 422.</li>
 *   <li>{@code transform.input.missing} — a required input reference appId was
 *       absent or unresolvable; surfaced as 422.</li>
 *   <li>{@code transform.input.not-found} — a bound input reference appId does
 *       not resolve to an existing reference; surfaced as 404.</li>
 * </ul>
 *
 * <p>Unknown codes fall through to HTTP 500 with the
 * {@code transform.internal-error} type URN.
 */
public class TransformException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  public TransformException(String code, String message) {
    super(message);
    this.code = code;
  }

  public TransformException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /** @return the typed error code (e.g. {@code transform.input.missing}) */
  public String code() {
    return code;
  }
}
