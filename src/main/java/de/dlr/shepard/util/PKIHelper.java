package de.dlr.shepard.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PKIHelper {

	public static final String PUBLIC = "public.key";
	public static final String PRIVATE = "private.key";

	private final String RSA = "RSA";

	private File keysDir = new File(System.getProperty("user.home"), ".shepard/keys");

	@Getter
	private PublicKey publicKey;

	@Getter
	private PrivateKey privateKey;

	public void init() {
		generateKeyPairIfNecessary();

		try {
			publicKey = importPublicKey();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
			log.fatal("Exception while reading public key specification: {}", e);
		}

		try {
			privateKey = importPrivateKey();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
			log.fatal("Exception while reading private key specification: {}", e.toString());
		}
	}

	private void generateKeyPairIfNecessary() {
		if (keysDir.exists() && keysDir.isFile()) {
			log.fatal("keys directory is a file, cannot create keys");
			return;
		} else if (!keysDir.exists()) {
			log.info("keys directory does not exist, creating...");
			keysDir.mkdirs();
		}

		var pubKey = new File(keysDir, "public.key");
		var privKey = new File(keysDir, "public.key");
		if (pubKey.exists() && !pubKey.isDirectory() && privKey.exists() && !privKey.isDirectory()) {
			log.info("keys found, importing...");
			return;
		}
		log.info("No keys available. Generating...");
		try {
			generateKeyPair();
		} catch (NoSuchAlgorithmException | IOException e) {
			log.fatal("Error while generating keys: {}", e.toString());
		}
	}

	private PublicKey importPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] key;
		try (var fis = new FileInputStream(new File(keysDir, PUBLIC));) {
			key = fis.readAllBytes();
		}

		var keyFactory = KeyFactory.getInstance(RSA);
		var spec = new X509EncodedKeySpec(key);
		return keyFactory.generatePublic(spec);
	}

	private PrivateKey importPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] key;
		try (var fis = new FileInputStream(new File(keysDir, PRIVATE));) {
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

		try (var pubFos = new FileOutputStream(new File(keysDir, PUBLIC));
				var privFos = new FileOutputStream(new File(keysDir, PRIVATE));) {
			pubFos.write(kp.getPublic().getEncoded());
			privFos.write(kp.getPrivate().getEncoded());
		}
	}

}
