package de.dlr.shepard.plugins.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI1 — minimal OpenAI-compatible chat completions response body.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} so unknown
 * fields from the provider (e.g. {@code system_fingerprint},
 * {@code service_tier}) are silently dropped — forward-compatible with
 * provider-specific extensions.
 *
 * @see OpenAiCompatClient
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiChatResponse {

  public List<Choice> choices;
  public Usage usage;
  public String model;

  /** A single completion choice. The first element is the primary reply. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Choice {

    public Message message;

    @JsonProperty("finish_reason")
    public String finishReason;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {

      public String role;
      public String content;
    }
  }

  /** Token usage counters reported by the model. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Usage {

    @JsonProperty("prompt_tokens")
    public int promptTokens;

    @JsonProperty("completion_tokens")
    public int completionTokens;

    @JsonProperty("total_tokens")
    public int totalTokens;
  }

  /** Convenience accessor — returns the text of the first choice, or {@code null}. */
  public String firstChoiceContent() {
    if (choices == null || choices.isEmpty()) return null;
    Choice first = choices.get(0);
    if (first == null || first.message == null) return null;
    return first.message.content;
  }
}
