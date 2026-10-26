package com.releasy.infrastructure.entrypoints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de extremo a extremo de la API en modo mock: registro, login, autorización por rol, y el
 * flujo del webhook de GitHub hasta un release SUCCEEDED.
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
    WebhookSignatureVerifier verifier;

    @Test
    void laApiExigeTokenPeroElHealthYElWebhookNo() throws Exception {
        mvc.perform(get("/api/me/tenant"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/me/tenant").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void registroYLoginDeUnCliente() throws Exception {
        String email = uniqueEmail();
        JsonNode session = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Ferretería López", email, "secreta123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("CLIENT"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        String tenantId = session.at("/user/tenantId").asText();

        // El mismo email no se puede registrar dos veces
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Otra", email.toUpperCase(), "secreta123")))
                .andExpect(status().isConflict());

        // Login: contraseña incorrecta y correcta
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "incorrecta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"));
        String token = login(email, "secreta123");

        // El cliente ve su propio tenant, todavía sin conexiones
        mvc.perform(get("/api/me/tenant").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantId))
                .andExpect(jsonPath("$.name").value("Ferretería López"))
                .andExpect(jsonPath("$.aws.status").value("NOT_CONNECTED"))
                .andExpect(jsonPath("$.aws.externalId").isNotEmpty())
                .andExpect(jsonPath("$.aws.platformAccountId").value("111122223333"))
                .andExpect(jsonPath("$.github.status").value("NOT_CONNECTED"))
                .andExpect(jsonPath("$.appCount").value(0));

        // ...pero no puede usar las rutas del administrador
        mvc.perform(get("/api/tenants").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorVeLosClientesPeroNoTieneRutasDeCliente() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("Clínica Sonrisa", email, "secreta123"))).andExpect(status().isCreated());

        String adminToken = login("admin", "test-password");
        mvc.perform(get("/api/tenants").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + email + "')].aws.status").value("NOT_CONNECTED"));
        mvc.perform(get("/api/me/tenant").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void validaLasPeticionesDeRegistro() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("A", "no-es-email", "corta")))
                .andExpect(status().isBadRequest());
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
    void flujoCompletoDesdeElRegistroHastaElReleaseDesplegado() throws Exception {
        // 1. El cliente se registra: todavía no puede crear apps
        String token = register("Ferretería López");
        mvc.perform(authed(post("/api/me/apps"), token).content(appBody("{}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Conecta primero tu cuenta de AWS"));

        // 2. Conecta su cuenta AWS (STS simulado: un ARN con "fail" se rechaza)
        mvc.perform(authed(post("/api/me/aws-connection"), token).content("{\"roleArn\":\"no-es-un-arn\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(post("/api/me/aws-connection"), token)
                        .content("{\"roleArn\":\"arn:aws:iam::123456789012:role/fail\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("AccessDenied")));
        mvc.perform(authed(post("/api/me/aws-connection"), token)
                        .content("{\"roleArn\":\"arn:aws:iam::123456789012:role/ReleEasyDeployRole\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aws.status").value("CONNECTED"))
                .andExpect(jsonPath("$.aws.accountId").value("123456789012"));

        // 3. Instala y autoriza la GitHub App; GitHub vuelve con un código OAuth (GitHub simulado: el código
        //    determina la instalación del usuario)
        mvc.perform(authed(get("/api/me/github/repositories"), token)).andExpect(status().isConflict());
        String code = "codigo-" + UUID.randomUUID();
        mvc.perform(authed(post("/api/me/github-connection"), token).content("{\"code\":\"" + code + "\",\"installationId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no tiene acceso a la instalación 1")));
        mvc.perform(authed(post("/api/me/github-connection"), token).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.github.status").value("CONNECTED"))
                .andExpect(jsonPath("$.github.accountLogin").value("mi-empresa"))
                .andExpect(jsonPath("$.github.authorizedBy").value("usuario-demo"))
                .andExpect(jsonPath("$.github.installationId").isNumber());
        mvc.perform(authed(get("/api/me/github/repositories"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].fullName").value("mi-empresa/api-pedidos"))
                .andExpect(jsonPath("$[2].private").value(true))
                .andExpect(jsonPath("$[2].branches[0]").value("master"));

        // La misma instalación no se puede vincular a otro cliente
        mvc.perform(authed(post("/api/me/github-connection"), register("Otra Empresa"))
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isConflict());

        // 4. Elige la plantilla Spring Boot y crea la app (opciones por defecto + email de avisos)
        JsonNode template = json(mvc.perform(authed(get("/api/templates/springboot-api"), token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode options = objectMapper.createObjectNode();
        template.path("optionGroups").forEach(g -> g.path("fields").forEach(f -> options.set(f.path("key").asText(), f.get("default"))));

        mvc.perform(authed(post("/api/me/apps"), token).content(appBody(options.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Email para alarmas: es obligatorio si activas alguna alarma."));

        options.put("notification_email", "ops@ferreteria-lopez.es");
        JsonNode app = json(mvc.perform(authed(post("/api/me/apps"), token).content(appBody(options.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateId").value("springboot-api"))
                .andExpect(jsonPath("$.repository").value("mi-empresa/api-pedidos"))
                .andExpect(jsonPath("$.options.instance_type").value("t3.small"))
                .andExpect(jsonPath("$.usage.apiRequestsPerMonth").value(1000000.0))
                .andExpect(jsonPath("$.url").value("https://pedidos-ferreteria-lopez.apps.releasy.local"))
                .andReturn().getResponse().getContentAsString());
        String appId = app.get("id").asText();

        // 5. Al crearla se despliega el último commit de la rama
        JsonNode first = waitForTerminalRelease(appId, token, 1);
        assertThat(first.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(first.get("url").asText()).endsWith(".cloudfront.net");

        // 6. GitHub notifica un merge a master y se despliega otro release
        byte[] push = """
                {"ref":"refs/heads/master","after":"0123456789abcdef0123456789abcdef01234567","deleted":false,
                 "repository":{"full_name":"mi-empresa/api-pedidos"},"head_commit":{"message":"Merge pull request #1"}}
                """.getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", verifier.sign(push))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(push))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));
        JsonNode second = waitForTerminalRelease(appId, token, 2);
        assertThat(second.get("commitSha").asText()).isEqualTo("0123456789abcdef0123456789abcdef01234567");
        assertThat(second.get("status").asText()).isEqualTo("SUCCEEDED");

        // 7. Solo el propietario y el administrador ven la app
        mvc.perform(authed(get("/api/me/apps"), token)).andExpect(jsonPath("$[0].id").value(appId));
        mvc.perform(authed(get("/api/apps/" + appId), register("Curiosa SL"))).andExpect(status().isNotFound());
        mvc.perform(authed(get("/api/apps/" + appId), login("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(second.get("url").asText()));
    }

    /** Espera a que haya {@code count} releases y el más reciente haya terminado. */
    private JsonNode waitForTerminalRelease(String appId, String token, int count) throws Exception {
        for (int i = 0; i < 50; i++) {
            JsonNode releases = json(mvc.perform(authed(get("/api/apps/" + appId + "/releases"), token))
                    .andReturn().getResponse().getContentAsString());
            if (releases.size() >= count) {
                String status = releases.get(0).get("status").asText();
                if (status.equals("SUCCEEDED") || status.equals("FAILED")) {
                    return releases.get(0);
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("El release no llegó a un estado final");
    }

    private String register(String companyName) throws Exception {
        return json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(companyName, uniqueEmail(), "secreta123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private static String appBody(String options) {
        return """
                {"name":"pedidos","templateId":"springboot-api","repository":"mi-empresa/api-pedidos","branch":"master",
                 "options":%s,"usage":{"apiRequestsPerMonth":1000000,"responseSizeKb":10}}""".formatted(options);
    }

    private String login(String email, String password) throws Exception {
        return json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** El contexto de Spring se comparte entre pruebas: cada una usa un email distinto. */
    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String registerBody(String companyName, String email, String password) {
        return "{\"companyName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(companyName, email, password);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
