package io.github.sebastianbadillo.linkly.clicks;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on {@code @Scheduled}; without this, the click flush would silently never run. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
