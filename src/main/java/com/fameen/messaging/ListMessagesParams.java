package com.fameen.messaging;

/**
 * Filtres de {@code GET /messages} (tous optionnels).
 *
 * <pre>{@code
 * ListMessagesParams.builder().channel(Channel.SMS).status("delivered").page(1).build();
 * }</pre>
 */
public final class ListMessagesParams {

    private final Channel channel;
    private final String status;
    private final String to;
    private final Integer page;
    private final Integer limit;

    private ListMessagesParams(Builder builder) {
        this.channel = builder.channel;
        this.status = builder.status;
        this.to = builder.to;
        this.page = builder.page;
        this.limit = builder.limit;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Filtre canal, ou {@code null}. */
    public Channel channel() {
        return channel;
    }

    /** Filtre statut ({@code queued|sending|sent|delivered|failed}), ou {@code null}. */
    public String status() {
        return status;
    }

    /** Filtre « contient » sur le destinataire, ou {@code null}. */
    public String to() {
        return to;
    }

    /** Numéro de page (1-indexé), ou {@code null}. */
    public Integer page() {
        return page;
    }

    /** Taille de page 1–100 (30 par défaut côté serveur), ou {@code null}. */
    public Integer limit() {
        return limit;
    }

    /** Constructeur fluide de {@link ListMessagesParams}. */
    public static final class Builder {

        private Channel channel;
        private String status;
        private String to;
        private Integer page;
        private Integer limit;

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

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public ListMessagesParams build() {
            return new ListMessagesParams(this);
        }
    }
}
