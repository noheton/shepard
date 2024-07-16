package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response.Status;

public class ShepardProcessingException extends ShepardException {

  private static final long serialVersionUID = 1125700124551787161L;

  public ShepardProcessingException(String message) {
    super(message, Status.INTERNAL_SERVER_ERROR);
  }
}
