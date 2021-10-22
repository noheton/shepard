package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response.Status;

public class InvalidPathException extends ShepardException {

	private static final long serialVersionUID = 2735916387225681093L;

	public InvalidPathException() {
		super("The specified path does not exist");
	}

	public InvalidPathException(String message) {
		super(message);
	}

	@Override
	int getStatusCode() {
		return Status.NOT_FOUND.getStatusCode();
	}

}
