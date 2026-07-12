package com.fameen.messaging;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Transport HTTP injectable du SDK.
 *
 * <p>L'implémentation par défaut est {@link JdkHttpTransport}
 * ({@code java.net.http.HttpClient}). Les tests injectent un faux transport
 * qui répond en mémoire, sans réseau. Le transport n'implémente NI les
 * réessais NI la gestion d'erreurs de l'API : c'est le rôle du client.</p>
 */
public interface HttpTransport {

    /**
     * Exécute une requête et renvoie la réponse brute.
     *
     * @throws IOException en cas d'échec réseau (DNS, connexion, timeout…) —
     *                     le client déclenche alors ses réessais.
     */
    Response execute(Request request) throws IOException;

    /**
     * Requête HTTP prête à partir (corps déjà sérialisé en JSON).
     *
     * @param method  {@code GET} ou {@code POST}
     * @param uri     URL absolue, query string comprise
     * @param headers en-têtes à envoyer (immuables)
     * @param body    corps encodé en UTF-8, ou {@code null} si aucun
     * @param timeout délai maximal pour CETTE tentative
     */
    record Request(String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout) {
    }

    /**
     * Réponse HTTP brute (statut + en-têtes + corps).
     *
     * @param statusCode statut HTTP
     * @param headers    en-têtes de réponse (première valeur de chaque en-tête)
     * @param body       corps brut, jamais {@code null} (tableau vide si absent)
     */
    record Response(int statusCode, Map<String, String> headers, byte[] body) {

        public Response {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            body = body == null ? new byte[0] : body;
        }

        /** Valeur de l'en-tête {@code name}, insensible à la casse. */
        public Optional<String> header(String name) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return Optional.ofNullable(entry.getValue());
                }
            }
            return Optional.empty();
        }
    }
}
