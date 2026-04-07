package com.asoviewclone.commercecore.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.wallet.model.WalletTicketContext;
import com.asoviewclone.commercecore.wallet.service.AppleWalletPassBuilder;
import com.asoviewclone.commercecore.wallet.service.WalletDevCertProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleWalletPassBuilderTest {

  private AppleWalletPassBuilder builder;
  private WalletDevCertProvider certProvider;

  @BeforeEach
  void setUp() throws Exception {
    certProvider =
        new WalletDevCertProvider(
            System.getProperty("java.io.tmpdir")
                + "/asoview-wallet-test-"
                + System.nanoTime()
                + ".p12",
            "devpass");
    certProvider.init();
    builder = new AppleWalletPassBuilder(certProvider, "pass.com.test", "TEAMID", "Test Org");
  }

  @Test
  void buildsPkpassZipWithExpectedFiles() throws Exception {
    WalletTicketContext ctx =
        new WalletTicketContext(
            "tp-1",
            "order-1",
            "user-1",
            "Rafting Tour",
            "Tokyo Bay",
            Instant.parse("2026-04-10T01:00:00Z"),
            Instant.parse("2026-04-10T03:00:00Z"),
            "QR-PAYLOAD-XYZ");

    byte[] pass = builder.build(ctx);
    assertThat(pass).isNotEmpty();

    Map<String, byte[]> entries = unzip(pass);
    assertThat(entries)
        .containsKeys(
            "pass.json", "manifest.json", "signature", "icon.png", "icon@2x.png", "logo.png");

    // Manifest SHA-1 of pass.json must match recomputed SHA-1.
    Map<?, ?> manifest = new ObjectMapper().readValue(entries.get("manifest.json"), Map.class);
    String declared = (String) manifest.get("pass.json");
    String recomputed =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-1").digest(entries.get("pass.json")));
    assertThat(declared).isEqualTo(recomputed);

    // Signature parses as CMSSignedData and verifies against the dev cert.
    CMSSignedData signed =
        new CMSSignedData(
            new CMSProcessableByteArray(entries.get("manifest.json")), entries.get("signature"));
    Store<?> certStore = signed.getCertificates();
    SignerInformation signer = signed.getSignerInfos().getSigners().iterator().next();
    var holder =
        (org.bouncycastle.cert.X509CertificateHolder)
            certStore.getMatches(signer.getSID()).iterator().next();
    boolean verified =
        signer.verify(
            new JcaSimpleSignerInfoVerifierBuilder()
                .build(new JcaX509CertificateConverter().getCertificate(holder)));
    assertThat(verified).isTrue();
  }

  private Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
    Map<String, byte[]> out = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        out.put(e.getName(), zis.readAllBytes());
      }
    }
    return out;
  }
}
