package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ShepardExceptionMapper implements ExceptionMapper<ShepardException> {

	@Override
	public Response toResponse(ShepardException exception) {
		var status = exception.getStatusCode();

		return Response.status(status)
				.entity(new ApiError(status, exception.getClass().getSimpleName(), exception.getMessage())).build();
	}

}
