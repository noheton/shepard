package de.dlr.shepard.spi.ai;

/**
 * Immutable response from the {@link LlmProvider}.
 *
 * <p>{@link #activityAppId()} is the appId of the {@code :AiActivity}
 * provenance node written by the provider implementation. Callers must
 * not write their own activity node — the provider handles that.
 */
public final class LlmResponse {

  private final String text;
  private final String activityAppId;
  private final int inputTokens;
  private final int outputTokens;

  private LlmResponse(Builder b) {
    this.text = b.text;
    this.activityAppId = b.activityAppId;
    this.inputTokens = b.inputTokens;
    this.outputTokens = b.outputTokens;
  }

  public String text() { return text; }
  /** appId of the written {@code :AiActivity} provenance node. */
  public String activityAppId() { return activityAppId; }
  public int inputTokens() { return inputTokens; }
  public int outputTokens() { return outputTokens; }

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    private String text = "";
    private String activityAppId = "";
    private int inputTokens = 0;
    private int outputTokens = 0;

    public Builder text(String v) { this.text = v; return this; }
    public Builder activityAppId(String v) { this.activityAppId = v; return this; }
    public Builder inputTokens(int v) { this.inputTokens = v; return this; }
    public Builder outputTokens(int v) { this.outputTokens = v; return this; }

    public LlmResponse build() { return new LlmResponse(this); }
  }
}
