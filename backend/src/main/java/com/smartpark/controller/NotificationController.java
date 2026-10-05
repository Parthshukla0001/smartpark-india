package com.smartpark.controller;

import com.smartpark.dto.notification.NotificationResponse;
import com.smartpark.entity.Notification;
import com.smartpark.entity.User;
import com.smartpark.exception.ResourceNotFoundException;
import com.smartpark.repository.NotificationRepository;
import com.smartpark.security.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationRepository notificationRepository, CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        User user = currentUserService.getCurrentUserEntity();
        List<NotificationResponse> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .limit(50)
                .map(NotificationResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(notifications);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    @Transactional
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        User user = currentUserService.getCurrentUserEntity();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Notification not found with id: " + id);
        }

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }

        return ResponseEntity.ok(NotificationResponse.fromEntity(notification));
    }
}
