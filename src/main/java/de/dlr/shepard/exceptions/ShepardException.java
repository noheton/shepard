package de.dlr.shepard.exceptions;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

public abstract class ShepardException extends WebApplicationException {

	private static final long serialVersionUID = 4144046935461575595L;

	protected ShepardException(String message, Status status) {
		super(message, status);
	}

}
