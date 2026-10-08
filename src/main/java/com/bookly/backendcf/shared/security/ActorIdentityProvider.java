package com.bookly.backendcf.shared.security;

import java.util.UUID;

/** Provides the authenticated actor without coupling application services to Spring Security. */
public interface ActorIdentityProvider {

    UUID currentActorId();
}
