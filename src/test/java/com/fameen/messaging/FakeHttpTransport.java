package com.fameen.messaging;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport en mémoire pour les tests : rejoue une file de réponses (ou
 * d'erreurs réseau) et enregistre chaque requête reçue. Aucun accès réseau.
 */
final class FakeHttpTransport implements HttpTransport {

    /** Requête observée par le transport. */
    record RecordedRequest(String method, URI uri, Map<String, String> headers, byte[] body) {

        String bodyText() {
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        }
    }

    private final Deque<Object> queue = new ArrayDeque<>();
    private final List<RecordedRequest> requests = new ArrayList<>();

    /** Empile une réponse JSON sans en-tête particulier. */
    FakeHttpTransport enqueueJson(int status, String json) {
        return enqueueJson(status, json, Map.of());
    }

    /** Empile une réponse JSON avec en-têtes custom (Retry-After, X-RateLimit-*…). */
    FakeHttpTransport enqueueJson(int status, String json, Map<String, String> headers) {
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.putIfAbsent("Content-Type", "application/json");
        queue.add(new Response(status, all, json == null ? null : json.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    /** Empile un échec réseau (levé au lieu de répondre). */
    FakeHttpTransport enqueueError(IOException error) {
        queue.add(error);
        return this;
    }

    /** Requêtes reçues, dans l'ordre. */
    List<RecordedRequest> requests() {
        return requests;
    }

    @Override
    public Response execute(Request request) throws IOException {
        requests.add(new RecordedRequest(request.method(), request.uri(), request.headers(), request.body()));
        Object next = queue.poll();
        if (next == null) {
            throw new IllegalStateException(
                    "FakeHttpTransport : aucune réponse en file pour " + request.method() + " " + request.uri());
        }
        if (next instanceof IOException io) {
            throw io;
        }
        return (Response) next;
    }
}
