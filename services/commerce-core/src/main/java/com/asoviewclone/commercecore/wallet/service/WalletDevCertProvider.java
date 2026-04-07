package com.asoviewclone.commercecore.wallet.service;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loads (or, when no keystore exists at the configured path, generates) a PKCS12 keystore used for
 * the Apple .pkpass PKCS#7 detached signature.
 *
 * <p>In production, point {@code app.wallet.apple.cert-path} at the real Apple-issued Pass Type ID
 * PKCS12 (uploaded out-of-band via Secret Manager + a CSI mount on the GKE pod). The generated
 * fall-back cert is self-signed and is NOT acceptable to Apple Wallet without being chained to a
 * real Apple-issued cert + the WWDR intermediate. Production install is documented in the PR
 * runbook.
 *
 * <p>Bean loads in every profile (no {@code @Profile} restriction): if the configured path is
 * absent the dev fall-back generates a self-signed PKCS12 in {@code java.io.tmpdir} so the
 * application context bootstraps cleanly even when wallet support isn't actively used in that
 * environment. (PR #21 Codex finding: previous {@code @Profile({"local","test","default"})} caused
 * {@code SPRING_PROFILES_ACTIVE=gke} startup to fail because {@code AppleWalletPassBuilder}
 * unconditionally depends on this bean.)
 */
@Component
public class WalletDevCertProvider {

  private static final Logger log = LoggerFactory.getLogger(WalletDevCertProvider.class);
  private static final String ALIAS = "asoview-dev";

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private final String certPath;
  private final String certPassword;

  private X509Certificate certificate;
  private PrivateKey privateKey;

  public WalletDevCertProvider(
      @Value(
              "${app.wallet.apple.cert-path:#{systemProperties['java.io.tmpdir']}/asoview-wallet-dev.p12}")
          String certPath,
      @Value("${app.wallet.apple.cert-password:devpass}") String certPassword) {
    this.certPath = certPath;
    this.certPassword = certPassword;
  }

  @PostConstruct
  public void init() throws GeneralSecurityException, IOException {
    Path path = Paths.get(certPath);
    if (Files.exists(path)) {
      load(path);
      log.info("Loaded wallet dev cert from {}", path);
    } else {
      generate(path);
      log.warn(
          "Generated self-signed dev wallet cert at {}; NOT valid for production Apple Wallet.",
          path);
    }
  }

  private void load(Path path) throws GeneralSecurityException, IOException {
    KeyStore ks = KeyStore.getInstance("PKCS12");
    try (FileInputStream fis = new FileInputStream(path.toFile())) {
      ks.load(fis, certPassword.toCharArray());
    }
    String alias = ks.aliases().hasMoreElements() ? ks.aliases().nextElement() : ALIAS;
    this.certificate = (X509Certificate) ks.getCertificate(alias);
    this.privateKey = (PrivateKey) ks.getKey(alias, certPassword.toCharArray());
  }

  private void generate(Path path) throws GeneralSecurityException, IOException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();

    Instant now = Instant.now();
    Date notBefore = Date.from(now.minusSeconds(60));
    Date notAfter = Date.from(now.plusSeconds(3650L * 24 * 3600));
    X500Name subject = new X500Name("CN=asoview-dev,O=asoview-clone,C=JP");
    BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

    X509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, kp.getPublic());
    try {
      ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
      X509Certificate cert =
          new JcaX509CertificateConverter()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .getCertificate(builder.build(signer));

      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(null, null);
      ks.setKeyEntry(
          ALIAS, kp.getPrivate(), certPassword.toCharArray(), new X509Certificate[] {cert});

      Files.createDirectories(path.toAbsolutePath().getParent());
      try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
        ks.store(fos, certPassword.toCharArray());
      }

      this.certificate = cert;
      this.privateKey = kp.getPrivate();
    } catch (org.bouncycastle.operator.OperatorCreationException e) {
      throw new GeneralSecurityException("Failed to build self-signed dev cert", e);
    }
  }

  public X509Certificate getCertificate() {
    return certificate;
  }

  public PrivateKey getPrivateKey() {
    return privateKey;
  }
}
