package com.asoviewclone.commercecore.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.wallet.model.WalletTicketContext;
import com.asoviewclone.commercecore.wallet.service.GoogleWalletJwtBuilder;
import com.asoviewclone.commercecore.wallet.service.WalletGoogleKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleWalletJwtBuilderTest {

  private GoogleWalletJwtBuilder builder;
  private PublicKey publicKey;

  @BeforeEach
  void setUp() throws Exception {
    var kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    var kp = kpg.generateKeyPair();

    WalletGoogleKeyProvider keyProvider =
        new WalletGoogleKeyProvider("") {
          @Override
          public PrivateKey getPrivateKey() {
            return kp.getPrivate();
          }
        };
    // No init() needed; we override getPrivateKey().

    RSAPrivateCrtKey rsaPriv = (RSAPrivateCrtKey) kp.getPrivate();
    publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(
                new RSAPublicKeySpec(rsaPriv.getModulus(), rsaPriv.getPublicExponent()));

    builder =
        new GoogleWalletJwtBuilder(
            keyProvider, "1234567890", "issuer@example.com", "1234567890.test-class");
  }

  @Test
  void buildsSaveUrlWithSignedRs256Jwt() {
    WalletTicketContext ctx =
        new WalletTicketContext(
            "tp-42",
            "order-42",
            "user-42",
            "Kayak",
            "Tokyo Bay",
            Instant.parse("2026-05-01T01:00:00Z"),
            Instant.parse("2026-05-01T03:00:00Z"),
            "QR-GOOGLE-XYZ");

    String url = builder.buildSaveUrl(ctx);
    assertThat(url).startsWith("https://pay.google.com/gp/v/save/");
    String jwt = url.substring("https://pay.google.com/gp/v/save/".length());

    Jws<Claims> parsed = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(jwt);
    assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
    Claims claims = parsed.getPayload();
    assertThat(claims.getAudience()).contains("google");
    assertThat(claims.get("typ")).isEqualTo("savetowallet");

    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) claims.get("payload");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> objs = (List<Map<String, Object>>) payload.get("eventTicketObjects");
    assertThat(objs).hasSize(1);
    @SuppressWarnings("unchecked")
    Map<String, Object> barcode = (Map<String, Object>) objs.get(0).get("barcode");
    assertThat(barcode.get("value")).isEqualTo("QR-GOOGLE-XYZ");
    assertThat(barcode.get("type")).isEqualTo("QR_CODE");
  }
}
