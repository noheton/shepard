package de.dlr.shepard.storage;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * FS1a — maps {@link StorageNotInstalledException} to RFC 7807
 * {@code storage.provider.not-installed} (HTTP 503).
 *
 * <p>Mirror of the inline {@code MinterNotInstalledException}
 * handler in {@code PublishRest} ({@code aidocs/66} KIP1h),
 * factored out into a dedicated mapper because the file-payload
 * surface is split across three endpoints (POST / GET / DELETE
 * payload) — duplicating the mapping inline three times would rot
 * fast. The publish path has exactly one entry point so the inline
 * shape there stays tidy; here the mapper is the right cost shape.
 *
 * <p>Operator-actionable message: the exception's own
 * {@link Throwable#getMessage()} carries the list of discovered
 * adapters + the recommended next step (set
 * {@code shepard.storage.provider} or install a plugin), so the
 * {@code detail} field is already operator-readable.
 *
 * <p>The H4 fallback (ShepardExceptionMapper) would otherwise map
 * the bare {@code RuntimeException} to 500 + a generic-detail
 * envelope — not what we want for a config-missing signal. Hence
 * the dedicated mapper.
 */
@Provider
public class StorageNotInstalledExceptionMapper implements ExceptionMapper<StorageNotInstalledException> {

  static final String TYPE_URL = "https://noheton.github.io/shepard/errors/storage.provider.not-installed";
  static final String TITLE = "No storage provider installed";

  @Override
  public Response toResponse(StorageNotInstalledException ex) {
    Log.warnf("StorageNotInstalledException: %s", ex.getMessage());
    ProblemJson body = new ProblemJson(
      TYPE_URL,
      TITLE,
      Status.SERVICE_UNAVAILABLE.getStatusCode(),
      ex.getMessage(),
      null
    );
    return Response.status(Status.SERVICE_UNAVAILABLE)
      .entity(body)
      .type(Constants.APPLICATION_PROBLEM_JSON)
      .build();
  }
}
