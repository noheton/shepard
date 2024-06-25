package de.dlr.shepard.exceptions;

import lombok.Value;

@Value
public class ApiError {

	private final int status;
	private final String exception;
	private final String message;
}
