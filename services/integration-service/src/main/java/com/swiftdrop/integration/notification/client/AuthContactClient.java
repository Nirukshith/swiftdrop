package com.swiftdrop.integration.notification.client;

import com.swiftdrop.integration.notification.dto.UserContactDto;
import java.util.Optional;

public interface AuthContactClient {
    Optional<UserContactDto> getContact(String userId);
}

