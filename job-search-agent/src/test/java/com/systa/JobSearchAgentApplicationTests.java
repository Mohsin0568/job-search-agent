package com.systa;

import com.systa.repository.CompanyRepository;
import com.systa.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;

// Placeholder credentials so the test needs no environment variables, and the MCP client is left
// uninitialised so starting the context doesn't call Firecrawl.
@SpringBootTest(properties = {
		"OPEN_API_KEY=test-openai-key",
		"FIRECRAWL_API_KEY=test-firecrawl-key",
		"COGNITO_ISSUER_URI=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
		"COGNITO_CLIENT_ID=test-client",
		"spring.ai.mcp.client.initialized=false"
})
@Import(MongoTestContainer.class)
class JobSearchAgentApplicationTests {

	@Autowired
	private CompanyRepository companyRepository;

	@Autowired
	private SkillRepository skillRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void seedsTheSuggestionListsOnStartup() {
		assertThat(companyRepository.count()).isPositive();
		assertThat(skillRepository.count()).isPositive();
		assertThat(companyRepository.findBySearchKeyPrefix("bank", Limit.of(5))).isNotEmpty();
	}

}
