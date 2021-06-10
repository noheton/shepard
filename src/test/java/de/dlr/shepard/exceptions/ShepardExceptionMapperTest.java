package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import de.dlr.shepard.BaseTestCase;

public class ShepardExceptionMapperTest extends BaseTestCase {

	@InjectMocks
	private ShepardExceptionMapper mapper;

	@Test
	public void toResponseTest_invalidPath() {
		var response = mapper.toResponse(new InvalidPathException("test"));
		var error = new ApiError(404, "InvalidPathException", "test");

		assertEquals(404, response.getStatus());
		assertEquals(error, response.getEntity());
	}

	@Test
	public void toResponseTest_invalidBody() {
		var response = mapper.toResponse(new InvalidBodyException("test"));
		var error = new ApiError(400, "InvalidBodyException", "test");

		assertEquals(400, response.getStatus());
		assertEquals(error, response.getEntity());
	}

	@Test
	public void toResponseTest_different() {
		var ex = new ShepardException("test") {
			private static final long serialVersionUID = 1L;
		};
		var response = mapper.toResponse(ex);
		var error = new ApiError(500, "", "test");

		assertEquals(500, response.getStatus());
		assertEquals(error, response.getEntity());
	}
}
