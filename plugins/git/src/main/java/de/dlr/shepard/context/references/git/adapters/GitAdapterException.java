package de.dlr.shepard.context.references.git.adapters;

/**
 * Operator-facing failure from a {@link GitAdapter}. Carries an HTTP-ish
 * status code so the service layer can map to RFC 7807 problem responses
 * without reverse-parsing the message.
 *
 * <p>The {@code message} is shaped to be readable by the human that owns
 * the PAT — examples:
 * <ul>
 *   <li>"Repository not found or access denied — verify your PAT has
 *       {@code read_repository} scope" (403/404).</li>
 *   <li>"GitLab returned 500 after a retry — try again in a minute" (5xx).</li>
 * </ul>
 */
public class GitAdapterException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** HTTP-style status. 0 when the failure is non-HTTP (e.g. timeout, malformed URL). */
  private final int status;

  public GitAdapterException(int status, String message) {
    super(message);
    this.status = status;
  }

  public GitAdapterException(int status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }
}
