package com.bookly.backendcf.organization.application;

import java.util.UUID;

/**
 * Notifica al administrador que su organización quedó activada.
 *
 * <p>La implementación vigente solo deja constancia en el log. Cuando exista un proveedor de
 * correo, basta con sustituir el adaptador: el caso de uso no cambia.
 */
public interface WelcomeNotificationPort {

    void sendOrganizationWelcome(UUID organizationId, String organizationName, String adminEmail);
}
