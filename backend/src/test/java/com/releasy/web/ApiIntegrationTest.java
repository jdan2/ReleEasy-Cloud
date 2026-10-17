package com.releasy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.tenant.AccountCreationPoller;
import com.releasy.application.webhook.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de extremo a extremo de la API en modo mock: alta de cliente, activación de la cuenta, alta
 * de aplicación, webhook de GitHub y avance del release hasta SUCCEEDED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AccountCreationPoller poller;
    @Autowired
    WebhookSignatureVerifier verifier;

    @Test
    void laApiExigeAutenticacionPeroElHealthYElWebhookNo() throws Exception {
        mvc.perform(get("/api/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/tenants").with(httpBasic("admin", "incorrecta"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tenants").with(httpBasic("admin", "test-password"))).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void webhookConFirmaInvalidaDevuelve401() throws Exception {
        mvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validaLasPeticionesDeAlta() throws Exception {
        mvc.perform(authed(post("/api/tenants")).content("{\"name\":\"\",\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void flujoCompletoDesdeElAltaHastaElReleaseDesplegado() throws Exception {
        // 1. Alta del cliente: la cuenta AWS queda en creación
        JsonNode tenant = json(mvc.perform(authed(post("/api/tenants"))
                        .content("{\"name\":\"Ferretería López\",\"email\":\"it@ferreteria-lopez.es\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATING"))
                .andReturn().getResponse().getContentAsString());
        String tenantId = tenant.get("id").asText();

        // 2. Sin cuenta activa no se pueden registrar aplicaciones
        String appBody = "{\"name\":\"tienda\",\"repository\":\"FerreteriaLopez/Tienda\",\"plan\":\"BASIC\"}";
        mvc.perform(authed(post("/api/tenants/" + tenantId + "/apps")).content(appBody)).andExpect(status().isConflict());

        // 3. El poller detecta que AWS terminó de crear la cuenta
        poller.pollPendingAccounts();
        mvc.perform(authed(get("/api/tenants/" + tenantId)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountId").isNotEmpty());

        // 4. Alta de la aplicación con URL provisional y rama por defecto
        JsonNode app = json(mvc.perform(authed(post("/api/tenants/" + tenantId + "/apps")).content(appBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branch").value("master"))
                .andExpect(jsonPath("$.repository").value("ferreterialopez/tienda"))
                .andExpect(jsonPath("$.url").value("https://tienda-ferreteria-lopez.apps.releasy.local"))
                .andReturn().getResponse().getContentAsString());
        String appId = app.get("id").asText();

        // 5. GitHub notifica el merge a master
        byte[] push = """
                {"ref":"refs/heads/master","after":"0123456789abcdef0123456789abcdef01234567","deleted":false,
                 "repository":{"full_name":"FerreteriaLopez/Tienda"},"head_commit":{"message":"Merge pull request #1"}}
                """.getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", verifier.sign(push))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(push))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));

        // 6. El orquestador simulado lleva el release hasta SUCCEEDED y fija la URL real
        JsonNode release = waitForTerminalRelease(appId);
        assertThat(release.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(release.get("commitSha").asText()).isEqualTo("0123456789abcdef0123456789abcdef01234567");
        assertThat(release.get("url").asText()).endsWith(".cloudfront.net");

        mvc.perform(authed(get("/api/apps/" + appId)))
                .andExpect(jsonPath("$.url").value(release.get("url").asText()));
    }

    private JsonNode waitForTerminalRelease(String appId) throws Exception {
        for (int i = 0; i < 50; i++) {
            JsonNode releases = json(mvc.perform(authed(get("/api/apps/" + appId + "/releases")))
                    .andReturn().getResponse().getContentAsString());
            if (!releases.isEmpty()) {
                String status = releases.get(0).get("status").asText();
                if (status.equals("SUCCEEDED") || status.equals("FAILED")) {
                    return releases.get(0);
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("El release no llegó a un estado final");
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.with(httpBasic("admin", "test-password")).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
