package com.swiftdrop.integration.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.integration.notification.dto.UserContactDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestAuthContactClientTest {

    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should successfully retrieve user contact from auth service")
    void shouldRetrieveContactSuccessfully() throws Exception {
        RestAuthContactClient client = new RestAuthContactClient(restClient, false);
        UserContactDto expected = new UserContactDto(
                "usr-101",
                "Jane Perera",
                "jane.p@example.com",
                "+94771234567",
                "CUSTOMER"
        );

        mockServer.expect(requestTo("http://localhost:8081/internal/v1/users/usr-101/contact"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        Optional<UserContactDto> actual = client.getContact("usr-101");

        assertThat(actual).isPresent();
        assertThat(actual.get().email()).isEqualTo("jane.p@example.com");
        assertThat(actual.get().fullName()).isEqualTo("Jane Perera");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should use fallback contact on 404 when fallback is enabled")
    void shouldReturnFallbackContactOn404() {
        RestAuthContactClient client = new RestAuthContactClient(restClient, true);

        mockServer.expect(requestTo("http://localhost:8081/internal/v1/users/usr-404/contact"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<UserContactDto> actual = client.getContact("usr-404");

        assertThat(actual).isPresent();
        assertThat(actual.get().userId()).isEqualTo("usr-404");
        assertThat(actual.get().email()).contains("usr-404");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return empty optional on 404 when fallback is disabled")
    void shouldReturnEmptyOn404WithoutFallback() {
        RestAuthContactClient client = new RestAuthContactClient(restClient, false);

        mockServer.expect(requestTo("http://localhost:8081/internal/v1/users/usr-404/contact"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<UserContactDto> actual = client.getContact("usr-404");

        assertThat(actual).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Should retry on 500 server error and throw ContactResolutionException when exhausted without fallback")
    void shouldRetryAndFailWhenExhaustedWithoutFallback() {
        RestAuthContactClient client = new RestAuthContactClient(restClient, false);

        // Expect 3 attempts
        for (int i = 0; i < 3; i++) {
            mockServer.expect(requestTo("http://localhost:8081/internal/v1/users/usr-500/contact"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        }

        assertThatThrownBy(() -> client.getContact("usr-500"))
                .isInstanceOf(ContactResolutionException.class)
                .hasMessageContaining("Failed to resolve contact");

        mockServer.verify();
    }
}

