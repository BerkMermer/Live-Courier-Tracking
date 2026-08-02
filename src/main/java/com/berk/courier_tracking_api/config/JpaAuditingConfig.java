package com.berk.courier_tracking_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables JPA auditing outside the main application class so @WebMvcTest slices stay lightweight. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
