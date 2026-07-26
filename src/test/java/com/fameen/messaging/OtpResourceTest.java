package com.fameen.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests des codes de vérification (OTP). Aucun réseau :
 * {@link FakeHttpTransport} répond en mémoire.
 */
class OtpResourceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String PENDING = """
            {"success":true,"data":{"verificationId":"ver_1","status":"pending","channel":"sms",
             "to":"+224620000000","attempts":0,"maxAttempts":5,"attemptsRemaining":5,
             "expiresAt":"2026-07-25T23:05:00.000Z","createdAt":"2026-07-25T23:00:00.000Z",
             "messageSid":"msg_1"},"message":"OK"}""";

    private static final String APPROVED = """
            {"success":true,"data":{"verificationId":"ver_1","status":"approved","channel":"sms",
             "to":"+224620000000","attempts":1,"maxAttempts":5,"attemptsRemaining":4,
             "expiresAt":"2026-07-25T23:05:00.000Z","createdAt":"2026-07-25T23:00:00.000Z",
             "messageSid":"msg_1"},"message":"OK"}""";

    private static final String REJECTED = """
            {"success":true,"data":{"verificationId":"ver_1","status":"rejected","reason":"invalid_code",
             "channel":"sms","to":"+224620000000","attempts":1,"maxAttempts":5,"attemptsRemaining":4,
             "expiresAt":"2026-07-25T23:05:00.000Z","createdAt":"2026-07-25T23:00:00.000Z",
             "messageSid":"msg_1"},"message":"OK"}""";

    private static FameenMessaging client(FakeHttpTransport transport) {
        return FameenMessaging.builder()
                .apiKey("fam_test_key")
                .transport(transport)
                .retryBase(Duration.ofMillis(1))
                .sleeper(new ArrayList<Long>()::add)
                .build();
    }

    private static JsonNode lastBody(FakeHttpTransport transport) throws Exception {
        List<FakeHttpTransport.RecordedRequest> reqs = transport.requests();
        return JSON.readTree(reqs.get(reqs.size() - 1).bodyText());
    }

    // ------------------------------------------------------------------
    // Envoi
    // ------------------------------------------------------------------

    @Test
    void sendPostsToOtpSendAndMapsResource() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, PENDING);

        VerificationResource v = client(transport).otp().send(
                SendOtpParams.builder().to("+224620000000").channel(Channel.SMS).build());

        assertTrue(transport.requests().get(0).uri().toString().endsWith("/otp/send"));
        assertEquals("+224620000000", lastBody(transport).get("to").asText());
        assertEquals("sms", lastBody(transport).get("channel").asText());
        assertEquals("ver_1", v.verificationId());
        assertEquals("pending", v.status());
        assertEquals(5, v.attemptsRemaining());
        assertEquals("msg_1", v.messageSid());
        assertFalse(v.isApproved());
    }

    @Test
    void sendForwardsPerRequestSettings() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, PENDING);

        client(transport).otp().send(SendOtpParams.builder()
                .to("client@exemple.com")
                .codeLength(8)
                .ttlSeconds(600)
                .maxAttempts(3)
                .subject("Votre code")
                .build());

        JsonNode body = lastBody(transport);
        assertEquals(8, body.get("codeLength").asInt());
        assertEquals(600, body.get("ttlSeconds").asInt());
        assertEquals(3, body.get("maxAttempts").asInt());
        assertEquals("Votre code", body.get("subject").asText());
    }

    @Test
    void sendSendsIdempotencyKeyHeader() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, PENDING);

        client(transport).otp().send(SendOtpParams.builder()
                .to("+224620000000").idempotencyKey("otp-001").build());

        assertEquals("otp-001", transport.requests().get(0).headers().get("Idempotency-Key"));
    }

    @Test
    void sendRejectsBlankRecipient() {
        FameenMessaging c = client(new FakeHttpTransport());
        assertThrows(IllegalArgumentException.class,
                () -> c.otp().send(SendOtpParams.builder().to("   ").build()));
    }

    @Test
    void sendRejectsTemplateWithoutCodePlaceholder() {
        FameenMessaging c = client(new FakeHttpTransport());
        assertThrows(IllegalArgumentException.class,
                () -> c.otp().send(SendOtpParams.builder().to("+224620000000").template("Bonjour !").build()));
    }

    @Test
    void sendRejectsEmailOnSmsChannel() {
        FameenMessaging c = client(new FakeHttpTransport());
        assertThrows(IllegalArgumentException.class,
                () -> c.otp().send(SendOtpParams.builder().to("a@b.c").channel(Channel.SMS).build()));
    }

    // ------------------------------------------------------------------
    // Vérification
    // ------------------------------------------------------------------

    @Test
    void verifyApprovesCorrectCode() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, APPROVED);

        VerificationResource r = client(transport).otp().verify(
                VerifyOtpParams.builder().verificationId("ver_1").code("483920").build());

        assertTrue(transport.requests().get(0).uri().toString().endsWith("/otp/verify"));
        assertEquals("ver_1", lastBody(transport).get("verificationId").asText());
        assertEquals("483920", lastBody(transport).get("code").asText());
        assertTrue(r.isApproved());
    }

    @Test
    void verifyDoesNotThrowOnWrongCode() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, REJECTED);

        VerificationResource r = client(transport).otp().verify(
                VerifyOtpParams.builder().verificationId("ver_1").code("000000").build());

        assertFalse(r.isApproved());
        assertEquals("rejected", r.status());
        assertEquals("invalid_code", r.reason());
        assertEquals(4, r.attemptsRemaining());
    }

    @Test
    void verifyAcceptsRecipientInsteadOfId() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, APPROVED);

        client(transport).otp().verify(
                VerifyOtpParams.builder().to("+224620000000").code("483920").build());

        assertEquals("+224620000000", lastBody(transport).get("to").asText());
    }

    @Test
    void verifyRequiresCode() {
        FameenMessaging c = client(new FakeHttpTransport());
        assertThrows(IllegalArgumentException.class,
                () -> c.otp().verify(VerifyOtpParams.builder().verificationId("ver_1").code("  ").build()));
    }

    @Test
    void verifyRequiresIdOrRecipient() {
        FameenMessaging c = client(new FakeHttpTransport());
        assertThrows(IllegalArgumentException.class,
                () -> c.otp().verify(VerifyOtpParams.builder().code("483920").build()));
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Test
    void getEncodesVerificationId() {
        FakeHttpTransport transport = new FakeHttpTransport().enqueueJson(200, PENDING);

        client(transport).otp().get("ver/1");

        assertTrue(transport.requests().get(0).uri().toString().endsWith("/otp/ver%2F1"));
    }

    @Test
    void getRequiresVerificationId() {
        FameenMessaging c = client(new FakeHttpTransport());
        assertThrows(IllegalArgumentException.class, () -> c.otp().get(""));
    }
}
