package io.github.ianhndz14.fairline;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the background import jobs. Tests turn it off with fairline.scheduling.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "fairline.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig {}
