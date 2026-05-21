package de.dlr.shepard.spi.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable request to the {@link LlmProvider}.
 *
 * <p>Layers are assembled in the order the provider must present them to
 * the model, matching the injection-safe call stack defined in
 * {@code aidocs/platform/86-ai-plugin-design.md §injection-defence}:
 *
 * <ol>
 *   <li>{@link #pluginSystemPrompt()} — trusted; set by the calling plugin</li>
 *   <li>{@link #trustedContext()} — trusted; application-assembled facts</li>
 *   <li>{@link #untrustedDocuments()} — labelled with delimiter boundary</li>
 *   <li>{@link #userInstruction()} — the end-user's instruction</li>
 * </ol>
 *
 * <p>Build with {@link #builder(AiCapability)}.
 */
public final class LlmRequest {

  private final AiCapability capability;
  private final String pluginSystemPrompt;
  private final String trustedContext;
  private final List<String> untrustedDocuments;
  private final String userInstruction;
  private final int maxTokens;
  private final double temperature;

  private LlmRequest(Builder b) {
    this.capability = b.capability;
    this.pluginSystemPrompt = b.pluginSystemPrompt;
    this.trustedContext = b.trustedContext;
    this.untrustedDocuments = Collections.unmodifiableList(new ArrayList<>(b.untrustedDocuments));
    this.userInstruction = b.userInstruction;
    this.maxTokens = b.maxTokens;
    this.temperature = b.temperature;
  }

  public AiCapability capability() { return capability; }
  public String pluginSystemPrompt() { return pluginSystemPrompt; }
  public String trustedContext() { return trustedContext; }
  public List<String> untrustedDocuments() { return untrustedDocuments; }
  public String userInstruction() { return userInstruction; }
  public int maxTokens() { return maxTokens; }
  public double temperature() { return temperature; }

  public static Builder builder(AiCapability capability) {
    return new Builder(capability);
  }

  public static final class Builder {
    private final AiCapability capability;
    private String pluginSystemPrompt = "";
    private String trustedContext = "";
    private final List<String> untrustedDocuments = new ArrayList<>();
    private String userInstruction = "";
    private int maxTokens = 2048;
    private double temperature = 0.7;

    private Builder(AiCapability capability) {
      this.capability = capability;
    }

    public Builder pluginSystemPrompt(String v) { this.pluginSystemPrompt = v; return this; }
    public Builder trustedContext(String v) { this.trustedContext = v; return this; }
    public Builder addUntrustedDocument(String doc) { this.untrustedDocuments.add(doc); return this; }
    public Builder userInstruction(String v) { this.userInstruction = v; return this; }
    public Builder maxTokens(int v) { this.maxTokens = v; return this; }
    public Builder temperature(double v) { this.temperature = v; return this; }

    public LlmRequest build() { return new LlmRequest(this); }
  }
}
