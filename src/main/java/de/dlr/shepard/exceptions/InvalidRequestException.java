package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response.Status;

/**
 * The specified request cannot be processed
 **/
public class InvalidRequestException extends ShepardException {

	private static final long serialVersionUID = 108407029964680620L;

	public InvalidRequestException() {
		super("The specified request cannot be processed");
	}

	public InvalidRequestException(String message) {
		super(message);
	}

	@Override
	int getStatusCode() {
		return Status.BAD_REQUEST.getStatusCode();
	}

}
