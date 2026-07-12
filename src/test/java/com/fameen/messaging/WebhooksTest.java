package com.fameen.messaging;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests de vérification des webhooks (signature HMAC-SHA256 du corps brut). */
class WebhooksTest {

    private static final String SECRET = "whsec_test_secret";

    private static final String PAYLOAD = "{\"event\":\"delivered\",\"sid\":\"msg_123\","
            + "\"status\":\"delivered\",\"channel\":\"sms\",\"to\":\"+224620000000\","
            + "\"from\":\"FAMEEN\",\"error\":null,\"externalId\":\"op-1\","
            + "\"timestamp\":\"2026-07-12T10:05:00.000Z\"}";

    /** Signe comme le ferait le serveur Fameen : HMAC-SHA256 hex du corps brut. */
    private static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsValidSignatureAndParsesEvent() throws Exception {
        String signature = sign(PAYLOAD, SECRET);

        assertTrue(Webhooks.verifySignature(PAYLOAD, signature, SECRET));

        WebhookEvent event = Webhooks.constructEvent(PAYLOAD, signature, SECRET);
        assertEquals("delivered", event.event());
        assertEquals("msg_123", event.sid());
        assertEquals("delivered", event.status());
        assertEquals("sms", event.channel());
        assertEquals("+224620000000", event.to());
        assertEquals("FAMEEN", event.from());
        assertNull(event.error());
        assertEquals("op-1", event.externalId());
        assertEquals("2026-07-12T10:05:00.000Z", event.timestamp());
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String signature = sign(PAYLOAD, SECRET);
        String tampered = PAYLOAD.replace("+224620000000", "+224999999999");

        assertFalse(Webhooks.verifySignature(tampered, signature, SECRET));

        WebhookVerificationException err = assertThrows(WebhookVerificationException.class,
                () -> Webhooks.constructEvent(tampered, signature, SECRET));
        assertTrue(err.getMessage().contains("Signature"));
    }

    @Test
    void rejectsSignatureFromAnotherSecret() throws Exception {
        String foreignSignature = sign(PAYLOAD, "whsec_autre_compte");

        assertFalse(Webhooks.verifySignature(PAYLOAD, foreignSignature, SECRET));
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.constructEvent(PAYLOAD, foreignSignature, SECRET));
    }

    @Test
    void rejectsUnreadableJsonEvenWithValidSignature() throws Exception {
        String payload = "pas-du-json{";
        String signature = sign(payload, SECRET);

        assertTrue(Webhooks.verifySignature(payload, signature, SECRET)); // la signature colle…

        WebhookVerificationException err = assertThrows(WebhookVerificationException.class,
                () -> Webhooks.constructEvent(payload, signature, SECRET)); // …mais le corps est illisible
        assertTrue(err.getMessage().contains("JSON"));
    }

    @Test
    void missingSignatureIsRejectedNotAnError() {
        assertFalse(Webhooks.verifySignature(PAYLOAD, null, SECRET));
        assertFalse(Webhooks.verifySignature(PAYLOAD, "   ", SECRET));
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.constructEvent(PAYLOAD, null, SECRET));
    }

    @Test
    void emptySecretIsATypeError() {
        assertThrows(IllegalArgumentException.class, () -> Webhooks.verifySignature(PAYLOAD, "abcd", ""));
        assertThrows(IllegalArgumentException.class, () -> Webhooks.verifySignature(PAYLOAD, "abcd", null));
        assertThrows(IllegalArgumentException.class, () -> Webhooks.constructEvent(PAYLOAD, "abcd", "  "));
    }

    @Test
    void byteAndStringOverloadsAgree() throws Exception {
        String signature = sign(PAYLOAD, SECRET);
        byte[] raw = PAYLOAD.getBytes(StandardCharsets.UTF_8);

        assertTrue(Webhooks.verifySignature(raw, signature, SECRET));
        assertEquals(Webhooks.constructEvent(raw, signature, SECRET),
                Webhooks.constructEvent(PAYLOAD, signature, SECRET));
    }
}
