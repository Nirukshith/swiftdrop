package com.swiftdrop.parcel;

import com.swiftdrop.parcel.address.AddressController;
import com.swiftdrop.parcel.address.AddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AddressController.class)
@Import(SecurityConfig.class)
class ParcelSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddressService addressService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/v1/addresses returns 401 Unauthorized when unauthenticated")
    void addresses_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/addresses succeeds when authenticated with valid CUSTOMER JWT")
    void addresses_authenticatedWithJwt() throws Exception {
        when(addressService.listAddresses(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/addresses")
                        .with(jwt().jwt(builder -> builder
                                .subject("customer-uuid-1")
                                .claim("email", "cust@example.com")
                                .claim("role", "CUSTOMER")
                                .claim("roles", List.of("ROLE_CUSTOMER")))))
                .andExpect(status().isOk());
    }
}
