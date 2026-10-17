package com.bookly.backendcf.shared.configuration;

import java.time.DayOfWeek;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

@Configuration
public class JacksonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer strictJsonTypes() {
        return builder -> builder
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(
                        DayOfWeek.class,
                        config -> config.setCoercion(
                                CoercionInputShape.Integer,
                                CoercionAction.Fail
                        )
                );
    }
}
