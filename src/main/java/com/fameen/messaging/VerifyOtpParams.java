package com.fameen.messaging;

/**
 * Paramètres du contrôle d'un code ({@code POST /otp/verify}).
 *
 * <pre>{@code
 * VerifyOtpParams.builder()
 *     .verificationId(verification.verificationId())
 *     .code("483920")
 *     .build();
 * }</pre>
 *
 * <p>Identifiez la vérification par {@code verificationId} (recommandé) ou, à
 * défaut, par {@code to} : la vérification en cours la plus récente pour ce
 * destinataire est alors utilisée.
 */
public final class VerifyOtpParams {

    private final String verificationId;
    private final String to;
    private final Channel channel;
    private final String code;

    private VerifyOtpParams(Builder builder) {
        this.verificationId = builder.verificationId;
        this.to = builder.to;
        this.channel = builder.channel;
        this.code = builder.code;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Identifiant renvoyé par {@code otp().send(...)}. */
    public String verificationId() {
        return verificationId;
    }

    /** Destinataire, si l'identifiant n'a pas été conservé. */
    public String to() {
        return to;
    }

    /** Restreint la recherche par {@code to} à ce canal. */
    public Channel channel() {
        return channel;
    }

    /** Le code saisi par l'utilisateur. */
    public String code() {
        return code;
    }

    /** Constructeur fluide de {@link VerifyOtpParams}. */
    public static final class Builder {

        private String verificationId;
        private String to;
        private Channel channel;
        private String code;

        private Builder() {
        }

        public Builder verificationId(String verificationId) {
            this.verificationId = verificationId;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder channel(Channel channel) {
            this.channel = channel;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public VerifyOtpParams build() {
            return new VerifyOtpParams(this);
        }
    }
}
