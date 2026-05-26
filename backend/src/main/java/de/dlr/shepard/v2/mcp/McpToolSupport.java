package de.dlr.shepard.v2.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.v2.ai.AiActivityType;
import de.dlr.shepard.v2.ai.AiProvenanceCapture;
import io.quarkiverse.mcp.server.McpException;
import io.quarkus.logging.Log;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import java.util.concurrent.Callable;

/**
 * Shared helpers for the v2 MCP tool surface so every tool produces the same
 * caller-friendly error shape — instead of letting an uncaught
 * {@link NullPointerException} or service-layer exception bubble up as a
 * generic JSON-RPC -32603 "Internal error" that an agent cannot recover from.
 *
 * <p>Two seams matter:
 *
 * <ul>
 *   <li>{@link #resolveOfType} — resolve an appId and verify the matched
 *       Neo4j node carries the expected label. Wrong type → -32602 Invalid
 *       Params with a message naming both the expected and the actual type
 *       (so the agent can self-correct).</li>
 *   <li>{@link #run} — wrap a tool body so any caller-facing exception
 *       (NotFoundException, NotAuthorizedException, ForbiddenException,
 *       IllegalArgumentException, McpException itself) is mapped to a clean
 *       JSON-RPC error code with the original message, while genuinely
 *       unexpected errors (NPE, RuntimeException) are logged and mapped to
 *       -32603 with the exception class name so debugging is possible
 *       without leaking stack traces.</li>
 * </ul>
 *
 * <p>JSON-RPC 2.0 error codes used here:
 * <ul>
 *   <li>{@code -32602} — Invalid params. Anything the caller can fix by
 *       sending different arguments (wrong appId, missing required field,
 *       wrong container type).</li>
 *   <li>{@code -32603} — Internal error. Reserved for genuine server bugs.</li>
 *   <li>{@code -32001} — Custom: authentication required.</li>
 *   <li>{@code -32002} — Custom: permission denied.</li>
 * </ul>
 *
 * <p><b>TPL9 AI provenance</b> ({@code aidocs/platform/89}): when the
 * caller includes an {@code X-AI-Agent} header, {@link #run} records a
 * supplementary {@code :Activity} node with
 * {@code actionKind = "AI_ACTION"} via {@link AiProvenanceCapture}.
 * This is the EU AI Act Article 50 transparency hook for inbound
 * AI-agent MCP calls. Capture is best-effort — a write failure does
 * not affect the tool call outcome.
 */
@ApplicationScoped
public class McpToolSupport {

  /** TPL9 header: identifies the calling AI agent or framework. */
  static final String HEADER_AI_AGENT = "X-AI-Agent";

  /** TPL9 header: model identifier reported by the calling agent. */
  static final String HEADER_AI_MODEL = "X-AI-Model";

  /** TPL9 header: SHA-256 of the user instruction (privacy default). */
  static final String HEADER_AI_PROMPT_HASH = "X-AI-Prompt-Hash";

  /** TPL9 header: {@link AiActivityType} value for this tool call. */
  static final String HEADER_AI_ACTIVITY_TYPE = "X-AI-Activity-Type";

  static final int INVALID_PARAMS = -32602;
  static final int INTERNAL_ERROR = -32603;
  static final int AUTH_REQUIRED = -32001;
  static final int FORBIDDEN = -32002;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  AiProvenanceCapture aiProvenanceCapture;

  @Inject
  Instance<CurrentVertxRequest> currentVertxRequest;

  /**
   * Resolve {@code appId} and assert the matched node carries
   * {@code expectedLabel}. Returns the OGM Long on success.
   *
   * <p>Throws {@link McpException} with code -32602 on:
   * <ul>
   *   <li>null/blank appId — no point hitting Neo4j with garbage</li>
   *   <li>no node with that appId — tells the agent "this is not an id we
   *       have" rather than the upstream NotFoundException leaking through</li>
   *   <li>wrong label — tells the agent "you gave me a {@code XContainer}
   *       but this tool needs a {@code expectedLabel}", which is the
   *       single most-common operator/agent mistake</li>
   * </ul>
   *
   * @param appId          UUID v7 appId to resolve
   * @param expectedLabel  Neo4j label the matched node must carry (e.g.
   *                       {@code "TimeseriesContainer"}, {@code "Collection"})
   * @param paramName      caller-visible name of the parameter (used in
   *                       diagnostics so the agent knows which field to fix)
   * @return OGM Long id for the resolved node
   */
  long resolveOfType(String appId, String expectedLabel, String paramName) {
    if (appId == null || appId.isBlank()) {
      throw invalidParams(paramName + " is required (UUID v7 appId).");
    }
    EntityIdResolver.LabeledResolution res;
    try {
      res = entityIdResolver.resolveWithLabels(appId);
    } catch (NotFoundException e) {
      throw invalidParams("No entity found for " + paramName + "=" + appId);
    }
    if (!res.hasLabel(expectedLabel)) {
      throw invalidParams(
        "Wrong type for " + paramName + "=" + appId +
        ": expected a " + expectedLabel +
        (res.labels().isEmpty() ? "" : " but found " + res.labelsString()) +
        ". " + hintFor(expectedLabel)
      );
    }
    return res.ogmId();
  }

