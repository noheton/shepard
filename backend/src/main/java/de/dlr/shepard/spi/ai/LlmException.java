package de.dlr.shepard.spi.ai;

/** Thrown by {@link LlmProvider#complete} on any non-recoverable error. */
public class LlmException extends RuntimeException {

  public LlmException(String message) {
    super(message);
  }

  public LlmException(String message, Throwable cause) {
    super(message, cause);
  }
}
