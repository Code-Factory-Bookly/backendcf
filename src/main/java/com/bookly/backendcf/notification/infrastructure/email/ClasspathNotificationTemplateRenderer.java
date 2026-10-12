package com.bookly.backendcf.notification.infrastructure.email;

import com.bookly.backendcf.notification.application.NotificationTemplateRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ClasspathNotificationTemplateRenderer implements NotificationTemplateRenderer {

    private static final String CONFIRMATION = "templates/email/booking-confirmation.txt";
    private static final String CANCELLATION = "templates/email/booking-cancellation.txt";
    private static final String RESCHEDULING = "templates/email/booking-rescheduling.txt";

    @Override
    public String confirmation(Map<String, String> variables) {
        return render(CONFIRMATION, variables);
    }

    @Override
    public String cancellation(Map<String, String> variables) {
        return render(CANCELLATION, variables);
    }

    @Override
    public String rescheduling(Map<String, String> variables) {
        return render(RESCHEDULING, variables);
    }

    private String render(String location, Map<String, String> variables) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(location)) {
            if (stream == null) {
                throw new IllegalStateException("Plantilla de correo no encontrada: " + location);
            }
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return template;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer la plantilla de correo", exception);
        }
    }
}
