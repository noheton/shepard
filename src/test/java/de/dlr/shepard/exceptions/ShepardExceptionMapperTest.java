package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import de.dlr.shepard.BaseTestCase;
import jakarta.ws.rs.core.Response.Status;

public class ShepardExceptionMapperTest extends BaseTestCase {

	@InjectMocks
	private ShepardExceptionMapper mapper;

	@Test
	public void toResponseTest_different() {
		var ex = new ShepardException("test", Status.INTERNAL_SERVER_ERROR) {
			private static final long serialVersionUID = 1L;
		};
		var response = mapper.toResponse(ex);
		var error = new ApiError(500, "", "test");

		assertEquals(500, response.getStatus());
		assertEquals(error, response.getEntity());
	}
}
