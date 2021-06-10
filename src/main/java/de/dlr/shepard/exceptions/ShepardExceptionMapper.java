package de.dlr.shepard.exceptions;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.apache.http.HttpStatus;

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
