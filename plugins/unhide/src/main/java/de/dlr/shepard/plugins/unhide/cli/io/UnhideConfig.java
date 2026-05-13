package de.dlr.shepard.plugins.unhide.cli.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

/**
 * CLI mirror of the backend's {@code UnhideConfigIO} JSON shape —
 * deserialises the response from {@code GET /v2/admin/unhide/config}
 * and the PATCH / revoke endpoints.
 *
 * <p>Read-side only; the CLI sends merge-patch requests as raw
 * {@code Map<String, Object>} bodies (the absent / null distinction
 * is hard to model on a Jackson POJO without {@code @JsonSetter}
 * tracking we don't need on the CLI side).
 *
 * <p>PM1d — relocated from {@code de.dlr.shepard.cli.io} alongside
 * the rest of the {@code unhide} CLI subcommand group, which moved
 * from the in-tree {@code cli/} module to {@code plugins/unhide/}
 * once the {@code AdminCliCommandProvider} SPI shipped.
 */
public final class UnhideConfig {

  private boolean enabled;
  private boolean feedPublic;
  private String contactEmail;
  private Date harvestApiKeyMintedAt;
  private String harvestApiKeyFingerprint;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isFeedPublic() {
    return feedPublic;
  }

  @JsonProperty("feedPublic")
  public void setFeedPublic(boolean feedPublic) {
    this.feedPublic = feedPublic;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public void setContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
  }

  public Date getHarvestApiKeyMintedAt() {
    return harvestApiKeyMintedAt;
  }

  public void setHarvestApiKeyMintedAt(Date harvestApiKeyMintedAt) {
    this.harvestApiKeyMintedAt = harvestApiKeyMintedAt;
  }

  public String getHarvestApiKeyFingerprint() {
    return harvestApiKeyFingerprint;
  }

  public void setHarvestApiKeyFingerprint(String harvestApiKeyFingerprint) {
    this.harvestApiKeyFingerprint = harvestApiKeyFingerprint;
  }
}
