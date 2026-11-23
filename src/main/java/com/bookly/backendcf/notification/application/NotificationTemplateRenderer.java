package com.bookly.backendcf.notification.application;

import java.util.Map;

public interface NotificationTemplateRenderer {

    String confirmation(Map<String, String> variables);

    String cancellation(Map<String, String> variables);

    /** Aviso al profesional de que una reserva de su agenda fue cancelada (HU-14). */
    String professionalCancellation(Map<String, String> variables);

    String rescheduling(Map<String, String> variables);
}
