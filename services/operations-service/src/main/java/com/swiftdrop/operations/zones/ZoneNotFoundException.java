package com.swiftdrop.operations.zones;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ZoneNotFoundException extends RuntimeException {

    public ZoneNotFoundException(UUID id) {
        super("Zone not found: " + id);
    }
}
