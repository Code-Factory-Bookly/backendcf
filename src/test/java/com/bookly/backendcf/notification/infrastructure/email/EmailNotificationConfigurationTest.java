package com.bookly.backendcf.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.notification.application.EmailNotificationPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifica que la eleccion del adaptador de email queda en un unico punto
 * (java:S9352) y que en cada escenario existe exactamente un bean
 * {@link EmailNotificationPort}.
 */
class EmailNotificationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(EmailNotificationConfiguration.class);

    @Test
    void sinPropiedadRegistraSoloElAdaptadorDeLogging() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(EmailNotificationPort.class))
                    .hasSize(1)
                    .allSatisfy((name, bean) ->
                            assertThat(bean).isInstanceOf(LoggingEmailNotificationAdapter.class));
        });
    }

    @Test
    void conProviderLoggingRegistraSoloElAdaptadorDeLogging() {
        runner.withPropertyValues("notification.email.provider=logging")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(EmailNotificationPort.class))
                            .hasSize(1)
                            .allSatisfy((name, bean) ->
                                    assertThat(bean).isInstanceOf(LoggingEmailNotificationAdapter.class));
                });
    }

    @Test
    void conProviderSmtpRegistraSoloElAdaptadorSmtp() {
        runner.withPropertyValues(
                        "notification.email.provider=smtp",
                        "notification.email.from=no-reply@bookly.test",
                        "spring.mail.host=localhost",
                        "spring.mail.port=2525")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(EmailNotificationPort.class))
                            .hasSize(1)
                            .allSatisfy((name, bean) ->
                                    assertThat(bean).isInstanceOf(SmtpEmailNotificationAdapter.class));
                });
    }
}
