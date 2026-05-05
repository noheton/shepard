package de.dlr.shepard.common.neo4j;

public class ConnectionWaitTimeoutException extends RuntimeException {

  public ConnectionWaitTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
