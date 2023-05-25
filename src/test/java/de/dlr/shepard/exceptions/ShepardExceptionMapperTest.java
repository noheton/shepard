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
		var ex = new ShepardException("test", Status.NOT_FOUND) {
			private static final long serialVersionUID = 1L;
		};
		var response = mapper.toResponse(ex);
		var expected = new ApiError(404, "", "test");

		assertEquals(404, response.getStatus());
		assertEquals(expected, response.getEntity());
	}

	@Test
	public void toResponseTest_noWebException() {
		var ex = new Exception("test") {
			private static final long serialVersionUID = 1L;
		};
		var response = mapper.toResponse(ex);
		var expected = new ApiError(500, "", "test");

		assertEquals(500, response.getStatus());
		assertEquals(expected, response.getEntity());
	}
}
