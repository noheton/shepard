package de.dlr.shepard.plugins.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI1 — minimal OpenAI-compatible chat completions request body.
 *
 * <p>Wire format: {@code POST /chat/completions} per the OpenAI API
 * spec. Fields that are absent (null) are excluded from the JSON body
 * via {@code @JsonInclude(NON_NULL)} so the endpoint only sees the
 * parameters we explicitly set.
 *
 * @see OpenAiCompatClient
 * @see <a href="https://platform.openai.com/docs/api-reference/chat">OpenAI chat completions</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatRequest {

  public String model;
  public List<Message> messages;

  @JsonProperty("max_tokens")
  public Integer maxTokens;

  public Double temperature;

  /** Single message in the chat conversation. */
  public static class Message {

    public String role;
    public String content;

    public Message() {}

    public Message(String role, String content) {
      this.role = role;
      this.content = content;
    }
  }
}
