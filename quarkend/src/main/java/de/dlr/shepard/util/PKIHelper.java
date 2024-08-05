package de.dlr.shepard.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class PKIHelper {

  private static final String RSA = "RSA";

  private Path keysDir = Paths.get(System.getProperty("user.home"), ".shepard/keys");
  private Path pubKey = Paths.get(keysDir.toString(), "public.key");
  private Path privKey = Paths.get(keysDir.toString(), "private.key");

  @Getter
  private PublicKey publicKey;

  @Getter
  private PrivateKey privateKey;

  public void init() {
    generateKeyPairIfNecessary();

    try {
      publicKey = importPublicKey();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
      log.error("Exception while reading public key specification: {}", e);
    }

    try {
      privateKey = importPrivateKey();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
      log.error("Exception while reading private key specification: {}", e);
    }
  }

  private void generateKeyPairIfNecessary() {
    if (Files.isRegularFile(keysDir)) {
      log.error("keys directory is a file, cannot create keys");
      return;
    }
    if (Files.notExists(keysDir)) {
      log.info("keys directory does not exist, creating...");
      try {
        Files.createDirectories(keysDir);
      } catch (IOException e) {
        log.error("Error while generating keys directory: {}", e.toString());
        return;
      }
    }
    if (!Files.isReadable(keysDir) || !Files.isWritable(keysDir)) {
      log.error("insufficient permissions for the keys directory");
      return;
    }

    if (Files.exists(pubKey) && Files.isRegularFile(pubKey) && Files.exists(privKey) && Files.isRegularFile(privKey)) {
      log.info("keys found, importing...");
      return;
    }
    log.info("No keys available. Generating...");
    try {
      generateKeyPair();
    } catch (NoSuchAlgorithmException | IOException e) {
      log.error("Error while generating keys: {}", e.toString());
    }
  }

  private PublicKey importPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    byte[] key;
    try (var fis = Files.newInputStream(pubKey)) {
      key = fis.readAllBytes();
    }

    var keyFactory = KeyFactory.getInstance(RSA);
    var spec = new X509EncodedKeySpec(key);
    return keyFactory.generatePublic(spec);
  }

  private PrivateKey importPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    byte[] key;
    try (var fis = Files.newInputStream(privKey)) {
      key = fis.readAllBytes();
    }

    var keyFactory = KeyFactory.getInstance(RSA);
    var spec = new PKCS8EncodedKeySpec(key);
    return keyFactory.generatePrivate(spec);
  }

  private void generateKeyPair() throws NoSuchAlgorithmException, IOException {
    var kpg = KeyPairGenerator.getInstance(RSA);
    kpg.initialize(2048);
    var kp = kpg.generateKeyPair();

    try (var pubFos = Files.newOutputStream(pubKey); var privFos = Files.newOutputStream(privKey);) {
      pubFos.write(kp.getPublic().getEncoded());
      privFos.write(kp.getPrivate().getEncoded());
    }
  }
}
