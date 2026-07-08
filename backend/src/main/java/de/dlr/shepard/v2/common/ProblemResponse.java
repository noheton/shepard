package de.dlr.shepard.v2.common;

import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** Shared RFC 7807 problem-response factory; replaces per-class private {@code problem()} helpers. */
public final class ProblemResponse {

  private static final String PROBLEM_JSON = "application/problem+json";

  private ProblemResponse() {}

  public static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type(PROBLEM_JSON)
        .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }

  public static Response problem(Response.Status status, String type, String title, String detail) {
    return problem(type, title, status, detail);
  }

  public static Response problem(String type, String title, int status, String detail) {
    return Response.status(status).type(PROBLEM_JSON)
        .entity(new ProblemJson(type, title, status, detail, null)).build();
  }

  public static Response problem(String type, String title, Response.Status status, String detail,
      Map<String, Object> ext) {
    return Response.status(status).type(PROBLEM_JSON)
        .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null, ext)).build();
  }

  public static Response problem(String type, String title, int status, String detail,
      Map<String, Object> ext) {
    return Response.status(status).type(PROBLEM_JSON)
        .entity(new ProblemJson(type, title, status, detail, null, ext)).build();
  }

  /**
   * Returns a {@code ResponseBuilder} pre-seeded with RFC 7807 content-type and entity,
   * so callers can chain extra headers (e.g. {@code Location}, {@code Retry-After}) before
   * calling {@code .build()}.
   */
  public static Response.ResponseBuilder problemBuilder(
      String type, String title, int status, String detail) {
    return Response.status(status).type(PROBLEM_JSON)
        .entity(new ProblemJson(type, title, status, detail, null));
  }

  /** Derives {@code type} and {@code title} from the HTTP status. */
  public static Response problem(Response.Status status, String detail) {
    String type = switch (status) {
      case UNAUTHORIZED -> "urn:shepard:error:unauthorized";
      case FORBIDDEN -> "urn:shepard:error:forbidden";
      case BAD_REQUEST -> "urn:shepard:error:validation";
      case NOT_FOUND -> "urn:shepard:error:not-found";
      case CONFLICT -> "urn:shepard:error:conflict";
      case SERVICE_UNAVAILABLE -> "urn:shepard:error:service-unavailable";
      default -> "urn:shepard:error:internal";
    };
    return Response.status(status).type(PROBLEM_JSON)
        .entity(new ProblemJson(type, status.getReasonPhrase(), status.getStatusCode(), detail, null))
        .build();
  }
}
