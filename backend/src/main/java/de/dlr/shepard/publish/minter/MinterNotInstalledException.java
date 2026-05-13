package de.dlr.shepard.publish.minter;

/**
 * KIP1h — signals that the publish flow cannot proceed because no
 * {@link Minter} bean is currently active. Raised by
 * {@code PublishService} when {@link MinterRegistry#activeMinter()}
 * is empty (no plugin installed, configured id missing, or active
 * bean reports {@code isEnabled()=false}).
 *
 * <p>Mapped by {@code PublishRest} to RFC 7807
 * {@code publish.minter.not-installed} with HTTP 503 — operators
 * see a clean, actionable error message ("install plugins/minter-local/
 * (or another minter plugin) and set shepard.publish.minter to
 * its id") rather than the cryptic null-pointer that pre-KIP1h would
 * have surfaced.
 *
 * <p>Distinct from {@link MinterException}: that exception signals a
 * mint that started but failed (network blip, upstream rejection);
 * this one signals the prerequisite "we never even tried because
 * there's nothing wired".
 */
public class MinterNotInstalledException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MinterNotInstalledException(String message) {
    super(message);
  }
}
