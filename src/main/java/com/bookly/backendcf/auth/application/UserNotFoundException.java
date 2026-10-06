package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.shared.error.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(UUID userId) {
        super("USER_NOT_FOUND", "El usuario no existe", Map.of("userId", userId.toString()));
    }
}
