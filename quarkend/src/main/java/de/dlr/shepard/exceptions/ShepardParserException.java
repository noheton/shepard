package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response.Status;

public class ShepardParserException extends ShepardException {

	private static final long serialVersionUID = 2L;

	public ShepardParserException() {
		super("A parser error occurred", Status.BAD_REQUEST);
	}

	public ShepardParserException(String message) {
		super(message, Status.BAD_REQUEST);
	}

}
