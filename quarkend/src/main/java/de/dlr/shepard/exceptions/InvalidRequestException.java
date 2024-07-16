package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response.Status;

/**
 * InvalidRequestException implements an exception. This exception should be
 * thrown if a required value in the Request doesn't fulfill the necessary
 * constraints. When a InvalidRequestException is thrown a Bad Request (status
 * code 400) does get sent with a message informing the client about the
 * specific error.
 **/
public class InvalidRequestException extends ShepardException {

  private static final long serialVersionUID = 8918170154141864994L;

  public InvalidRequestException() {
    super("The request is incorrect and cannot be processed", Status.BAD_REQUEST);
  }

  public InvalidRequestException(String message) {
    super(message, Status.BAD_REQUEST);
  }
}
