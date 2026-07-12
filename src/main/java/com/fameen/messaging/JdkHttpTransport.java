package com.fameen.messaging;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implémentation par défaut de {@link HttpTransport} basée sur
 * {@link java.net.http.HttpClient} (JDK 11+), redirections suivies.
 *
 * <p>Pour un besoin avancé (proxy, TLS custom, executor dédié…), passez votre
 * propre {@link HttpClient} au constructeur, ou implémentez directement
 * {@link HttpTransport}.</p>
 */
public final class JdkHttpTransport implements HttpTransport {

    private final HttpClient client;

    /** Client HTTP par défaut (redirections {@code NORMAL}). */
    public JdkHttpTransport() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Réutilise un {@link HttpClient} déjà configuré. */
    public JdkHttpTransport(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Response execute(Request request) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).timeout(request.timeout());
        for (Map.Entry<String, String> entry : request.headers().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        if (request.body() != null) {
            builder.method(request.method(), HttpRequest.BodyPublishers.ofByteArray(request.body()));
        } else {
            builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        }

        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            Map<String, String> headers = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    headers.put(entry.getKey(), entry.getValue().get(0));
                }
            }
            return new Response(response.statusCode(), headers, response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Requête HTTP interrompue.", e);
        }
    }
}
