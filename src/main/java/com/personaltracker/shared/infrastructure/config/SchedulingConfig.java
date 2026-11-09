package com.personaltracker.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas ({@code @Scheduled}) de los módulos, como la cola de la captura.
 * Por ahora se despliega una sola instancia (PROJECT_CONTEXT §16).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}
