package com.whitxowl.notificationservice.service;

import com.whitxowl.authservice.events.auth.UserCreated;
import com.whitxowl.notificationservice.domain.NotificationStatus;
import com.whitxowl.notificationservice.domain.NotificationType;
import com.whitxowl.notificationservice.domain.document.NotificationDocument;
import com.whitxowl.notificationservice.domain.document.UserEmailCacheDocument;
import com.whitxowl.notificationservice.repository.NotificationRepository;
import com.whitxowl.notificationservice.repository.UserEmailCacheRepository;
import com.whitxowl.notificationservice.service.impl.NotificationServiceImpl;
import com.whitxowl.orderservice.events.order.OrderCreated;
import com.whitxowl.orderservice.events.order.OrderStatusChanged;
import com.whitxowl.userservice.events.user.UserRoleChanged;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserEmailCacheRepository userEmailCacheRepository;

    @InjectMocks
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "from", "noreply@example.com");
        ReflectionTestUtils.setField(service, "verificationUrl", "http://localhost:8083/api/v1/auth/verify");
    }

    // ── sendUserCreated ───────────────────────────────────────────────────────

    @Test
    void sendUserCreated_shouldCacheEmailAndSendNotification_whenNotSentBefore() throws Exception {
        UUID userId = UUID.randomUUID();
        UserCreated event = UserCreated.newBuilder()
                .setUserId(userId)
                .setEmail("user@example.com")
                .setRoles(List.of("ROLE_USER"))
                .setVerificationToken("tok-123")
                .setCreatedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(userId.toString(), NotificationType.USER_CREATED))
                .thenReturn(false);
        when(templateEngine.process(eq("user-created"), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendUserCreated(event);

        verify(userEmailCacheRepository).save(any(UserEmailCacheDocument.class));
        verify(mailSender).send(mimeMessage);

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(notificationRepository).save(captor.capture());
        NotificationDocument saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.USER_CREATED);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getRecipientEmail()).isEqualTo("user@example.com");
        assertThat(saved.getReferenceId()).isEqualTo(userId.toString());
    }

    @Test
    void sendUserCreated_shouldCacheEmailButSkipSend_whenAlreadySent() {
        UUID userId = UUID.randomUUID();
        UserCreated event = UserCreated.newBuilder()
                .setUserId(userId)
                .setEmail("user@example.com")
                .setRoles(List.of("ROLE_USER"))
                .setVerificationToken("tok-123")
                .setCreatedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(userId.toString(), NotificationType.USER_CREATED))
                .thenReturn(true);

        service.sendUserCreated(event);

        verify(userEmailCacheRepository).save(any(UserEmailCacheDocument.class));
        verifyNoInteractions(mailSender);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendUserCreated_shouldSaveFailedNotification_whenMailThrows() throws Exception {
        UUID userId = UUID.randomUUID();
        UserCreated event = UserCreated.newBuilder()
                .setUserId(userId)
                .setEmail("user@example.com")
                .setRoles(List.of("ROLE_USER"))
                .setVerificationToken("tok-fail")
                .setCreatedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(userId.toString(), NotificationType.USER_CREATED))
                .thenReturn(false);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        service.sendUserCreated(event);

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).contains("SMTP error");
    }

    // ── sendRoleChanged ───────────────────────────────────────────────────────

    @Test
    void sendRoleChanged_shouldSendNotification_whenEmailFoundInCache() throws Exception {
        String userId = "user-1";
        UserRoleChanged event = UserRoleChanged.newBuilder()
                .setUserId(userId)
                .setRoles(List.of("ROLE_ADMIN"))
                .setChangedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(userId, NotificationType.ROLE_CHANGED))
                .thenReturn(false);
        when(userEmailCacheRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserEmailCacheDocument.builder().userId(userId).email("admin@example.com").build()));
        when(templateEngine.process(eq("role-changed"), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendRoleChanged(event);

        verify(mailSender).send(mimeMessage);
        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ROLE_CHANGED);
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void sendRoleChanged_shouldSkipSend_whenEmailNotFoundInCache() {
        String userId = "user-unknown";
        UserRoleChanged event = UserRoleChanged.newBuilder()
                .setUserId(userId)
                .setRoles(List.of("ROLE_USER"))
                .setChangedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(userId, NotificationType.ROLE_CHANGED))
                .thenReturn(false);
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.sendRoleChanged(event);

        verifyNoInteractions(mailSender);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendRoleChanged_shouldSkipSend_whenAlreadySent() {
        String userId = "user-1";
        UserRoleChanged event = UserRoleChanged.newBuilder()
                .setUserId(userId)
                .setRoles(List.of("ROLE_ADMIN"))
                .setChangedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(userId, NotificationType.ROLE_CHANGED))
                .thenReturn(true);

        service.sendRoleChanged(event);

        verifyNoInteractions(mailSender);
        verifyNoInteractions(userEmailCacheRepository);
    }

    // ── sendOrderCreated ──────────────────────────────────────────────────────

    @Test
    void sendOrderCreated_shouldSendNotification_whenEmailFoundInCache() throws Exception {
        String orderId = "ord-1";
        String userId = "user-1";
        OrderCreated event = OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId("prod-1")
                .setQuantity(2)
                .setCreatedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(orderId, NotificationType.ORDER_CREATED))
                .thenReturn(false);
        when(userEmailCacheRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserEmailCacheDocument.builder().userId(userId).email("buyer@example.com").build()));
        when(templateEngine.process(eq("order-created"), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendOrderCreated(event);

        verify(mailSender).send(mimeMessage);
        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(notificationRepository).save(captor.capture());
        NotificationDocument saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CREATED);
        assertThat(saved.getReferenceId()).isEqualTo(orderId);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void sendOrderCreated_shouldSkipSend_whenEmailNotFoundInCache() {
        String orderId = "ord-1";
        String userId = "user-unknown";
        OrderCreated event = OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId("prod-1")
                .setQuantity(1)
                .setCreatedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(orderId, NotificationType.ORDER_CREATED))
                .thenReturn(false);
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.sendOrderCreated(event);

        verifyNoInteractions(mailSender);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendOrderCreated_shouldSkipSend_whenAlreadySent() {
        String orderId = "ord-dup";
        OrderCreated event = OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId("user-1")
                .setProductId("prod-1")
                .setQuantity(1)
                .setCreatedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(orderId, NotificationType.ORDER_CREATED))
                .thenReturn(true);

        service.sendOrderCreated(event);

        verifyNoInteractions(mailSender);
        verifyNoInteractions(userEmailCacheRepository);
    }

    // ── sendOrderStatusChanged ────────────────────────────────────────────────

    @Test
    void sendOrderStatusChanged_shouldSendReservedNotification_whenStatusReserved() throws Exception {
        String orderId = "ord-1";
        String userId = "user-1";
        OrderStatusChanged event = OrderStatusChanged.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId("prod-1")
                .setQuantity(3)
                .setStatus("RESERVED")
                .setChangedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(orderId, NotificationType.ORDER_RESERVED))
                .thenReturn(false);
        when(userEmailCacheRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserEmailCacheDocument.builder().userId(userId).email("buyer@example.com").build()));
        when(templateEngine.process(eq("order-status-changed"), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendOrderStatusChanged(event);

        verify(mailSender).send(mimeMessage);
        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(notificationRepository).save(captor.capture());
        NotificationDocument saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_RESERVED);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getReferenceId()).isEqualTo(orderId);
    }

    @Test
    void sendOrderStatusChanged_shouldSendCancelledNotification_whenStatusCancelled() throws Exception {
        String orderId = "ord-1";
        String userId = "user-1";
        OrderStatusChanged event = OrderStatusChanged.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId("prod-1")
                .setQuantity(3)
                .setStatus("CANCELLED")
                .setChangedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(orderId, NotificationType.ORDER_CANCELLED))
                .thenReturn(false);
        when(userEmailCacheRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserEmailCacheDocument.builder().userId(userId).email("buyer@example.com").build()));
        when(templateEngine.process(eq("order-status-changed"), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendOrderStatusChanged(event);

        verify(mailSender).send(mimeMessage);
        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ORDER_CANCELLED);
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void sendOrderStatusChanged_shouldSkipSend_whenStatusUnknown() {
        OrderStatusChanged event = OrderStatusChanged.newBuilder()
                .setOrderId("ord-1")
                .setUserId("user-1")
                .setProductId("prod-1")
                .setQuantity(1)
                .setStatus("SHIPPED")
                .setChangedAt(Instant.now())
                .build();

        service.sendOrderStatusChanged(event);

        verifyNoInteractions(mailSender);
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(userEmailCacheRepository);
    }

    @Test
    void sendOrderStatusChanged_shouldSkipSend_whenAlreadySent() {
        String orderId = "ord-dup";
        OrderStatusChanged event = OrderStatusChanged.newBuilder()
                .setOrderId(orderId)
                .setUserId("user-1")
                .setProductId("prod-1")
                .setQuantity(1)
                .setStatus("RESERVED")
                .setChangedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(orderId, NotificationType.ORDER_RESERVED))
                .thenReturn(true);

        service.sendOrderStatusChanged(event);

        verifyNoInteractions(mailSender);
        verifyNoInteractions(userEmailCacheRepository);
    }

    @Test
    void sendOrderStatusChanged_shouldSkipSend_whenEmailNotFoundInCache() {
        String orderId = "ord-1";
        String userId = "user-unknown";
        OrderStatusChanged event = OrderStatusChanged.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId("prod-1")
                .setQuantity(1)
                .setStatus("RESERVED")
                .setChangedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(orderId, NotificationType.ORDER_RESERVED))
                .thenReturn(false);
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.sendOrderStatusChanged(event);

        verifyNoInteractions(mailSender);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendOrderStatusChanged_shouldSaveFailedNotification_whenMailThrows() throws Exception {
        String orderId = "ord-1";
        String userId = "user-1";
        OrderStatusChanged event = OrderStatusChanged.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId("prod-1")
                .setQuantity(1)
                .setStatus("RESERVED")
                .setChangedAt(Instant.now())
                .build();

        when(notificationRepository.existsByReferenceIdAndType(orderId, NotificationType.ORDER_RESERVED))
                .thenReturn(false);
        when(userEmailCacheRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserEmailCacheDocument.builder().userId(userId).email("buyer@example.com").build()));
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        service.sendOrderStatusChanged(event);

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).contains("SMTP error");
    }
}