package de.dlr.shepard.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/**
 * FS1a — pins the RFC 7807 envelope shape that
 * {@link StorageNotInstalledExceptionMapper} surfaces when a caller
 * hits a file-payload endpoint and the registry has no active
 * adapter (operator hasn't picked one, or the configured id doesn't
 * match any installed bean).
 */
class StorageNotInstalledExceptionMapperTest {

  @Test
  void mapsToServiceUnavailableProblemJson() {
    StorageNotInstalledExceptionMapper mapper = new StorageNotInstalledExceptionMapper();
    StorageNotInstalledException ex = new StorageNotInstalledException(
      "No file-payload storage adapter is active. Set shepard.storage.provider to one of [gridfs]."
    );

    Response response = mapper.toResponse(ex);
    assertEquals(503, response.getStatus());
    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());

    Object body = response.getEntity();
    assertNotNull(body);
    assertTrue(body instanceof ProblemJson, "expected ProblemJson body, got " + body.getClass());
    ProblemJson problem = (ProblemJson) body;
    assertEquals(StorageNotInstalledExceptionMapper.TYPE_URL, problem.type());
    assertEquals(StorageNotInstalledExceptionMapper.TITLE, problem.title());
    assertEquals(503, problem.status());
    assertNotNull(problem.detail());
    assertTrue(problem.detail().contains("shepard.storage.provider"));
  }
}
