package com.bookly.backendcf.notification.application;

import java.util.Map;

public interface NotificationTemplateRenderer {

    String confirmation(Map<String, String> variables);

    String cancellation(Map<String, String> variables);

    String rescheduling(Map<String, String> variables);
}
