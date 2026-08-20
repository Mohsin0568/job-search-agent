package com.systa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JobSearchAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobSearchAgentApplication.class, args);
	}

}
