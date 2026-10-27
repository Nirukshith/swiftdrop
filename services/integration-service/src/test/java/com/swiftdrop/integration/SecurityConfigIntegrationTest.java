package com.swiftdrop.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityConfigIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void merchantRegistrationPostIsPublic() throws Exception {
        // The controller is added in the dependent M6 onboarding PR. A 404 here confirms
        // the request passed the security filter instead of being rejected as unauthenticated.
        mockMvc.perform(post("/api/v1/merchants/register"))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherMerchantEndpointsRemainAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/webhooks/settings"))
            .andExpect(status().isForbidden());
    }
}
