package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response.Status;

/**
 * InvalidBodyException implements an exception. This exception should be thrown
 * if a value in the JSON Request Body doesn't fulfill the necessary constraints
 * of an attribute. When a InvalidBodyException is thrown a Bad Request (status
 * code 400) does get sent with a message informing the client about the
 * specific error.
 **/
public class InvalidBodyException extends ShepardException {

  private static final long serialVersionUID = 8918170154141864994L;
  private static final Status status = Status.BAD_REQUEST;

  public InvalidBodyException() {
    super("Some of the values provided in the JSON Body are incorrect", status);
  }

  public InvalidBodyException(String message) {
    super(message, status);
  }

  public InvalidBodyException(String format, Object... args) {
    super(String.format(format, args), status);
  }
}
