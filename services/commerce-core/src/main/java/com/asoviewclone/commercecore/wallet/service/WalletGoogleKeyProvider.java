package com.asoviewclone.commercecore.wallet.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loads (or, when no path is configured, generates an in-memory stub) the RSA private key used to
 * RS256-sign Google Wallet save URLs. Production installs configure {@code
 * app.wallet.google.sa-key-path} to point at a real Google service account JSON's private key.
 */
@Component
public class WalletGoogleKeyProvider {

  private static final Logger log = LoggerFactory.getLogger(WalletGoogleKeyProvider.class);

  private final String keyPath;

  private PrivateKey privateKey;

  public WalletGoogleKeyProvider(@Value("${app.wallet.google.sa-key-path:}") String keyPath) {
    this.keyPath = keyPath;
  }

  @PostConstruct
  public void init() throws GeneralSecurityException, IOException {
    if (keyPath != null && !keyPath.isBlank()) {
      Path path = Paths.get(keyPath);
      if (Files.exists(path)) {
        loadPkcs8Pem(path);
        log.info("Loaded Google Wallet signing key from {}", path);
        return;
      }
      log.warn("Configured Google Wallet key path {} does not exist; generating stub key", path);
    }
    generateStub();
    log.warn(
        "Generated in-memory stub Google Wallet RSA key; NOT valid for production Google Wallet.");
  }

  private void loadPkcs8Pem(Path path) throws GeneralSecurityException, IOException {
    String pem = Files.readString(path);
    String stripped =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(stripped);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    this.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  private void generateStub() throws GeneralSecurityException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();
    this.privateKey = kp.getPrivate();
  }

  public PrivateKey getPrivateKey() {
    return privateKey;
  }
}
