package com.fameen.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du client : enveloppe, en-têtes, réessais, erreurs typées.
 * Aucun réseau : {@link FakeHttpTransport} répond en mémoire.
 */
class FameenMessagingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String MESSAGE_ENVELOPE = """
            {"success":true,"data":{"sid":"msg_123","status":"queued","channel":"sms",
             "to":"+224620000000","from":"FAMEEN","body":"Bonjour","segments":1,"credits":1,
             "error":null,"externalId":null,"statusCallback":null,
             "createdAt":"2026-07-12T10:00:00.000Z","sentAt":null,"deliveredAt":null},
             "message":"OK"}""";

    /** Builder de client de test : transport fake, backoff 1 ms, attente enregistrée. */
    private static FameenMessaging.Builder testClient(FakeHttpTransport transport, List<Long> sleeps) {
        return FameenMessaging.builder()
                .apiKey("fam_test_key")
                .transport(transport)
                .retryBase(Duration.ofMillis(1))
                .sleeper(sleeps::add);
    }

    private static SendMessageParams simpleSms() {
        return SendMessageParams.builder().to("+224620000000").message("Bonjour").build();
    }

    // ------------------------------------------------------------------
    // Enveloppe & mapping
    // ------------------------------------------------------------------

    @Test
    void smsSendUnwrapsEnvelopeAndReturnsMessageResource() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, MESSAGE_ENVELOPE);
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        MessageResource msg = client.sms().send(simpleSms());

        assertEquals("msg_123", msg.sid());
        assertEquals("queued", msg.status());
        assertEquals("sms", msg.channel());
        assertEquals("+224620000000", msg.to());
        assertEquals("FAMEEN", msg.from());
        assertEquals(1, msg.segments());
        assertEquals(1, msg.credits());
        assertNull(msg.error());
        assertNull(msg.deliveredAt());
        assertEquals(1, transport.requests().size());
    }

    @Test
    void bodyWithoutEnvelopeIsReturnedAsIs() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200,
                "{\"sid\":\"raw_1\",\"status\":\"sent\",\"channel\":\"sms\",\"to\":\"+224620000000\","
                        + "\"segments\":1,\"credits\":1,\"champInconnu\":true}");
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        MessageResource msg = client.messages().get("raw_1");

        assertEquals("raw_1", msg.sid());
        assertEquals("sent", msg.status());
    }

    @Test
    void walletBalanceMapsNestedBillingRecord() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, """
                {"success":true,"data":{"smsCredits":120,"waCredits":40,"emailCredits":300,
                 "billing":{"mode":"consumption","postpaid":true,"prepaidRequired":false,"sendingBlocked":false}},
                 "message":"OK"}""");
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        WalletBalance balance = client.wallet().balance();

        assertEquals(120, balance.smsCredits());
        assertEquals(40, balance.waCredits());
        assertEquals(300, balance.emailCredits());
        assertEquals("consumption", balance.billing().mode());
        assertTrue(balance.billing().postpaid());
        assertFalse(balance.billing().sendingBlocked());
        assertEquals("GET", transport.requests().get(0).method());
        assertEquals("/api/v1/wallet/balance", transport.requests().get(0).uri().getPath());
    }

    // ------------------------------------------------------------------
    // En-têtes : auth, idempotence, user-agent
    // ------------------------------------------------------------------

    @Test
    void sendsAuthIdempotencyAndUserAgentHeaders() throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, MESSAGE_ENVELOPE);
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        client.sms().send(SendMessageParams.builder()
                .to("+224620000000")
                .message("Bonjour")
                .idempotencyKey("idem-42")
                .build());

        FakeHttpTransport.RecordedRequest req = transport.requests().get(0);
        assertEquals("POST", req.method());
        assertEquals("https://fameenbusiness.com/api/v1/sms/send", req.uri().toString());
        assertEquals("Bearer fam_test_key", req.headers().get("Authorization"));
        assertEquals("application/json", req.headers().get("Accept"));
        assertEquals("fameen-messaging-java/1.0.3", req.headers().get("User-Agent"));
        assertEquals("idem-42", req.headers().get("Idempotency-Key"));
        assertEquals("application/json", req.headers().get("Content-Type"));

        JsonNode body = JSON.readTree(req.body());
        assertEquals("+224620000000", body.get("to").asText());
        assertEquals("Bonjour", body.get("message").asText());
        assertFalse(body.has("subject"));
    }

    @Test
    void createSendsChannelAndSubjectInBody() throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, MESSAGE_ENVELOPE);
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        client.messages().create(CreateMessageParams.builder()
                .to("client@exemple.com")
                .message("Bonjour {prenom}")
                .channel(Channel.EMAIL)
                .subject("Bienvenue")
                .statusCallback("https://exemple.com/webhooks/fameen")
                .build());

        FakeHttpTransport.RecordedRequest req = transport.requests().get(0);
        assertEquals("/api/v1/messages", req.uri().getPath());
        JsonNode body = JSON.readTree(req.body());
        assertEquals("email", body.get("channel").asText());
        assertEquals("Bienvenue", body.get("subject").asText());
        assertEquals("https://exemple.com/webhooks/fameen", body.get("statusCallback").asText());
        assertNull(req.headers().get("Idempotency-Key"));
    }

    // ------------------------------------------------------------------
    // Médias (WhatsApp & email)
    // ------------------------------------------------------------------

    @Test
    void whatsappAttachmentIsBase64EncodedInBody() throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, MESSAGE_ENVELOPE);
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        client.whatsapp().send(SendMessageParams.builder()
                .to("+224620000000")
                .message("Votre facture")
                .addAttachment(Attachment.ofBytes("%PDF-1.4 hello".getBytes(), "facture.pdf")
                        .withContentType("application/pdf").withType(MediaType.DOCUMENT))
                .build());

        JsonNode body = JSON.readTree(transport.requests().get(0).body());
        JsonNode att = body.get("attachments").get(0);
        assertEquals(Base64.getEncoder().encodeToString("%PDF-1.4 hello".getBytes()), att.get("content").asText());
        assertEquals("facture.pdf", att.get("filename").asText());
        assertEquals("application/pdf", att.get("contentType").asText());
        assertEquals("document", att.get("type").asText());
    }

    @Test
    void emailSupportsMultipleAttachments() throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, MESSAGE_ENVELOPE);
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        client.email().send(SendMessageParams.builder()
                .to("client@exemple.com")
                .subject("Documents")
                .message("Voir pièces jointes")
                .addAttachment(Attachment.ofBytes("un".getBytes(), "a.pdf"))
                .addAttachment(Attachment.ofBase64("ZGV1eA==", "b.txt"))
                .build());

        JsonNode atts = JSON.readTree(transport.requests().get(0).body()).get("attachments");
        assertEquals(2, atts.size());
        assertEquals(Base64.getEncoder().encodeToString("un".getBytes()), atts.get(0).get("content").asText());
        assertEquals("ZGV1eA==", atts.get(1).get("content").asText());
        assertEquals("b.txt", atts.get(1).get("filename").asText());
    }

    @Test
    void emptyMessageAllowedWhenMediaPresent() throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, MESSAGE_ENVELOPE);
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        client.whatsapp().send(SendMessageParams.builder()
                .to("+224620000000")
                .addAttachment(Attachment.ofBytes("img".getBytes(), "photo.png").withType(MediaType.IMAGE))
                .build());

        JsonNode body = JSON.readTree(transport.requests().get(0).body());
        assertEquals("", body.get("message").asText());
        assertTrue(body.has("attachments"));
    }

    @Test
    void mediaRejectedOnSmsChannel() {
        FakeHttpTransport transport = new FakeHttpTransport();
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        assertThrows(IllegalArgumentException.class, () -> client.sms().send(SendMessageParams.builder()
                .to("+224620000000")
                .message("x")
                .addAttachment(Attachment.ofBytes("img".getBytes(), "photo.png"))
                .build()));
        assertTrue(transport.requests().isEmpty());
    }

    @Test
    void attachmentFromFileReadsAndEncodes() throws IOException {
        Path file = Files.createTempFile("fameen", ".txt");
        Files.write(file, "contenu de test".getBytes());
        try {
            Attachment att = Attachment.fromFile(file);
            assertEquals(Base64.getEncoder().encodeToString("contenu de test".getBytes()), att.contentBase64());
            assertEquals(file.getFileName().toString(), att.filename());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // ------------------------------------------------------------------
    // Query string de la liste
    // ------------------------------------------------------------------

    @Test
    void listBuildsQueryStringAndUnwrapsPage() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, """
                {"success":true,"data":{"data":[{"sid":"msg_1","status":"sent","channel":"sms",
                 "to":"+224620000001","from":null,"body":"a","segments":1,"credits":1,"error":null,
                 "externalId":null,"statusCallback":null,"createdAt":"2026-07-12T10:00:00.000Z",
                 "sentAt":null,"deliveredAt":null}],
                 "page":2,"limit":50,"total":51,"totalPages":2},"message":"OK"}""");
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        MessageList page = client.messages().list(ListMessagesParams.builder()
                .channel(Channel.SMS)
                .status("sent")
                .to("+224")
                .page(2)
                .limit(50)
                .build());

        FakeHttpTransport.RecordedRequest req = transport.requests().get(0);
        assertEquals("GET", req.method());
        assertEquals("/api/v1/messages", req.uri().getPath());
        assertEquals("channel=sms&status=sent&to=%2B224&page=2&limit=50", req.uri().getRawQuery());

        assertEquals(1, page.data().size());
        assertEquals("msg_1", page.data().get(0).sid());
        assertEquals(2, page.page());
        assertEquals(50, page.limit());
        assertEquals(51, page.total());
        assertEquals(2, page.totalPages());
    }

    @Test
    @SuppressWarnings("deprecation")
    void historyIsDeprecatedButStillWorks() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, """
                {"success":true,"data":{"messages":[{"id":1,"canal":"sms"}],"total":1,"page":1,"pages":1},
                 "message":"OK"}""");
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        HistoryPage page = client.messages().history(HistoryParams.builder()
                .channel(Channel.SMS)
                .page(1)
                .build());

        FakeHttpTransport.RecordedRequest req = transport.requests().get(0);
        assertEquals("/api/v1/messages/history", req.uri().getPath());
        assertEquals("channel=sms&page=1", req.uri().getRawQuery());
        assertEquals(1, page.messages().size());
        assertEquals(1, page.pages());
    }

    // ------------------------------------------------------------------
    // Erreurs typées
    // ------------------------------------------------------------------

    @Test
    void insufficientCreditsBecomesTypedApiException402() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(402,
                "{\"success\":false,\"error\":{\"code\":\"insufficient_credits\","
                        + "\"message\":\"Crédits SMS insuffisants\"},\"statusCode\":402}");
        List<Long> sleeps = new ArrayList<>();
        FameenMessaging client = testClient(transport, sleeps).build();

        FameenApiException err = assertThrows(FameenApiException.class,
                () -> client.sms().send(simpleSms()));

        assertEquals(402, err.status());
        assertEquals("insufficient_credits", err.code());
        assertEquals("Crédits SMS insuffisants", err.getMessage());
        assertTrue(err.retryAfter().isEmpty());
        assertEquals(1, transport.requests().size()); // 4xx : jamais réessayé
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void unreadableErrorBodyFallsBackToStatusCode() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .enqueueJson(500, "Internal Server Error (pas du JSON)");
        List<Long> sleeps = new ArrayList<>();
        FameenMessaging client = testClient(transport, sleeps).build();

        FameenApiException err = assertThrows(FameenApiException.class,
                () -> client.sms().send(simpleSms()));

        assertEquals(500, err.status());
        assertEquals("internal_error", err.code());
        assertEquals("Erreur HTTP 500 sur POST /api/v1/sms/send", err.getMessage());
    }

    // ------------------------------------------------------------------
    // Réessais
    // ------------------------------------------------------------------

    @Test
    void retries429AndHonorsRetryAfterSeconds() {
        List<Long> sleeps = new ArrayList<>();
        FakeHttpTransport transport = new FakeHttpTransport()
                .enqueueJson(429,
                        "{\"success\":false,\"error\":{\"code\":\"rate_limited\","
                                + "\"message\":\"Trop de requêtes\"},\"statusCode\":429}",
                        Map.of("Retry-After", "7",
                                "X-RateLimit-Limit", "60",
                                "X-RateLimit-Remaining", "0",
                                "X-RateLimit-Reset", "1752300000"))
                .enqueueJson(200, MESSAGE_ENVELOPE);
        FameenMessaging client = testClient(transport, sleeps).build();

        // POST sans clé d'idempotence : le 429 est quand même réessayé (jamais traité).
        MessageResource msg = client.sms().send(simpleSms());

        assertEquals("msg_123", msg.sid());
        assertEquals(2, transport.requests().size());
        assertEquals(List.of(7000L), sleeps); // Retry-After: 7 s → 7000 ms, pas de backoff

        // lastRateLimit vient de la 1re réponse et n'est pas écrasé par la 2e (sans en-têtes).
        assertTrue(client.lastRateLimit().isPresent());
        RateLimitInfo rl = client.lastRateLimit().get();
        assertEquals(60, rl.limit());
        assertEquals(0, rl.remaining());
        assertEquals(1752300000L, rl.reset());
    }

    @Test
    void rateLimitInfoIsExposedOn429Exception() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .enqueueJson(429,
                        "{\"success\":false,\"error\":{\"code\":\"rate_limited\","
                                + "\"message\":\"Trop de requêtes\"},\"statusCode\":429}",
                        Map.of("Retry-After", "3",
                                "X-RateLimit-Limit", "60",
                                "X-RateLimit-Remaining", "0",
                                "X-RateLimit-Reset", "1752300000"));
        List<Long> sleeps = new ArrayList<>();
        FameenMessaging client = testClient(transport, sleeps).maxRetries(0).build();

        FameenApiException err = assertThrows(FameenApiException.class,
                () -> client.sms().send(simpleSms()));

        assertEquals(429, err.status());
        assertEquals("rate_limited", err.code());
        assertEquals(3, err.retryAfter().orElseThrow());
        assertEquals(60, err.rateLimit().orElseThrow().limit());
        assertTrue(sleeps.isEmpty()); // maxRetries(0) : aucun réessai
    }

    @Test
    void doesNotRetryPost5xxWithoutIdempotencyKey() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(500,
                "{\"success\":false,\"error\":{\"code\":\"internal_error\",\"message\":\"Oups\"},\"statusCode\":500}");
        List<Long> sleeps = new ArrayList<>();
        FameenMessaging client = testClient(transport, sleeps).build();

        FameenApiException err = assertThrows(FameenApiException.class,
                () -> client.sms().send(simpleSms()));

        assertEquals(500, err.status());
        assertEquals("internal_error", err.code());
        assertEquals(1, transport.requests().size()); // POST non idempotent : pas de réessai 5xx
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void retriesPost5xxWhenIdempotencyKeyIsProvided() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .enqueueJson(500,
                        "{\"success\":false,\"error\":{\"code\":\"internal_error\",\"message\":\"Oups\"},\"statusCode\":500}")
                .enqueueJson(200, MESSAGE_ENVELOPE);
        List<Long> sleeps = new ArrayList<>();
        FameenMessaging client = testClient(transport, sleeps).build();

        MessageResource msg = client.sms().send(SendMessageParams.builder()
                .to("+224620000000")
                .message("Bonjour")
                .idempotencyKey("idem-77")
                .build());

        assertEquals("msg_123", msg.sid());
        assertEquals(2, transport.requests().size());
        assertEquals(1, sleeps.size()); // backoff (pas de Retry-After)
        assertEquals("idem-77", transport.requests().get(1).headers().get("Idempotency-Key"));
    }

    @Test
    void retriesGet5xxWithoutIdempotencyKey() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .enqueueJson(503, "{\"success\":false,\"error\":{\"code\":\"internal_error\","
                        + "\"message\":\"Maintenance\"},\"statusCode\":503}")
                .enqueueJson(200, MESSAGE_ENVELOPE);
        List<Long> sleeps = new ArrayList<>();
        FameenMessaging client = testClient(transport, sleeps).build();

        MessageResource msg = client.messages().get("msg_123");

        assertEquals("msg_123", msg.sid());
        assertEquals(2, transport.requests().size()); // GET : réessai 5xx autorisé
        assertEquals("/api/v1/messages/msg_123", transport.requests().get(0).uri().getPath());
    }

    @Test
    void throwsConnectionExceptionAfterExhaustedNetworkRetries() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .enqueueError(new IOException("connexion refusée"))
                .enqueueError(new IOException("connexion refusée"))
                .enqueueError(new IOException("connexion refusée"));
        List<Long> sleeps = new ArrayList<>();
        FameenMessaging client = testClient(transport, sleeps).build();

        FameenConnectionException err = assertThrows(FameenConnectionException.class,
                () -> client.wallet().balance());

        assertEquals(3, transport.requests().size()); // 1 tentative + 2 réessais
        assertEquals(2, sleeps.size());
        assertTrue(err.getMessage().contains("fameenbusiness.com"));
        assertTrue(err.getCause() instanceof IOException);
    }

    // ------------------------------------------------------------------
    // Validation locale & construction
    // ------------------------------------------------------------------

    @Test
    void builderRequiresApiKey() {
        assertThrows(IllegalArgumentException.class, () -> FameenMessaging.builder().build());
        assertThrows(IllegalArgumentException.class, () -> FameenMessaging.builder().apiKey("   ").build());
    }

    @Test
    void trimsTrailingSlashesFromBaseUrl() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, MESSAGE_ENVELOPE);
        FameenMessaging client = testClient(transport, new ArrayList<>())
                .baseUrl("https://exemple.test/api/v1///")
                .build();

        client.sms().send(simpleSms());

        assertEquals("https://exemple.test/api/v1/sms/send", transport.requests().get(0).uri().toString());
    }

    @Test
    void validatesRecipientAndMessageLocallyWithoutNetworkCall() {
        FakeHttpTransport transport = new FakeHttpTransport();
        FameenMessaging client = testClient(transport, new ArrayList<>()).build();

        // Email fourni à un canal non-email → refus local.
        assertThrows(IllegalArgumentException.class, () -> client.sms().send(
                SendMessageParams.builder().to("client@exemple.com").message("Bonjour").build()));
        assertThrows(IllegalArgumentException.class, () -> client.whatsapp().send(
                SendMessageParams.builder().to("client@exemple.com").message("Bonjour").build()));
        assertThrows(IllegalArgumentException.class, () -> client.messages().create(
                CreateMessageParams.builder().to("client@exemple.com").message("x").channel(Channel.SMS).build()));

        // Champs requis vides.
        assertThrows(IllegalArgumentException.class, () -> client.sms().send(
                SendMessageParams.builder().to("  ").message("Bonjour").build()));
        assertThrows(IllegalArgumentException.class, () -> client.sms().send(
                SendMessageParams.builder().to("+224620000000").message("   ").build()));
        assertThrows(IllegalArgumentException.class, () -> client.messages().get("  "));

        assertEquals(0, transport.requests().size()); // aucun appel réseau
    }
}
