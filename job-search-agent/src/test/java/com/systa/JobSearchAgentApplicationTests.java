package com.systa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// No Mongo in this test, so don't try to seed the company and skill lists.
@SpringBootTest(properties = "suggestions.seed-on-startup=false")
class JobSearchAgentApplicationTests {

	@Test
	void contextLoads() {
	}

}
