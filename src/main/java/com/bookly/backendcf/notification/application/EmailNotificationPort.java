package com.bookly.backendcf.notification.application;

public interface EmailNotificationPort {

    void send(EmailMessage message);

    record EmailMessage(String recipient, String subject, String body) {
    }
}
