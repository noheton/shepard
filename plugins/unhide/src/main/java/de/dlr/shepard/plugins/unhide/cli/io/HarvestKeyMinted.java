package de.dlr.shepard.plugins.unhide.cli.io;

import java.util.Date;

/**
 * CLI mirror of the backend's {@code HarvestKeyMintedIO} —
 * response of {@code POST /v2/admin/unhide/harvest-key/rotate}.
 *
 * <p>PM1d — relocated from {@code de.dlr.shepard.cli.io} alongside
 * the rest of the {@code unhide} CLI subcommand group.
 */
public final class HarvestKeyMinted {

  private String harvestApiKey;
  private String fingerprint;
  private Date mintedAt;
  private String warning;

  public String getHarvestApiKey() {
    return harvestApiKey;
  }

  public void setHarvestApiKey(String harvestApiKey) {
    this.harvestApiKey = harvestApiKey;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public Date getMintedAt() {
    return mintedAt;
  }

  public void setMintedAt(Date mintedAt) {
    this.mintedAt = mintedAt;
  }

  public String getWarning() {
    return warning;
  }

  public void setWarning(String warning) {
    this.warning = warning;
  }
}
