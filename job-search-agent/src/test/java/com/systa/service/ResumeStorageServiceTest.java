package com.systa.service;

import com.systa.config.ResumeProperties;
import com.systa.exception.ResumeStorageException;
import com.systa.model.ResumeDownload;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against S3Mock, an S3-compatible store in Docker, so the real S3 calls and download links are exercised. */
@Testcontainers
class ResumeStorageServiceTest {

    private static final String BUCKET = "test-resumes";
    private static final Duration LINK_TTL = Duration.ofMinutes(5);
    private static final byte[] PDF = TestPdfs.withText("Senior Java Developer");

    private static final int S3_PORT = 9090;

    @Container
    private static final GenericContainer<?> S3_MOCK =
            new GenericContainer<>("adobe/s3mock:5.2.0").withExposedPorts(S3_PORT);

    private static S3Client s3Client;
    private static S3Presigner s3Presigner;

    @BeforeAll
    static void connect() {
        final StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test-access-key", "test-secret-key"));
        final URI endpoint = URI.create("http://" + S3_MOCK.getHost() + ":" + S3_MOCK.getMappedPort(S3_PORT));
        // S3Mock serves buckets as paths (host/bucket/key), not as subdomains.
        final S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        s3Client = S3Client.builder().endpointOverride(endpoint).region(Region.EU_WEST_2)
                .credentialsProvider(credentials).serviceConfiguration(pathStyle)
                .httpClient(UrlConnectionHttpClient.create()).build();
        s3Presigner = S3Presigner.builder().endpointOverride(endpoint).region(Region.EU_WEST_2)
                .credentialsProvider(credentials).serviceConfiguration(pathStyle).build();
        s3Client.createBucket(request -> request.bucket(BUCKET));
    }

    @AfterAll
    static void disconnect() {
        s3Presigner.close();
        s3Client.close();
    }

    @Test
    void storesAFileUnderItsKey() {
        serviceFor(BUCKET).store("resumes/user-1/v1/a.pdf", PDF, "application/pdf");

        assertThat(s3Client.getObjectAsBytes(request -> request.bucket(BUCKET).key("resumes/user-1/v1/a.pdf"))
                .asByteArray()).isEqualTo(PDF);
        assertThat(s3Client.headObject(request -> request.bucket(BUCKET).key("resumes/user-1/v1/a.pdf"))
                .contentType()).isEqualTo("application/pdf");
    }

    @Test
    void keepsEveryVersion_eachUnderItsOwnKey() {
        final byte[] secondVersion = TestPdfs.withText("Lead Java Developer");
        final ResumeStorageService service = serviceFor(BUCKET);

        service.store("resumes/user-2/v1/a.pdf", PDF, "application/pdf");
        service.store("resumes/user-2/v2/b.pdf", secondVersion, "application/pdf");

        assertThat(s3Client.getObjectAsBytes(request -> request.bucket(BUCKET).key("resumes/user-2/v1/a.pdf"))
                .asByteArray()).isEqualTo(PDF);
        assertThat(s3Client.getObjectAsBytes(request -> request.bucket(BUCKET).key("resumes/user-2/v2/b.pdf"))
                .asByteArray()).isEqualTo(secondVersion);
    }

    @Test
    void givesALinkThatDownloadsTheFileUnderItsOriginalName_withoutAnyCredentials() throws Exception {
        final ResumeStorageService service = serviceFor(BUCKET);
        service.store("resumes/user-3/v1/a.pdf", PDF, "application/pdf");

        final Instant before = Instant.now();
        final ResumeDownload link = service.downloadLink("resumes/user-3/v1/a.pdf", "Jane Doe (2026).pdf",
                "application/pdf");

        assertThat(link.expiresAt()).isBetween(before.plus(LINK_TTL).minusSeconds(5), before.plus(LINK_TTL).plusSeconds(5));
        final HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(link.url())).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(PDF);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/pdf");
        assertThat(response.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"Jane Doe (2026).pdf\"");
    }

    @Test
    void reportsAStorageFailure_whenTheBucketCannotBeWrittenTo() {
        assertThatThrownBy(() -> serviceFor("no-such-bucket").store("resumes/user-1/v1/a.pdf", PDF, "application/pdf"))
                .isInstanceOf(ResumeStorageException.class)
                .hasMessageContaining("resumes/user-1/v1/a.pdf");
    }

    private static ResumeStorageService serviceFor(final String bucket) {
        return new ResumeStorageService(s3Client, s3Presigner,
                new ResumeProperties(new ResumeProperties.S3(bucket, null), LINK_TTL));
    }
}
