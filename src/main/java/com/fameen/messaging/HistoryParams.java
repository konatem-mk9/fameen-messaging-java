package com.fameen.messaging;

/**
 * Filtres de {@code GET /messages/history} (endpoint historique, déprécié).
 * Préférez {@link ListMessagesParams} avec {@link MessagesResource#list}.
 */
public final class HistoryParams {

    private final Channel channel;
    private final String status;
    private final Integer page;

    private HistoryParams(Builder builder) {
        this.channel = builder.channel;
        this.status = builder.status;
        this.page = builder.page;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Filtre canal, ou {@code null}. */
    public Channel channel() {
        return channel;
    }

    /** Filtre statut, ou {@code null}. */
    public String status() {
        return status;
    }

    /** Numéro de page, ou {@code null}. */
    public Integer page() {
        return page;
    }

    /** Constructeur fluide de {@link HistoryParams}. */
    public static final class Builder {

        private Channel channel;
        private String status;
        private Integer page;

        private Builder() {
        }

        public Builder channel(Channel channel) {
            this.channel = channel;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public HistoryParams build() {
            return new HistoryParams(this);
        }
    }
}
