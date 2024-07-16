package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response.Status;

public class InvalidAuthException extends ShepardException {

  private static final long serialVersionUID = 8796971092537544749L;

  public InvalidAuthException() {
    super("Invalid authentication or authorization", Status.FORBIDDEN);
  }

  public InvalidAuthException(String message) {
    super(message, Status.FORBIDDEN);
  }
}
