package de.dlr.shepard.exceptions;

public abstract class ShepardException extends Exception {

	private static final long serialVersionUID = 4144046935461575595L;

	protected ShepardException(String message) {
		super(message);
	}

	abstract int getStatusCode();

}
