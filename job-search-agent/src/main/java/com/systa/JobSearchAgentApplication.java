package com.systa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobSearchAgentApplication {

	public static final String JOB_PROFILE = "job";

	public static void main(String[] args) {
		final ConfigurableApplicationContext context = SpringApplication.run(JobSearchAgentApplication.class, args);

		// Job mode has no web server to keep it alive: the daily search has already run by now, so exit
		// with its exit code rather than wait on HTTP client threads that may still be around.
		if (context.getEnvironment().matchesProfiles(JOB_PROFILE)) {
			System.exit(SpringApplication.exit(context));
		}
	}

}
