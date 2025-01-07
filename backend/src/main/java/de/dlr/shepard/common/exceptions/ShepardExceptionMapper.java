package de.dlr.shepard.common.exceptions;

import io.quarkus.logging.Log;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ShepardExceptionMapper implements ExceptionMapper<Exception> {

  @Override
  public Response toResponse(Exception exception) {
    int status = Status.INTERNAL_SERVER_ERROR.getStatusCode();
    if (exception instanceof WebApplicationException webException) {
      status = webException.getResponse().getStatus();
    }

    Log.error(exception.getMessage(), exception);

    return Response.status(status)
      .entity(new ApiError(status, exception.getClass().getSimpleName(), exception.getMessage()))
      .type(MediaType.APPLICATION_JSON)
      .build();
  }
}
