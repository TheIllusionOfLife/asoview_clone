package com.asoviewclone.commercecore.wallet.service;

import com.asoviewclone.commercecore.wallet.model.WalletTicketContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds an Apple Wallet .pkpass byte[] for a {@link WalletTicketContext}. Produces a real PKCS#7
 * detached signature over manifest.json using BouncyCastle.
 *
 * <p><b>Production note:</b> the cert chain produced here is a self-signed dev cert and is NOT
 * acceptable to Apple Wallet. A real production install requires an Apple Developer account and a
 * Pass Type ID cert chained to the Apple WWDR intermediate. See PR runbook.
 */
@Component
public class AppleWalletPassBuilder {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WalletDevCertProvider certProvider;
  private final String passTypeId;
  private final String teamId;
  private final String organizationName;

  public AppleWalletPassBuilder(
      WalletDevCertProvider certProvider,
      @Value("${app.wallet.apple.pass-type-id:pass.com.asoviewclone.dev}") String passTypeId,
      @Value("${app.wallet.apple.team-id:DEVTEAMID}") String teamId,
      @Value("${app.wallet.apple.organization-name:Asoview Clone}") String organizationName) {
    this.certProvider = certProvider;
    this.passTypeId = passTypeId;
    this.teamId = teamId;
    this.organizationName = organizationName;
  }

  public byte[] build(WalletTicketContext ctx) {
    try {
      Map<String, byte[]> files = new LinkedHashMap<>();
      files.put("pass.json", buildPassJson(ctx));
      byte[] icon = buildPlaceholderPng();
      files.put("icon.png", icon);
      files.put("icon@2x.png", icon);
      files.put("logo.png", icon);

      byte[] manifest = buildManifest(files);
      files.put("manifest.json", manifest);

      byte[] signature = signManifest(manifest);
      files.put("signature", signature);

      return zip(files);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build Apple Wallet pass", e);
    }
  }

  private byte[] buildPassJson(WalletTicketContext ctx) throws IOException {
    Map<String, Object> pass = new LinkedHashMap<>();
    pass.put("formatVersion", 1);
    pass.put("passTypeIdentifier", passTypeId);
    pass.put("teamIdentifier", teamId);
    pass.put("organizationName", organizationName);
    pass.put("serialNumber", ctx.ticketPassId());
    pass.put("description", ctx.productName());

    Map<String, Object> barcode = new LinkedHashMap<>();
    barcode.put("format", "PKBarcodeFormatQR");
    barcode.put("message", ctx.qrCodePayload());
    barcode.put("messageEncoding", "iso-8859-1");
    pass.put("barcode", barcode);
    pass.put("barcodes", List.of(barcode));

    Map<String, Object> eventTicket = new LinkedHashMap<>();
    List<Map<String, Object>> primaryFields = new ArrayList<>();
    Map<String, Object> primary = new LinkedHashMap<>();
    primary.put("key", "event");
    primary.put("label", "EVENT");
    primary.put("value", ctx.productName());
    primaryFields.add(primary);
    eventTicket.put("primaryFields", primaryFields);

    List<Map<String, Object>> secondaryFields = new ArrayList<>();
    Map<String, Object> venueField = new LinkedHashMap<>();
    venueField.put("key", "venue");
    venueField.put("label", "VENUE");
    venueField.put("value", ctx.venueName() != null ? ctx.venueName() : "");
    secondaryFields.add(venueField);
    if (ctx.validFrom() != null) {
      Map<String, Object> when = new LinkedHashMap<>();
      when.put("key", "when");
      when.put("label", "WHEN");
      when.put("value", ctx.validFrom().toString());
      secondaryFields.add(when);
    }
    eventTicket.put("secondaryFields", secondaryFields);
    pass.put("eventTicket", eventTicket);

    pass.put("locations", List.of());

    return MAPPER.writeValueAsBytes(pass);
  }

  private byte[] buildManifest(Map<String, byte[]> files)
      throws NoSuchAlgorithmException, IOException {
    Map<String, String> manifest = new LinkedHashMap<>();
    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
    HexFormat hex = HexFormat.of();
    for (Map.Entry<String, byte[]> entry : files.entrySet()) {
      sha1.reset();
      manifest.put(entry.getKey(), hex.formatHex(sha1.digest(entry.getValue())));
    }
    return MAPPER.writeValueAsBytes(manifest);
  }

  private byte[] signManifest(byte[] manifest) {
    try {
      CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
      ContentSigner signer =
          new JcaContentSignerBuilder("SHA256withRSA")
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .build(certProvider.getPrivateKey());
      DigestCalculatorProvider digestCalcProvider =
          new JcaDigestCalculatorProviderBuilder()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .build();
      SignerInfoGenerator signerInfoGen =
          new JcaSignerInfoGeneratorBuilder(digestCalcProvider)
              .build(signer, certProvider.getCertificate());
      generator.addSignerInfoGenerator(signerInfoGen);
      generator.addCertificates(new JcaCertStore(List.of(certProvider.getCertificate())));
      CMSSignedData signed = generator.generate(new CMSProcessableByteArray(manifest), false);
      return signed.getEncoded();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign Apple Wallet manifest", e);
    }
  }

  private byte[] buildPlaceholderPng() throws IOException {
    BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "png", baos);
    return baos.toByteArray();
  }

  private byte[] zip(Map<String, byte[]> files) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (Map.Entry<String, byte[]> entry : files.entrySet()) {
        ZipEntry e = new ZipEntry(entry.getKey());
        zos.putNextEntry(e);
        zos.write(entry.getValue());
        zos.closeEntry();
      }
    }
    return baos.toByteArray();
  }
}
