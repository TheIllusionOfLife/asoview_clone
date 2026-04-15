package com.asoviewclone.reservation.service;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public EmailService(
      JavaMailSender mailSender,
      @Value("${app.mail.from:noreply@asoview-clone.dev}") String fromAddress) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
  }

  @Async
  public CompletableFuture<Void> sendReservationConfirmation(Reservation reservation) {
    if (reservation.guestEmail() == null || reservation.guestEmail().isBlank()) {
      log.debug(
          "Skipping confirmation email: no guest email for reservation {}",
          reservation.reservationId());
      return CompletableFuture.completedFuture(null);
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(reservation.guestEmail());
    message.setSubject("予約リクエストを受け付けました");
    message.setText(
        "%s 様\n\n予約リクエストを受け付けました。\n\n予約ID: %s\n人数: %d名\n\nステータスが変更されましたらメールでお知らせいたします。"
            .formatted(
                reservation.guestName(), reservation.reservationId(), reservation.guestCount()));

    sendSafely(message, reservation.reservationId());
    return CompletableFuture.completedFuture(null);
  }

  @Async
  public CompletableFuture<Void> sendStatusChangeNotification(Reservation reservation) {
    if (reservation.guestEmail() == null || reservation.guestEmail().isBlank()) {
      log.debug(
          "Skipping status notification: no guest email for reservation {}",
          reservation.reservationId());
      return CompletableFuture.completedFuture(null);
    }

    String subject = buildSubject(reservation.status());
    String body = buildStatusBody(reservation);

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(reservation.guestEmail());
    message.setSubject(subject);
    message.setText(body);

    sendSafely(message, reservation.reservationId());
    return CompletableFuture.completedFuture(null);
  }

  private String buildSubject(ReservationStatus status) {
    return switch (status) {
      case APPROVED -> "予約が承認されました";
      case REJECTED -> "予約が却下されました";
      case WAITLISTED -> "予約がキャンセル待ちになりました";
      case CANCELLED -> "予約がキャンセルされました";
      case COMPLETED -> "予約が完了しました";
      default -> "予約ステータスが変更されました";
    };
  }

  private String buildStatusBody(Reservation reservation) {
    StringBuilder sb = new StringBuilder();
    sb.append("%s 様\n\n".formatted(reservation.guestName()));
    sb.append("予約ID: %s\n".formatted(reservation.reservationId()));
    sb.append("ステータス: %s\n".formatted(statusLabel(reservation.status())));

    if (reservation.rejectReason() != null && !reservation.rejectReason().isBlank()) {
      sb.append("理由: %s\n".formatted(reservation.rejectReason()));
    }
    if (reservation.cancelReason() != null && !reservation.cancelReason().isBlank()) {
      sb.append("理由: %s\n".formatted(reservation.cancelReason()));
    }

    return sb.toString();
  }

  private String statusLabel(ReservationStatus status) {
    return switch (status) {
      case PENDING_APPROVAL -> "承認待ち";
      case APPROVED -> "承認済み";
      case REJECTED -> "却下";
      case WAITLISTED -> "キャンセル待ち";
      case CANCELLED -> "キャンセル済み";
      case COMPLETED -> "完了";
    };
  }

  private void sendSafely(SimpleMailMessage message, String reservationId) {
    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.error("Failed to send email for reservation {}: {}", reservationId, e.getMessage());
    }
  }
}
