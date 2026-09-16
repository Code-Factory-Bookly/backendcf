package com.bookly.backendcf.platform.application;

import java.util.UUID;

/**
 * Notifica al administrador que la plataforma quedó configurada.
 *
 * <p>La implementación vigente solo deja constancia en el log. Cuando exista un proveedor de
 * correo, basta con sustituir el adaptador: el caso de uso no cambia.
 */
public interface WelcomeNotificationPort {

    void sendPlatformWelcome(UUID platformId, String platformName, String adminEmail);
}
