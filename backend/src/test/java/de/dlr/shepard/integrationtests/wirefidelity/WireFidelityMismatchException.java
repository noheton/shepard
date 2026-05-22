package de.dlr.shepard.integrationtests.wirefidelity;

/**
 * Thrown when a live response diverges from its recorded v5 wire fixture.
 *
 * <p>Message lists every diverging JSON path on a separate indented line so a failing test
 * surfaces all drift at once, not just the first hit. See
 * {@link V5WireFidelityTest} for the recording / re-recording workflow.
 */
public class WireFidelityMismatchException extends AssertionError {

  public WireFidelityMismatchException(String detail) {
    super("v5 wire-fidelity mismatch:\n  " + detail);
  }
}
