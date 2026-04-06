package com.asoviewclone.common.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

  public Money {
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    if (currency.length() != 3) {
      throw new IllegalArgumentException("currency must be a 3-letter ISO code");
    }
    int scale;
    try {
      scale = Currency.getInstance(currency).getDefaultFractionDigits();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown ISO currency code: " + currency, e);
    }
    if (scale < 0) {
      scale = 2;
    }
    amount = amount.setScale(scale, RoundingMode.HALF_UP);
  }

  public static Money of(BigDecimal amount, String currency) {
    return new Money(amount, currency);
  }

  public static Money of(String amount, String currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public static Money jpy(long amount) {
    return new Money(BigDecimal.valueOf(amount), "JPY");
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  public Money multiply(int quantity) {
    return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Cannot operate on different currencies: " + currency + " vs " + other.currency);
    }
  }
}
