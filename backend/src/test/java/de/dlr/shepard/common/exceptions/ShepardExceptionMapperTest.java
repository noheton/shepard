package de.dlr.shepard.common.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ShepardExceptionMapperTest {

  @Inject
  ShepardExceptionMapper exceptionMapper;

  @Test
  public void toResponseTest_different() {
    var ex = new ShepardException("test", Status.NOT_FOUND) {
      private static final long serialVersionUID = 1L;
    };
    var response = exceptionMapper.toResponse(ex);
    var expected = new ApiError(404, "", "test");

    assertEquals(404, response.getStatus());
    assertEquals(expected, response.getEntity());
  }

  @Test
  public void toResponseTest_noWebException() {
    var ex = new Exception("test") {
      private static final long serialVersionUID = 1L;
    };
    var response = exceptionMapper.toResponse(ex);
    var expected = new ApiError(500, "", "test");

    assertEquals(500, response.getStatus());
    assertEquals(expected, response.getEntity());
  }
}
