package com.whitxowl.notificationservice.service.impl;

import com.whitxowl.authservice.events.auth.UserCreated;
import com.whitxowl.notificationservice.domain.NotificationStatus;
import com.whitxowl.notificationservice.domain.NotificationType;
import com.whitxowl.notificationservice.domain.document.NotificationDocument;
import com.whitxowl.notificationservice.domain.document.UserEmailCacheDocument;
import com.whitxowl.notificationservice.repository.NotificationRepository;
import com.whitxowl.notificationservice.repository.UserEmailCacheRepository;
import com.whitxowl.notificationservice.service.NotificationService;
import com.whitxowl.orderservice.events.order.OrderCreated;
import com.whitxowl.userservice.events.user.UserRoleChanged;
import com.whitxowl.orderservice.events.order.OrderStatusChanged;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationRepository notificationRepository;
    private final UserEmailCacheRepository userEmailCacheRepository;

    @Value("${spring.mail.from}")
    private String from;

    @Value("${app.auth.verification-url}")
    private String verificationUrl;

    // ── user.created ──────────────────────────────────────────────────────────

    @Override
    public void sendUserCreated(UserCreated event) {
        String userId = event.getUserId().toString();
        String email  = event.getEmail();

        userEmailCacheRepository.save(
                UserEmailCacheDocument.builder()
                        .userId(userId)
                        .email(email)
                        .build()
        );

        if (isAlreadySent(userId, NotificationType.USER_CREATED)) {
            return;
        }

        String verificationLink = verificationUrl + "?token=" + event.getVerificationToken();

        Context ctx = new Context();
        ctx.setVariable("email",            email);
        ctx.setVariable("verificationLink", verificationLink);

        send(email, userId, NotificationType.USER_CREATED,
                "Подтвердите ваш email", "user-created", ctx);
    }

    // ── user.role-changed ─────────────────────────────────────────────────────

    @Override
    public void sendRoleChanged(UserRoleChanged event) {
        String userId = event.getUserId();

        if (isAlreadySent(userId, NotificationType.ROLE_CHANGED)) {
            return;
        }

        String email = resolveEmail(userId);
        if (email == null) {
            log.warn("Email not found for userId={}, skipping ROLE_CHANGED notification", userId);
            return;
        }

        Context ctx = new Context();
        ctx.setVariable("roles", String.join(", ", event.getRoles()));

        send(email, userId, NotificationType.ROLE_CHANGED,
                "Ваши права доступа изменены", "role-changed", ctx);
    }

    // ── order.created ─────────────────────────────────────────────────────────

    @Override
    public void sendOrderCreated(OrderCreated event) {
        String orderId = event.getOrderId();
        String userId  = event.getUserId();

        if (isAlreadySent(orderId, NotificationType.ORDER_CREATED)) {
            return;
        }

        String email = resolveEmail(userId);
        if (email == null) {
            log.warn("Email not found for userId={}, skipping ORDER_CREATED notification [orderId={}]",
                    userId, orderId);
            return;
        }

        Context ctx = new Context();
        ctx.setVariable("orderId",   orderId);
        ctx.setVariable("productId", event.getProductId());
        ctx.setVariable("quantity",  event.getQuantity());

        send(email, orderId, NotificationType.ORDER_CREATED,
                "Заказ %s принят".formatted(orderId), "order-created", ctx);
    }

    // ── order.status-changed ──────────────────────────────────────────────────

    @Override
    public void sendOrderStatusChanged(OrderStatusChanged event) {
        String orderId = event.getOrderId();
        String status  = event.getStatus();

        NotificationType type = switch (status) {
            case "RESERVED"  -> NotificationType.ORDER_RESERVED;
            case "CANCELLED" -> NotificationType.ORDER_CANCELLED;
            default -> {
                log.warn("Unhandled order status [orderId={}, status={}], skipping", orderId, status);
                yield null;
            }
        };

        if (type == null) return;

        if (isAlreadySent(orderId, type)) return;

        String email = userEmailCacheRepository.findByUserId(event.getUserId())
                .map(UserEmailCacheDocument::getEmail)
                .orElse(null);

        if (email == null) {
            log.warn("Email not found for userId={}, skipping {} notification", event.getUserId(), type);
            return;
        }

        Context ctx = new Context();
        ctx.setVariable("orderId",   orderId);
        ctx.setVariable("productId", event.getProductId());
        ctx.setVariable("quantity",  event.getQuantity());
        ctx.setVariable("status",    status);

        String subject = switch (status) {
            case "RESERVED"  -> "Заказ %s подтверждён".formatted(orderId);
            case "CANCELLED" -> "Заказ %s отменён".formatted(orderId);
            default          -> "Статус заказа %s изменён".formatted(orderId);
        };

        send(email, orderId, type, subject, "order-status-changed", ctx);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean isAlreadySent(String referenceId, NotificationType type) {
        boolean exists = notificationRepository.existsByReferenceIdAndType(referenceId, type);
        if (exists) {
            log.warn("Notification already sent, skipping [referenceId={}, type={}]",
                    referenceId, type);
        }
        return exists;
    }

    private String resolveEmail(String userId) {
        return userEmailCacheRepository.findByUserId(userId)
                .map(UserEmailCacheDocument::getEmail)
                .orElse(null);
    }

    private void send(String to, String referenceId, NotificationType type,
                      String subject, String template, Context ctx) {
        NotificationDocument.NotificationDocumentBuilder builder = NotificationDocument.builder()
                .referenceId(referenceId)
                .type(type)
                .recipientEmail(to)
                .sentAt(Instant.now());

        try {
            String html = templateEngine.process(template, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

            notificationRepository.save(builder.status(NotificationStatus.SENT).build());
            log.info("Notification sent [to={}, type={}, referenceId={}]", to, type, referenceId);

        } catch (Exception e) {
            log.error("Failed to send notification [to={}, type={}, referenceId={}]",
                    to, type, referenceId, e);
            notificationRepository.save(builder
                    .status(NotificationStatus.FAILED)
                    .failureReason(e.getMessage())
                    .build());
        }
    }
}