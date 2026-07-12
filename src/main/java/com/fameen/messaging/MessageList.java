package com.fameen.messaging;

import java.util.List;

/**
 * Page renvoyée par {@code GET /messages}.
 *
 * @param data       messages de la page
 * @param page       numéro de page (1-indexé)
 * @param limit      taille de page effective (max 100)
 * @param total      nombre total de messages
 * @param totalPages nombre total de pages
 */
public record MessageList(
        List<MessageResource> data,
        int page,
        int limit,
        int total,
        int totalPages) {
}
