package com.asoviewclone.commercecore.wallet.service;

import com.asoviewclone.commercecore.wallet.model.WalletTicketContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds a Google Wallet save URL by signing an EventTicketObject JWT with RS256. Production needs
 * a real Google Wallet service account key configured via {@code app.wallet.google.sa-key-path}.
 */
@Component
public class GoogleWalletJwtBuilder {

  private static final String SAVE_URL_PREFIX = "https://pay.google.com/gp/v/save/";

  private final WalletGoogleKeyProvider keyProvider;
  private final String issuerId;
  private final String issuerEmail;
  private final String classId;

  public GoogleWalletJwtBuilder(
      WalletGoogleKeyProvider keyProvider,
      @Value("${app.wallet.google.issuer-id:0000000000000000000}") String issuerId,
      @Value("${app.wallet.google.issuer-email:dev@asoview-clone.example}") String issuerEmail,
      @Value("${app.wallet.google.class-id:0000000000000000000.asoview-ticket}") String classId) {
    this.keyProvider = keyProvider;
    this.issuerId = issuerId;
    this.issuerEmail = issuerEmail;
    this.classId = classId;
  }

  public String buildSaveUrl(WalletTicketContext ctx) {
    Map<String, Object> barcode = new LinkedHashMap<>();
    barcode.put("type", "QR_CODE");
    barcode.put("value", ctx.qrCodePayload());

    Map<String, Object> venue = new LinkedHashMap<>();
    venue.put(
        "name",
        Map.of(
            "defaultValue",
            Map.of("language", "ja-JP", "value", ctx.venueName() != null ? ctx.venueName() : "")));

    Map<String, Object> eventName =
        Map.of("defaultValue", Map.of("language", "ja-JP", "value", ctx.productName()));

    Map<String, Object> ticketObject = new LinkedHashMap<>();
    ticketObject.put("id", issuerId + "." + ctx.ticketPassId());
    ticketObject.put("classId", classId);
    ticketObject.put("state", "ACTIVE");
    ticketObject.put("ticketNumber", ctx.ticketPassId());
    ticketObject.put("eventName", eventName);
    if (ctx.validFrom() != null) {
      Map<String, Object> dateTime = new LinkedHashMap<>();
      dateTime.put("start", ctx.validFrom().toString());
      if (ctx.validUntil() != null) {
        dateTime.put("end", ctx.validUntil().toString());
      }
      ticketObject.put("dateTime", dateTime);
    }
    ticketObject.put("venue", venue);
    ticketObject.put("barcode", barcode);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventTicketObjects", List.of(ticketObject));

    Instant now = Instant.now();
    String jwt =
        Jwts.builder()
            .issuer(issuerEmail)
            .audience()
            .add("google")
            .and()
            .claim("typ", "savetowallet")
            .issuedAt(Date.from(now))
            .claim("payload", payload)
            .signWith(keyProvider.getPrivateKey(), Jwts.SIG.RS256)
            .compact();

    return SAVE_URL_PREFIX + jwt;
  }

  /** Exposed for tests that need to know which alg the builder uses without parsing tokens. */
  public SignatureAlgorithm getAlgorithm() {
    return SignatureAlgorithm.RS256;
  }
}
