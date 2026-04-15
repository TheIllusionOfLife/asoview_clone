package com.asoviewclone.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  @Mock private JavaMailSender mailSender;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = new EmailService(mailSender, "noreply@asoview-clone.dev");
  }

  private static final Reservation SAMPLE =
      new Reservation(
          "res-1",
          "tenant-1",
          "venue-1",
          "slot-1",
          "user-1",
          ReservationStatus.PENDING_APPROVAL,
          "idem-1",
          "Taro Yamada",
          "taro@example.com",
          2,
          null,
          null,
          Instant.now(),
          Instant.now());

  @Test
  void sendReservationConfirmation_sendsEmail() {
    emailService.sendReservationConfirmation(SAMPLE);

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());

    SimpleMailMessage msg = captor.getValue();
    assertThat(msg.getTo()).containsExactly("taro@example.com");
    assertThat(msg.getSubject()).contains("予約リクエスト");
    assertThat(msg.getText()).contains("Taro Yamada");
    assertThat(msg.getText()).contains("res-1");
  }

  @Test
  void sendStatusChangeNotification_approved() {
    Reservation approved =
        new Reservation(
            "res-1",
            "t-1",
            "v-1",
            "s-1",
            "u-1",
            ReservationStatus.APPROVED,
            "idem-1",
            "Taro Yamada",
            "taro@example.com",
            2,
            null,
            null,
            Instant.now(),
            Instant.now());

    emailService.sendStatusChangeNotification(approved);

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());

    SimpleMailMessage msg = captor.getValue();
    assertThat(msg.getTo()).containsExactly("taro@example.com");
    assertThat(msg.getSubject()).contains("承認");
    assertThat(msg.getText()).contains("承認済み");
  }

  @Test
  void sendStatusChangeNotification_rejected_includesReason() {
    Reservation rejected =
        new Reservation(
            "res-1",
            "t-1",
            "v-1",
            "s-1",
            "u-1",
            ReservationStatus.REJECTED,
            "idem-1",
            "Taro Yamada",
            "taro@example.com",
            2,
            "Fully booked",
            null,
            Instant.now(),
            Instant.now());

    emailService.sendStatusChangeNotification(rejected);

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());

    SimpleMailMessage msg = captor.getValue();
    assertThat(msg.getText()).contains("Fully booked");
  }

  @Test
  void sendStatusChangeNotification_cancelled_includesReason() {
    Reservation cancelled =
        new Reservation(
            "res-1",
            "t-1",
            "v-1",
            "s-1",
            "u-1",
            ReservationStatus.CANCELLED,
            "idem-1",
            "Taro Yamada",
            "taro@example.com",
            2,
            null,
            "Changed plans",
            Instant.now(),
            Instant.now());

    emailService.sendStatusChangeNotification(cancelled);

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());

    SimpleMailMessage msg = captor.getValue();
    assertThat(msg.getText()).contains("Changed plans");
  }

  @Test
  void sendReservationConfirmation_skipsWhenNoEmail() {
    Reservation noEmail =
        new Reservation(
            "res-1",
            "t-1",
            "v-1",
            "s-1",
            "u-1",
            ReservationStatus.PENDING_APPROVAL,
            "idem-1",
            "Taro Yamada",
            null,
            2,
            null,
            null,
            Instant.now(),
            Instant.now());

    emailService.sendReservationConfirmation(noEmail);

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }
}
