package de.dlr.shepard.plugins.storage.s3.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.storage.s3.cli.io.S3CredentialSet;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin storage s3 set-credentials [accessKeyId]} — FS1b.
 *
 * <p>Sets (or rotates) the S3 credential pair. The accessKeyId may be
 * passed as a positional argument; the secretKey is always read from
 * stdin/tty (never on the command line, so it doesn't appear in
 * shell history or {@code ps} output).
 *
 * <p>The plaintext is <b>never</b> passed on the command line.
 * The server-side admin endpoint encrypts the value into
 * {@code :S3StorageConfig.secretAccessKeyCipher} (AES-GCM keyed off
 * the instance id) and stores the SHA-256 hash for fingerprint
 * display.
 */
@Command(
  name = "set-credentials",
  mixinStandardHelpOptions = true,
  description = "Set or rotate the S3 access credentials. " +
  "secretKey is read from stdin/terminal — never on the command line."
)
public class S3SetCredentialsCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    description = "AWS Access Key ID. If omitted, read interactively from stdin/tty."
  )
  String accessKeyId;

  /**
   * Read the access key id from stdin. Visible to tests.
   */
  String readAccessKeyId() {
    Console console = System.console();
    if (console != null) {
      err().println("Enter Access Key ID:");
      char[] chars = console.readLine().toCharArray();
      if (chars == null || chars.length == 0) {
        throw new AdminCliException("No Access Key ID provided.");
      }
      return new String(chars).trim();
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      if (line == null) {
        throw new AdminCliException("stdin closed before an Access Key ID was read.");
      }
      return line.trim();
    } catch (IOException e) {
      throw new AdminCliException("Could not read Access Key ID from stdin: " + e.getMessage(), e);
    }
  }

  /**
   * Read the secret key. Visible to tests so they can inject a stub.
   */
  String readSecretKey() {
    Console console = System.console();
    if (console != null) {
      err().println("Enter Secret Access Key (input not echoed):");
      char[] chars = console.readPassword();
      if (chars == null) {
        throw new AdminCliException("No Secret Access Key provided on the terminal.");
      }
      return new String(chars).trim();
    }
    // Non-tty: read one line from stdin.
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      if (line == null) {
        throw new AdminCliException("stdin closed before a Secret Access Key was read.");
      }
      return line.trim();
    } catch (IOException e) {
      throw new AdminCliException("Could not read Secret Access Key from stdin: " + e.getMessage(), e);
    }
  }

  @Override
  protected Integer run() {
    String keyId = (accessKeyId != null && !accessKeyId.isBlank()) ? accessKeyId : readAccessKeyId();
    String secretKey = readSecretKey();

    if (keyId == null || keyId.isBlank()) {
      throw new AdminCliException("Refusing to set a blank Access Key ID.");
    }
    if (secretKey == null || secretKey.isBlank()) {
      throw new AdminCliException("Refusing to set a blank Secret Access Key.");
    }

    Map<String, String> body = new HashMap<>();
    body.put("accessKeyId", keyId);
    body.put("secretKey", secretKey);

    S3CredentialSet response = buildClient()
      .postJson(S3AdminPaths.CREDENTIAL, body, new TypeReference<S3CredentialSet>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(response));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise credential-set response: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "S3 credential stored. fingerprint=" +
      (response.getSecretKeyFingerprint() == null ? "(absent)" : response.getSecretKeyFingerprint()) +
      ", secretKeySet=" + response.isSecretKeySet()
    );
    return 0;
  }
}
