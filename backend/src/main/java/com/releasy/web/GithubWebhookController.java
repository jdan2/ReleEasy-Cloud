package com.releasy.web;

import com.releasy.application.webhook.GithubWebhookService;
import com.releasy.application.webhook.WebhookResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe los webhooks de GitHub. El cuerpo se lee como bytes sin procesar, porque la firma HMAC se
 * calcula sobre el contenido exacto que envió GitHub.
 *
 * <p>Respuestas: 202 si se crearon releases, 200 si el evento se ignoró (con el motivo), 401 si la
 * firma no es válida y 400 si el cuerpo está mal formado.
 */
@RestController
@RequestMapping("/api/webhooks/github")
public class GithubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookController.class);

    private final GithubWebhookService webhookService;

    public GithubWebhookController(GithubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    ResponseEntity<WebhookResult> receive(
            @RequestHeader(name = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody(required = false) byte[] body) {
        WebhookResult result = webhookService.handle(eventType, signature, body == null ? new byte[0] : body);
        log.info("Webhook {} ({}): {} - {}", deliveryId, eventType, result.outcome(), result.message());
        HttpStatus status = result.outcome() == WebhookResult.Outcome.ACCEPTED ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
