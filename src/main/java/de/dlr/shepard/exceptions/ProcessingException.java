package de.dlr.shepard.exceptions;

public class ProcessingException extends ShepardException {

	private static final long serialVersionUID = 1125700124551787161L;

	public ProcessingException(String message) {
		super(message);
	}

	@Override
	int getStatusCode() {
		return 500;
	}

}
