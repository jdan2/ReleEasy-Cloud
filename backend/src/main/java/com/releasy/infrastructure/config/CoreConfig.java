package com.releasy.infrastructure.config;

import com.releasy.application.tenant.AccountCreationPoller;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

/**
 * Beans transversales: reloj, planificador de tareas y la tarea periódica que consulta la creación de
 * cuentas AWS.
 */
@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("releasy-scheduler-");
        return scheduler;
    }

    @Bean
    ApplicationRunner scheduleAccountCreationPolling(TaskScheduler scheduler, AccountCreationPoller poller,
                                                     ReleasyProperties properties) {
        return args -> scheduler.scheduleWithFixedDelay(poller::pollPendingAccounts,
                properties.aws().accountCreationPollInterval());
    }
}
