package de.dlr.shepard.common.exceptions;

import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;

/**
 * Request-scoped trace identifier used as the {@code instance} field of
 * {@link ProblemJson} responses and as a correlation id in error logs.
 *
 * <p>{@link ShepardExceptionMapper} prefers an inbound {@code X-Request-Id}
 * header when present; this bean is the fallback for clients that don't
 * set one. The bean is request-scoped, so the same id is reused for every
 * problem response (and every log line) emitted during a single HTTP call.
 */
@RequestScoped
public class TraceIdProvider {

  private final String traceId = UUID.randomUUID().toString();

  public String getTraceId() {
    return traceId;
  }
}
