package com.swiftdrop.integration.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpAuthProvisioningClientTest {

    @Test
    void recoveryUsesTheAgreedStableOperationAndServiceCredential() {
        var op=java.util.UUID.randomUUID(); var merchant=java.util.UUID.randomUUID(); var replacement=java.util.UUID.randomUUID();
        String response="{\"operationId\":\""+op+"\",\"merchantId\":\""+merchant+"\",\"clientId\":\"same-client\",\"credentialRecoveryRequired\":true}";
        mockServer.expect(requestTo("http://localhost:8081/internal/v1/merchant-provisioning/"+op))
                .andExpect(method(HttpMethod.PUT)).andExpect(header("X-Internal-Service-Token","swiftdrop-internal-merchant-secret-2026"))
                .andExpect(content().json("{\"merchantId\":\""+merchant+"\",\"email\":\"shop@example.test\",\"fullName\":\"Owner\",\"phoneNumber\":\"+94771234567\",\"expectedUserId\":null,\"actorId\":\"verified-admin\"}",true))
                .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://localhost:8081/internal/v1/merchant-provisioning/"+op))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://localhost:8081/internal/v1/merchant-provisioning/"+op+"/credentials"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json("{\"replacementId\":\""+replacement+"\",\"actorId\":\"verified-admin\",\"confirmReplacement\":true}",true))
                .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        var result=client.provision(op,new AuthProvisioningClient.ProvisioningRequest(merchant,"shop@example.test","Owner","+94771234567",null,"verified-admin"));
        assertThat(result.credentialRecoveryRequired()).isTrue();
        assertThat(client.provisioningStatus(op).clientId()).isEqualTo("same-client");
        assertThat(client.replaceCredential(op,new AuthProvisioningClient.ReplacementRequest(replacement,"verified-admin",true)).clientSecret()).isNull();
        mockServer.verify();
    }

    @Test
    void provisioningResponseStringDoesNotExposeSecretOrContactData() {
        var response=new AuthProvisioningClient.ProvisioningResponse(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),
                new AuthProvisioningClient.AuthUserResponse("u","private@example.test","Private","private-phone","MERCHANT","m",true),"client","one-time-private-secret",false);
        assertThat(response.toString()).doesNotContain("one-time-private-secret","private@example.test","private-phone");
    }

    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private HttpAuthProvisioningClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        builder.defaultHeader("X-Internal-Service-Token", "swiftdrop-internal-merchant-secret-2026");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new HttpAuthProvisioningClient(builder.build());
    }

    @Test
    @DisplayName("createMerchantUser sends X-Internal-Service-Token header")
    void createMerchantUser_sendsInternalToken() throws Exception {
        AuthProvisioningClient.AuthUserResponse response = new AuthProvisioningClient.AuthUserResponse(
                "usr-1", "mch@test.com", "Mch", "+94771234567", "MERCHANT", "mch-1", false);

        mockServer.expect(requestTo("http://localhost:8081/internal/v1/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "swiftdrop-internal-merchant-secret-2026"))
                .andExpect(content().json("""
                        {"email":"mch@test.com","password":null,"fullName":"Mch","phoneNumber":"+94771234567",
                         "role":"MERCHANT","merchantId":"mch-1","enabled":false}
                        """, true))
                .andRespond(withStatus(HttpStatus.CREATED).body(objectMapper.writeValueAsString(response))
                        .contentType(MediaType.APPLICATION_JSON));

        AuthProvisioningClient.AuthCreateUserRequest request = new AuthProvisioningClient.AuthCreateUserRequest(
                "mch@test.com", null, "Mch", "+94771234567", "MERCHANT", "mch-1", false);

        AuthProvisioningClient.AuthUserResponse actual = client.createMerchantUser(request);
        assertThat(actual.id()).isEqualTo("usr-1");
    }

    @Test
    @DisplayName("activateUser sends X-Internal-Service-Token header")
    void activateUser_sendsInternalToken() throws Exception {
        AuthProvisioningClient.AuthUserResponse response = new AuthProvisioningClient.AuthUserResponse(
                "usr-1", "mch@test.com", "Mch", "+94771234567", "MERCHANT", "mch-1", true);

        mockServer.expect(requestTo("http://localhost:8081/internal/v1/users/usr-1/activate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "swiftdrop-internal-merchant-secret-2026"))
                .andExpect(content().string(""))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        AuthProvisioningClient.AuthUserResponse actual = client.activateUser("usr-1");
        assertThat(actual.enabled()).isTrue();
    }

    @Test
    @DisplayName("createClient sends X-Internal-Service-Token header")
    void createClient_sendsInternalToken() throws Exception {
        AuthProvisioningClient.AuthClientCredentialsResponse response = new AuthProvisioningClient.AuthClientCredentialsResponse(
                "client-1", "secret-1", "mch-1", "Store Client", List.of("parcels:create"));

        mockServer.expect(requestTo("http://localhost:8081/internal/v1/clients"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "swiftdrop-internal-merchant-secret-2026"))
                .andExpect(content().json("""
                        {"merchantId":"mch-1","clientName":"Store Client","scopes":["parcels:create"]}
                        """, true))
                .andRespond(withStatus(HttpStatus.CREATED).body(objectMapper.writeValueAsString(response))
                        .contentType(MediaType.APPLICATION_JSON));

        AuthProvisioningClient.AuthCreateClientRequest request = new AuthProvisioningClient.AuthCreateClientRequest(
                "mch-1", "Store Client", List.of("parcels:create"));

        AuthProvisioningClient.AuthClientCredentialsResponse actual = client.createClient(request);
        assertThat(actual.clientId()).isEqualTo("client-1");
        assertThat(actual.clientSecret()).isEqualTo("secret-1");
    }
}
