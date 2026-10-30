package com.swiftdrop.parcel.address;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AddressControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AddressRepository addressRepository;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "usr-cust-1", roles = "CUSTOMER")
    @DisplayName("Customer can create and list saved addresses")
    void shouldCreateAndListAddresses() throws Exception {
        CreateAddressRequest request = new CreateAddressRequest(
                "Home",
                "45 Galle Road",
                "Apt 3B",
                "Colombo 03",
                "Colombo",
                "00300",
                true
        );

        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.label", is("Home")))
                .andExpect(jsonPath("$.district", is("Colombo")))
                .andExpect(jsonPath("$.isDefault", is(true)));

        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("Home")));
    }

    @Test
    @WithMockUser(username = "usr-cust-1", roles = "CUSTOMER")
    @DisplayName("Creation fails with 400 when district is not in 25 approved districts")
    void shouldRejectInvalidDistrict() throws Exception {
        CreateAddressRequest request = new CreateAddressRequest(
                "Office",
                "100 Main St",
                null,
                "Gotham City",
                "InvalidDistrict",
                "12345",
                false
        );

        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Customer B cannot access Customer A's address")
    void shouldEnforceCustomerIsolation() throws Exception {
        Address address = new Address();
        address.setCustomerId("usr-cust-A");
        address.setLabel("Secret Home");
        address.setLine1("123 Secret St");
        address.setCity("Colombo");
        address.setDistrict("Colombo");
        address.setPostalCode("00100");
        address.setDefault(false);
        Address saved = addressRepository.save(address);

        // Accessing as Customer B
        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor userB =
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("usr-cust-B").roles("CUSTOMER");

        mockMvc.perform(get("/api/v1/addresses/" + saved.getId()).with(userB))
                .andExpect(status().isNotFound());

        CreateAddressRequest updateReq = new CreateAddressRequest(
                "Hacked Label",
                "Hacked",
                null,
                "Colombo",
                "Colombo",
                "00100",
                false
        );

        mockMvc.perform(put("/api/v1/addresses/" + saved.getId())
                        .with(userB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        // Address remains unchanged
        Address found = addressRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getLabel()).isEqualTo("Secret Home");
    }
}
