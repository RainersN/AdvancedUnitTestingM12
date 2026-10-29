package lv.bootcamp.shelter.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientPartnerRegistryClientTest {

    private MockWebServer server;
    private RestClientPartnerRegistryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new RestClientPartnerRegistryClient(
                RestClient.builder(), stripTrailingSlash(server.url("/").toString()));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void lookup_shouldParseSuccessfulResponseAndCallCorrectPath() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"registered\":true,\"partnerShelterName\":\"Riga Paws\"}"));

        PartnerRegistryResult result = client.lookup("CHIP-123");

        assertThat(result.registered()).isTrue();
        assertThat(result.partnerShelterName()).isEqualTo("Riga Paws");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/registry/CHIP-123");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void lookup_shouldParseNotRegisteredResponse() {
        server.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"registered\":false,\"partnerShelterName\":null}"));

        PartnerRegistryResult result = client.lookup("CHIP-999");

        assertThat(result.registered()).isFalse();
        assertThat(result.partnerShelterName()).isNull();
    }

    @Test
    void lookup_shouldThrowWhenPartnerFailsWith500() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.lookup("CHIP-123"))
                .isInstanceOf(HttpServerErrorException.InternalServerError.class)
                .satisfies(ex -> assertThat(((RestClientResponseException) ex).getStatusCode().value())
                        .isEqualTo(500));
    }

    @Test
    void lookup_shouldThrowWhenPartnerIsUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client.lookup("CHIP-123"))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }
}