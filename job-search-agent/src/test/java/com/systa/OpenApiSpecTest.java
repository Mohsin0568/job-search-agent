package com.systa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keeps openapi.yaml (the whole REST API in one file) in step with the controllers.
 * When a controller or DTO changes, this fails until the file is regenerated:
 *   ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true
 */
@SpringBootTest(properties = {
        "OPEN_API_KEY=test-openai-key",
        "FIRECRAWL_API_KEY=test-firecrawl-key",
        "COGNITO_ISSUER_URI=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "COGNITO_CLIENT_ID=test-client",
        "RESUME_S3_BUCKET=test-resumes",
        "RESUME_S3_REGION=eu-west-2",
        "spring.ai.mcp.client.initialized=false"
})
@AutoConfigureMockMvc
@Import(MongoTestContainer.class)
class OpenApiSpecTest {

    private static final Path SPEC_FILE = Path.of("openapi.yaml");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiYamlMatchesTheControllers() throws Exception {
        final String generated = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        if (Boolean.getBoolean("openapi.update") || !Files.exists(SPEC_FILE)) {
            Files.writeString(SPEC_FILE, generated, StandardCharsets.UTF_8);
        }

        assertThat(read(SPEC_FILE))
                .as("openapi.yaml is out of date. Regenerate it with: "
                        + "./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true")
                .isEqualTo(generated);
    }

    private static String read(final Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
