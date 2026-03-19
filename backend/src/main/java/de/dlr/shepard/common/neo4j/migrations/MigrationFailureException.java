package de.dlr.shepard.common.neo4j.migrations;

public class MigrationFailureException extends RuntimeException {

  public MigrationFailureException(Exception cause) {
    super(cause);
  }

  public MigrationFailureException(String message) {
    super(message);
  }
}
