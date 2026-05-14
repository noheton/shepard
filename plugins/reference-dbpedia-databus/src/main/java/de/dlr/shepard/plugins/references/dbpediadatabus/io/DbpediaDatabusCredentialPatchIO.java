package de.dlr.shepard.plugins.references.dbpediadatabus.io;

/**
 * REF1c — request body for
 * {@code POST /v2/admin/references/dbpedia-databus/credential}.
 *
 * <p>The {@code clientSecret} plaintext lands here exactly once —
 * the credential service AES-GCM-encrypts it on the spot; this IO
 * instance is never persisted or logged.
 */
public final class DbpediaDatabusCredentialPatchIO {

  private String clientSecret;

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }
}
