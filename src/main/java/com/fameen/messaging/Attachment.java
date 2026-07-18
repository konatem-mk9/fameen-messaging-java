package com.fameen.messaging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pièce jointe d'un message (WhatsApp ou email).
 *
 * <p>Le fichier voyage <strong>encodé en base64</strong> ; l'API l'héberge puis
 * le distribue (URL signée pour WhatsApp, pièce jointe inline pour l'email).
 * Aucune URL publique n'est requise de votre côté.</p>
 *
 * <pre>{@code
 * Attachment facture = Attachment.fromFile(Path.of("facture.pdf"));
 * fameen.email().send(SendMessageParams.builder()
 *     .to("client@exemple.com").subject("Votre facture").message("Bonjour…")
 *     .addAttachment(facture)
 *     .build());
 * }</pre>
 */
public final class Attachment {

    private final String contentBase64;
    private final String filename;
    private final String contentType;
    private final MediaType type;

    private Attachment(String contentBase64, String filename, String contentType, MediaType type) {
        this.contentBase64 = Objects.requireNonNull(contentBase64, "content");
        this.filename = filename;
        this.contentType = contentType;
        this.type = type;
    }

    /** Pièce jointe à partir d'octets bruts (encodés en base64 par le SDK). */
    public static Attachment ofBytes(byte[] content, String filename) {
        Objects.requireNonNull(content, "content");
        return new Attachment(Base64.getEncoder().encodeToString(content), filename, null, null);
    }

    /** Pièce jointe à partir d'un contenu déjà encodé en base64. */
    public static Attachment ofBase64(String base64, String filename) {
        return new Attachment(base64, filename, null, null);
    }

    /** Pièce jointe lue depuis un fichier local (le nom et, si possible, le type MIME sont déduits). */
    public static Attachment fromFile(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            byte[] bytes = Files.readAllBytes(path);
            String contentType = Files.probeContentType(path);
            Path name = path.getFileName();
            return new Attachment(
                    Base64.getEncoder().encodeToString(bytes),
                    name != null ? name.toString() : null,
                    contentType,
                    null);
        } catch (IOException e) {
            throw new UncheckedIOException("Pièce jointe illisible : " + path, e);
        }
    }

    /** Nouvelle instance avec le type MIME précisé. */
    public Attachment withContentType(String contentType) {
        return new Attachment(contentBase64, filename, contentType, type);
    }

    /** Nouvelle instance avec la classe média précisée. */
    public Attachment withType(MediaType type) {
        return new Attachment(contentBase64, filename, contentType, type);
    }

    /** Contenu encodé en base64. */
    public String contentBase64() {
        return contentBase64;
    }

    public String filename() {
        return filename;
    }

    public String contentType() {
        return contentType;
    }

    public MediaType type() {
        return type;
    }

    /** Sérialise la pièce jointe en map JSON (clés camelCase de l'API, sans valeurs nulles). */
    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", contentBase64);
        if (filename != null && !filename.isBlank()) {
            map.put("filename", filename);
        }
        if (contentType != null && !contentType.isBlank()) {
            map.put("contentType", contentType);
        }
        if (type != null) {
            map.put("type", type.value());
        }
        return map;
    }
}
