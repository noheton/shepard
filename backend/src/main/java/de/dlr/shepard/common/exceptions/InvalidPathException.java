package de.dlr.shepard.common.exceptions;

import jakarta.ws.rs.core.Response.Status;

public class InvalidPathException extends ShepardException {

  private static final long serialVersionUID = 2735916387225681093L;

  public InvalidPathException() {
    super("The specified path does not exist", Status.NOT_FOUND);
  }

  public InvalidPathException(String message) {
    super(message, Status.NOT_FOUND);
  }
}
