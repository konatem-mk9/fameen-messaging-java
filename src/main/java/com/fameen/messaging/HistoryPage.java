package com.fameen.messaging;

import java.util.List;
import java.util.Map;

/**
 * Page renvoyée par {@code GET /messages/history} (endpoint historique, déprécié).
 * Lignes brutes non garanties stables — préférez {@link MessagesResource#list}.
 *
 * @param messages lignes brutes
 * @param total    nombre total de lignes
 * @param page     page courante
 * @param pages    nombre total de pages
 */
public record HistoryPage(
        List<Map<String, Object>> messages,
        int total,
        int page,
        int pages) {
}
