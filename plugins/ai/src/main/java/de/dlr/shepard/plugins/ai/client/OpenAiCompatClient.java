package de.dlr.shepard.plugins.ai.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * AI1 — MicroProfile REST client interface for OpenAI-compatible endpoints.
 *
 * <p>Intentionally has no {@code @RegisterRestClient} base URI — the
 * endpoint URL comes from the per-capability
 * {@code :AiCapabilityConfig} node and is injected at call time via
 * {@code RestClientBuilder.newBuilder().baseUri(...).build(OpenAiCompatClient.class)}.
 * This allows the URL to be changed at runtime without a restart.
 *
 * <p>The {@code @RegisterRestClient} annotation is included so
 * Quarkus's build-time CDI scanner recognises this as a REST client
 * interface (required for {@code RestClientBuilder} to work in dev
 * mode). The {@code configKey} is never used for injection — it exists
 * only to satisfy the annotation requirement.
 */
@RegisterRestClient(configKey = "ai-openai-compat")
@Path("/")
public interface OpenAiCompatClient {

  /**
   * Send a chat completions request to the configured OpenAI-compatible
   * endpoint.
   *
   * @param bearerToken {@code "Bearer <api-key>"} authorization header value
   * @param request     the chat completions request body
   * @return the model's response
   */
  @POST
  @Path("/chat/completions")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  OpenAiChatResponse chatCompletions(
    @HeaderParam("Authorization") String bearerToken,
    OpenAiChatRequest request
  );
}
