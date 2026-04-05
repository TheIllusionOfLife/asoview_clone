package com.asoviewclone.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void createFromBigDecimal() {
    Money money = Money.of(new BigDecimal("1000"), "JPY");
    assertThat(money.amount()).isEqualByComparingTo("1000.00");
    assertThat(money.currency()).isEqualTo("JPY");
  }

  @Test
  void createFromString() {
    Money money = Money.of("500.5", "JPY");
    assertThat(money.amount()).isEqualByComparingTo("500.50");
  }

  @Test
  void jpyFactory() {
    Money money = Money.jpy(3000);
    assertThat(money.amount()).isEqualByComparingTo("3000.00");
    assertThat(money.currency()).isEqualTo("JPY");
  }

  @Test
  void add() {
    Money a = Money.jpy(1000);
    Money b = Money.jpy(500);
    Money result = a.add(b);
    assertThat(result.amount()).isEqualByComparingTo("1500.00");
  }

  @Test
  void subtract() {
    Money a = Money.jpy(1000);
    Money b = Money.jpy(300);
    Money result = a.subtract(b);
    assertThat(result.amount()).isEqualByComparingTo("700.00");
  }

  @Test
  void multiply() {
    Money money = Money.jpy(500);
    Money result = money.multiply(3);
    assertThat(result.amount()).isEqualByComparingTo("1500.00");
  }

  @Test
  void rejectDifferentCurrencies() {
    Money jpy = Money.jpy(1000);
    Money usd = Money.of("10", "USD");
    assertThatThrownBy(() -> jpy.add(usd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("different currencies");
  }

  @Test
  void rejectInvalidCurrencyCode() {
    assertThatThrownBy(() -> Money.of("100", "JP"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3-letter");
  }

  @Test
  void rejectNullAmount() {
    assertThatThrownBy(() -> Money.of((BigDecimal) null, "JPY"))
        .isInstanceOf(NullPointerException.class);
  }
}
