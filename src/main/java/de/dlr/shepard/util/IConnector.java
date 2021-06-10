package de.dlr.shepard.util;

/**
 * Interface for databases to enable connect and disconnect functions
 */
public interface IConnector {

	boolean connect();

	boolean disconnect();
}
