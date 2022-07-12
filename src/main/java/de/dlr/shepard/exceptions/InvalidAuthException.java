package de.dlr.shepard.exceptions;

public class InvalidAuthException extends ShepardException {

	private static final long serialVersionUID = 8796971092537544749L;

	public InvalidAuthException() {
		super("Invalid authentication or authorization");
	}

	public InvalidAuthException(String message) {
		super(message);
	}

	@Override
	int getStatusCode() {
		return 403;
	}

}
