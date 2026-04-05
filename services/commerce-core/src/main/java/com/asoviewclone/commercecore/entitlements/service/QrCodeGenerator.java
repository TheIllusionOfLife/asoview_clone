package com.asoviewclone.commercecore.entitlements.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QrCodeGenerator {

  public String generate() {
    return "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
  }
}
