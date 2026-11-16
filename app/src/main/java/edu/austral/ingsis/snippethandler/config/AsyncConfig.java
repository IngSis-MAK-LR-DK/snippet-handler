package edu.austral.ingsis.snippethandler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background work: @Async listeners and @Scheduled checks. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {}
