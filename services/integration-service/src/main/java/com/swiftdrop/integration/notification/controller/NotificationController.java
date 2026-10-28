package com.swiftdrop.integration.notification.controller;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.integration.notification.dto.NotificationDto;
import com.swiftdrop.integration.notification.dto.NotificationPageResponse;
import com.swiftdrop.integration.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<NotificationPageResponse> getNotifications(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {

        String userId = resolveUserId(jwt, authentication, userIdHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        NotificationPageResponse response = notificationService.getNotifications(userId, page, size, unreadOnly);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable("id") String id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {

        String userId = resolveUserId(jwt, authentication, userIdHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        NotificationDto dto = notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(dto);
    }

    private String resolveUserId(Jwt jwt, Authentication authentication, String userIdHeader) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        var currentUser = CurrentUser.get();
        if (currentUser.isPresent() && currentUser.get().userId() != null && !currentUser.get().userId().isBlank()) {
            return currentUser.get().userId();
        }
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return userIdHeader;
        }
        return null;
    }
}