  /** Build an INVALID_PARAMS McpException. */
  static McpException invalidParams(String message) {
    return new McpException(message, INVALID_PARAMS);
  }

  /**
   * Wrap a tool body so caller-facing exceptions become clean MCP errors and
   * unexpected exceptions become a logged INTERNAL_ERROR with the exception
   * class name preserved in the message.
   *
   * <p><b>TPL9:</b> on successful execution, inspects the current Vert.x
   * routing context for the {@code X-AI-Agent} header. When present,
   * delegates to {@link AiProvenanceCapture#record} to stamp a supplementary
   * {@code :Activity} node with {@code actionKind = "AI_ACTION"} and
   * f(ai)²r metadata. Capture is best-effort — any failure is swallowed
   * so the tool result is never blocked by a provenance write.
   */
  <T> T run(String toolName, Callable<T> body) {
    try {
      T result = body.call();
      // TPL9: record AI provenance when X-AI-Agent header is present.
      captureAiProvenance(toolName);
      return result;
    } catch (McpException e) {
      throw e;
    } catch (NotAuthorizedException e) {
      throw new McpException("Authentication required: " + safeMsg(e), AUTH_REQUIRED);
    } catch (ForbiddenException | InvalidAuthException e) {
      throw new McpException("Permission denied: " + safeMsg(e), FORBIDDEN);
    } catch (NotFoundException | IllegalArgumentException e) {
      // Caller-fixable: bad id, wrong shape, etc.
      throw new McpException(safeMsg(e), INVALID_PARAMS);
    } catch (RuntimeException e) {
      Log.errorf(e, "MCP tool %s failed with unexpected exception", toolName);
      throw new McpException(
        toolName + " failed (" + e.getClass().getSimpleName() + "): " + safeMsg(e),
        INTERNAL_ERROR
      );
    } catch (Exception e) {
      Log.errorf(e, "MCP tool %s failed with checked exception", toolName);
      throw new McpException(
        toolName + " failed (" + e.getClass().getSimpleName() + "): " + safeMsg(e),
        INTERNAL_ERROR
      );
    }
  }

  /**
   * TPL9: inspect the current routing context for {@code X-AI-Agent}.
   * When present, record the inbound AI action via {@link AiProvenanceCapture}.
   * Never throws — provenance capture is observability, not contract.
   */
  private void captureAiProvenance(String toolName) {
    try {
      RoutingContext rc = currentRoutingContext();
      if (rc == null) return;
      String agentId = rc.request().getHeader(HEADER_AI_AGENT);
      if (agentId == null || agentId.isBlank()) return;

      String modelId = rc.request().getHeader(HEADER_AI_MODEL);
      String promptHash = rc.request().getHeader(HEADER_AI_PROMPT_HASH);
      AiActivityType activityType =
        AiActivityType.fromHeader(rc.request().getHeader(HEADER_AI_ACTIVITY_TYPE));

      aiProvenanceCapture.record(activityType, modelId, promptHash, null, null, toolName, agentId);
    } catch (RuntimeException e) {
      Log.debugf(e, "TPL9: AI provenance capture skipped for tool=%s", toolName);
    }
  }

  private RoutingContext currentRoutingContext() {
    try {
      CurrentVertxRequest cvr = currentVertxRequest.get();
      return cvr == null ? null : cvr.getCurrent();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Serialise to JSON or return a structured error blob — never null. */
  String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      Log.errorf(e, "MCP JSON serialisation failed");
      throw new McpException("Response serialisation failed: " + safeMsg(e), INTERNAL_ERROR);
    }
  }

  private static String safeMsg(Throwable e) {
    String m = e.getMessage();
    return m == null ? e.getClass().getSimpleName() : m;
  }

  /**
   * Short hint about where the right kind of appId lives in the API surface.
   * Helps an LLM self-correct without needing to re-read the tool descriptions.
   */
  private static String hintFor(String expectedLabel) {
    return switch (expectedLabel) {
      case "TimeseriesContainer" ->
        "Get a TimeseriesContainer appId from `get_data_object → containers.timeseries[].containerAppId`.";
      case "FileContainer" ->
        "Get a FileContainer appId from `get_data_object → containers.files[].containerAppId`.";
      case "StructuredDataContainer" ->
        "Get a StructuredDataContainer appId from `get_data_object → containers.structuredData[].containerAppId`.";
      case "Collection" ->
        "Get a Collection appId from `list_collections`.";
      case "DataObject" ->
        "Get a DataObject appId from `list_data_objects` or any *Summaries entry on `get_data_object`.";
      default -> "";
    };
  }
}
