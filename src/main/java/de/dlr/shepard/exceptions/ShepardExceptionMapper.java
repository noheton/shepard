package de.dlr.shepard.exceptions;

import org.apache.http.HttpStatus;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ShepardExceptionMapper implements ExceptionMapper<ShepardException> {

	@Override
	public Response toResponse(ShepardException exception) {
		int status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
		if (exception instanceof InvalidBodyException) {
			status = HttpStatus.SC_BAD_REQUEST;
		} else if (exception instanceof InvalidPathException) {
			status = HttpStatus.SC_NOT_FOUND;
		}

		return Response.status(status)
				.entity(new ApiError(status, exception.getClass().getSimpleName(), exception.getMessage())).build();
	}

}
