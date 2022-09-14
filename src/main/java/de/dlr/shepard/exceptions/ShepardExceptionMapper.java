package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ShepardExceptionMapper implements ExceptionMapper<Exception> {

	@Override
	public Response toResponse(Exception exception) {
		int status = Status.INTERNAL_SERVER_ERROR.getStatusCode();
		if (exception instanceof ShepardException shepardException) {
			status = shepardException.getStatusCode();
		}

		return Response.status(status)
				.entity(new ApiError(status, exception.getClass().getSimpleName(), exception.getMessage())).build();
	}

}
