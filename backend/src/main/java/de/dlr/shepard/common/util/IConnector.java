package de.dlr.shepard.common.util;

/**
 * Interface for databases to enable connect and disconnect functions
 */
public interface IConnector {
  boolean connect();

  boolean disconnect();

  boolean alive();
}
