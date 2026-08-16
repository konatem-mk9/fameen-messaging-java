# Fameen Messaging — SDK Java officiel

SDK Java de l'API **Fameen Messaging** : envoi de SMS, WhatsApp et emails, codes
de vérification (OTP), suivi des statuts, solde de crédits et vérification des
webhooks.

- Java **17+**, une seule dépendance runtime : `jackson-databind`.
- Coordonnées Maven : `io.github.konatem-mk9:fameen-messaging:1.0.3` — licence MIT.
- Package : `com.fameen.messaging` (le groupId Central est le namespace GitHub vérifié ; le package Java reste celui de la marque).

## Installation

**Maven**

```xml
<dependency>
  <groupId>io.github.konatem-mk9</groupId>
  <artifactId>fameen-messaging</artifactId>
  <version>1.0.3</version>
</dependency>
```

**Gradle**

```groovy
implementation 'io.github.konatem-mk9:fameen-messaging:1.0.3'
```

## Démarrage rapide

```java
import com.fameen.messaging.*;

FameenMessaging fameen = FameenMessaging.builder()
    .apiKey(System.getenv("FAMEEN_API_KEY")) // clé "fam_…" — jamais en dur, jamais côté client
    .build();

// SMS
MessageResource sms = fameen.sms().send(SendMessageParams.builder()
    .to("+224620000000")
    .message("Bonjour {prenom}, votre commande est prête !")
    .build());
System.out.println(sms.sid() + " → " + sms.status()); // msg_… → queued

// WhatsApp
fameen.whatsapp().send(SendMessageParams.builder()
    .to("+224620000000")
    .message("Votre code : 123456")
    .build());

// Email
fameen.email().send(SendMessageParams.builder()
    .to("client@exemple.com")
    .subject("Bienvenue !")
    .message("Merci pour votre inscription.")
    .build());
```

### Endpoint unifié (façon Twilio)

Le canal peut être explicite ou déduit du destinataire (« @ » dans `to` → email,
sinon SMS — WhatsApp doit toujours être explicite) :

```java
MessageResource msg = fameen.messages().create(CreateMessageParams.builder()
    .to("client@exemple.com")           // canal email déduit
    .message("Bonjour !")
    .statusCallback("https://mon-app.com/webhooks/fameen")
    .build());
```

### Suivi et liste

```java
// Statut courant d'un message
MessageResource msg = fameen.messages().get("msg_abc123");

// Liste paginée avec filtres
MessageList page = fameen.messages().list(ListMessagesParams.builder()
    .channel(Channel.SMS)
    .status("delivered")
    .page(1)
    .limit(50)
    .build());
page.data().forEach(m -> System.out.println(m.sid() + " " + m.status()));
```

### Solde de crédits

```java
WalletBalance solde = fameen.wallet().balance();
System.out.println("SMS restants : " + solde.smsCredits());
if (solde.billing().sendingBlocked()) {
    System.out.println("Compte bloqué — contactez Fameen.");
}
```


## WhatsApp — à faire une fois avant le premier envoi

`whatsapp.send(...)` échoue tant que **votre** numéro WhatsApp Business n'est pas
connecté : il n'existe aucun numéro partagé de repli, Meta imposant que chaque
entreprise émette depuis le sien.

1. Tableau de bord → **Paramètres → WhatsApp → Connecter WhatsApp**. Une fenêtre
   Meta (*Embedded Signup*) vous fait choisir ou créer votre compte WhatsApp
   Business et votre numéro ; la connexion se finalise au retour.
2. Prérequis Meta : un compte Meta Business et un numéro **non déjà utilisé sur
   WhatsApp** (ni l'app classique, ni WhatsApp Business), joignable pour recevoir
   un code.

**La fenêtre de 24 h — la règle qui surprend le plus.** Meta n'autorise le
message libre que dans les 24 h suivant le dernier message **reçu** de ce
contact. En dehors (ou pour un premier contact), seul un **gabarit approuvé**
passe ; un message libre est refusé par Meta et la ressource finit en `failed`.
Faites approuver vos gabarits depuis Paramètres → WhatsApp → Gabarits avant de
planifier des envois sortants.

Détail complet : <https://fameenbusiness.com/communication/api>

## Codes de vérification (OTP)

Authentifiez un utilisateur par code à usage unique sur **SMS, WhatsApp ou email**.
Le code est généré, stocké haché et vérifié **côté serveur** : il ne transite jamais
par votre application et n'apparaît dans aucune réponse. Ni génération, ni stockage,
ni expiration à gérer.

```java
// 1. Envoyer le code (canal déduit du destinataire si absent)
VerificationResource v = fameen.otp().send(
        SendOtpParams.builder()
                .to("+224620000000")
                .channel(Channel.SMS)
                .build());
// v.verificationId(), v.status() == "pending", v.expiresAt(), v.attemptsRemaining()

// 2. Contrôler le code saisi par l'utilisateur
VerificationResource r = fameen.otp().verify(
        VerifyOtpParams.builder()
                .verificationId(v.verificationId())
                .code("483920")
                .build());

if (r.isApproved()) {
    // utilisateur authentifié
} else {
    // r.reason() : "invalid_code" | "expired" | "max_attempts"
    System.out.printf("Échec (%s), %d tentative(s) restante(s)%n", r.reason(), r.attemptsRemaining());
}
```

Un code erroné **ne lève pas d'exception** : la réponse porte `status = "rejected"`
et `reason`. Seules les erreurs de transport ou d'authentification lèvent.

Si vous ne conservez pas l'identifiant, vérifiez par destinataire — la vérification
en cours la plus récente est utilisée :

```java
fameen.otp().verify(VerifyOtpParams.builder().to("+224620000000").code("483920").build());
```

Options d'envoi : `codeLength` (4–8), `ttlSeconds` (60–3600), `maxAttempts` (1–10),
`template` (doit contenir `{{code}}` ; marqueurs `{{code}}`, `{{minutes}}`,
`{{seconds}}`, `{{company}}`), `subject` (email), `statusCallback` et
`idempotencyKey`. Sans ces paramètres, les réglages du compte s'appliquent.

À savoir :

- L'envoi consomme un crédit du canal utilisé. Toute clé créée depuis le tableau de bord couvre les trois canaux ; `channel_not_allowed` (403) ne concerne que d'anciennes clés restreintes.
- Un code validé est **à usage unique** ; le revérifier renvoie `rejected`.
- Demander un nouveau code pour le même destinataire **annule le précédent**.
- `fameen.otp().get(verificationId)` retourne l'état courant, jamais le code.

## Médias (pièces jointes)

WhatsApp et email acceptent des pièces jointes (PDF, images, vidéo, audio). Construisez une `Attachment` (depuis un fichier ou des octets) — le SDK l'encode en base64 ; l'API héberge le fichier et le distribue. **SMS non supporté.** Quand un média est fourni, le message peut être vide.

```java
import com.fameen.messaging.*;
import java.nio.file.Path;

// WhatsApp : un seul média par message, message = légende (facultative)
fameen.whatsapp().send(SendMessageParams.builder()
        .to("+224620000000")
        .message("Votre facture")
        .addAttachment(Attachment.fromFile(Path.of("facture.pdf")))
        .build());

// Email : plusieurs pièces jointes
fameen.email().send(SendMessageParams.builder()
        .to("client@exemple.com")
        .subject("Vos documents")
        .message("Bonjour, voir en pièces jointes.")
        .addAttachment(Attachment.fromFile(Path.of("facture.pdf")))
        .addAttachment(Attachment.ofBytes(pdfBytes, "cgv.pdf").withType(MediaType.DOCUMENT))
        .build());
```

Fabriques `Attachment` : `fromFile(Path)`, `ofBytes(byte[], filename)`, `ofBase64(String, filename)` ; affinez avec `withContentType(...)` / `withType(MediaType.IMAGE|VIDEO|AUDIO|DOCUMENT)`. Max 16 Mo par fichier.

## Idempotence

Fournissez une clé d'idempotence pour éviter tout doublon : pendant 24 h, un
réessai avec la même clé renvoie la réponse d'origine au lieu de recréer un
message. Elle rend aussi les réessais automatiques du SDK sûrs sur les POST.

```java
fameen.sms().send(SendMessageParams.builder()
    .to("+224620000000")
    .message("Votre commande n°881 est prête.")
    .idempotencyKey("commande-881-notif-prete")
    .build());
```

## Authenticité du paquet

Tous les artefacts publiés sur Maven Central sont **signés GPG** — le `.jar`, les
sources, le javadoc et le `.pom` ont chacun leur fichier `.asc`. La clé publique est
diffusée sur les serveurs de clés :

```
46F0 5D46 5687 9629 6220  FCB3 86D4 0718 F9BD D386
Moussa KONATE (Fameen SDK signing) <konatem.mk9@gmail.com>
```

Pour vérifier une archive téléchargée :

```bash
gpg --keyserver keyserver.ubuntu.com --recv-keys 86D40718F9BDD386
gpg --verify fameen-messaging-1.0.3.jar.asc fameen-messaging-1.0.3.jar
```

La sortie doit indiquer `Good signature` avec l'identité ci-dessus. Maven et Gradle
vérifient ces signatures automatiquement lorsque la vérification de somme est activée.

## Erreurs

Toutes les exceptions du SDK héritent de `FameenException` (non contrôlée).

| Exception | Quand |
|---|---|
| `FameenApiException` | Réponse HTTP non-2xx de l'API : `status()`, `code()`, `retryAfter()`, `rateLimit()` |
| `FameenConnectionException` | API injoignable après épuisement des réessais (DNS, timeout, coupure) |
| `WebhookVerificationException` | Signature ou corps de webhook invalide |
| `IllegalArgumentException` | Validation locale (clé API absente, `to`/`message` vides, email sur canal SMS…) |

Codes stables de `FameenApiException.code()` : `invalid_request`,
`unauthorized`, `insufficient_credits`, `subscription_expired`,
`channel_not_allowed`, `not_found`, `rate_limited`, `internal_error`.
`unknown_error` n'est jamais émis par l'API — c'est le repli du SDK quand la
réponse ne porte pas de code exploitable.

```java
try {
    fameen.sms().send(params);
} catch (FameenApiException e) {
    switch (e.code()) {
        case "insufficient_credits" -> System.err.println("Rechargez votre compte !");
        case "rate_limited" -> System.err.println("Réessayez dans "
                + e.retryAfter().orElse(60) + " s");
        default -> System.err.println("Erreur API " + e.status() + " : " + e.getMessage());
    }
} catch (FameenConnectionException e) {
    System.err.println("Réseau indisponible : " + e.getMessage());
}
```

## Réessais automatiques

Le client réessaie automatiquement (2 fois par défaut) avec backoff exponentiel
(`retryBase × 2^tentative + aléa`) :

| Situation | Réessai ? |
|---|---|
| Erreur réseau (DNS, timeout, coupure) | ✔ toutes méthodes |
| `429 Too Many Requests` | ✔ en respectant l'en-tête `Retry-After` s'il est présent |
| `5xx` sur un `GET` | ✔ |
| `5xx` sur un `POST` **avec** `idempotencyKey` | ✔ (sans risque de doublon) |
| `5xx` sur un `POST` **sans** `idempotencyKey` | ✘ jamais (le serveur a pu traiter l'envoi) |
| `4xx` (400, 401, 402, 403, 404…) | ✘ jamais |

### Limite de débit

60 requêtes/min **par compte** — toutes les clés d'un compte partagent ce
quota. Les compteurs `X-RateLimit-*` de la dernière réponse sont
exposés :

```java
fameen.lastRateLimit().ifPresent(rl ->
    System.out.println(rl.remaining() + "/" + rl.limit() + " requêtes restantes"));
```

## Webhooks

L'API notifie chaque changement de statut sur votre `statusCallback` avec un
en-tête `X-Fameen-Signature` = HMAC-SHA256 hexadécimal du **corps brut**,
calculé avec votre secret `whsec_…`.

> ⚠️ Vérifiez la signature sur les **octets bruts reçus**, avant tout parsing
> JSON : un re-sérialisage ne produit pas forcément les mêmes octets.

```java
import com.fameen.messaging.WebhookEvent;
import com.fameen.messaging.WebhookVerificationException;
import com.fameen.messaging.Webhooks;

byte[] corpsBrut = /* corps de la requête HTTP, tel quel */;
String signature = /* en-tête X-Fameen-Signature */;

try {
    WebhookEvent event = Webhooks.constructEvent(corpsBrut, signature, System.getenv("FAMEEN_WEBHOOK_SECRET"));
    // event.event() ∈ queued | sent | delivered | failed
    System.out.println(event.sid() + " → " + event.status());
} catch (WebhookVerificationException e) {
    // Répondez 401 et ne traitez rien.
}
```

`Webhooks.verifySignature(payload, signature, secret)` est aussi disponible si
vous voulez seulement le booléen (comparaison en temps constant via
`MessageDigest.isEqual`).

## Spring Boot

### Bean `FameenMessaging`

`application.properties` :

```properties
fameen.api-key=${FAMEEN_API_KEY}
fameen.webhook-secret=${FAMEEN_WEBHOOK_SECRET}
# Optionnel :
# fameen.base-url=https://fameenbusiness.com/api/v1
```

Configuration :

```java
import com.fameen.messaging.FameenMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FameenConfig {

    @Bean
    public FameenMessaging fameenMessaging(
            @Value("${fameen.api-key}") String apiKey,
            @Value("${fameen.base-url:https://fameenbusiness.com/api/v1}") String baseUrl) {
        return FameenMessaging.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }
}
```

Utilisation dans un service :

```java
@Service
public class NotificationService {

    private final FameenMessaging fameen;

    public NotificationService(FameenMessaging fameen) {
        this.fameen = fameen;
    }

    public void notifierCommande(String telephone, String prenom) {
        fameen.sms().send(SendMessageParams.builder()
                .to(telephone)
                .message("Bonjour " + prenom + ", votre commande est prête !")
                .idempotencyKey("commande-prete-" + telephone)
                .build());
    }
}
```

### Contrôleur webhook (corps brut)

Déclarez le paramètre en `byte[]` pour recevoir les octets bruts, condition
indispensable à la vérification de signature :

```java
import com.fameen.messaging.WebhookEvent;
import com.fameen.messaging.WebhookVerificationException;
import com.fameen.messaging.Webhooks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class FameenWebhookController {

    @Value("${fameen.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/webhooks/fameen")
    public ResponseEntity<Void> handle(
            @RequestBody byte[] payload, // corps brut, avant tout parsing
            @RequestHeader(value = "X-Fameen-Signature", required = false) String signature) {
        final WebhookEvent event;
        try {
            event = Webhooks.constructEvent(payload, signature, webhookSecret);
        } catch (WebhookVerificationException e) {
            return ResponseEntity.status(401).build();
        }
        // Traiter l'événement : event.sid(), event.status(), event.error()…
        return ResponseEntity.ok().build();
    }
}
```

## Configuration du client

| Option du builder | Défaut | Rôle |
|---|---|---|
| `apiKey(String)` | — (requis) | Clé API `fam_…` du compte |
| `baseUrl(String)` | `https://fameenbusiness.com/api/v1` | Les `/` finaux sont retirés |
| `timeout(Duration)` | 30 s | Timeout **par tentative** |
| `maxRetries(int)` | 2 | Réessais automatiques (0 pour désactiver) |
| `retryBase(Duration)` | 500 ms | Base du backoff exponentiel |
| `transport(HttpTransport)` | `JdkHttpTransport` | Transport HTTP injectable |

### Transport HTTP personnalisé

Le SDK parle HTTP à travers l'interface `HttpTransport`. L'implémentation par
défaut, `JdkHttpTransport`, repose sur `java.net.http.HttpClient` et accepte un
client préconfiguré (proxy, TLS, executor…) :

```java
HttpClient http = HttpClient.newBuilder()
    .proxy(ProxySelector.of(new InetSocketAddress("proxy.interne", 3128)))
    .build();

FameenMessaging fameen = FameenMessaging.builder()
    .apiKey(System.getenv("FAMEEN_API_KEY"))
    .transport(new JdkHttpTransport(http))
    .build();
```

En test, injectez un transport en mémoire (voir `src/test/java/...:FakeHttpTransport`)
pour simuler l'API sans réseau.

## Développement

Avec Maven :

```bash
mvn test
```

Sans Maven (Windows — télécharge les jars dans `lib/`, compile dans `out/` et
lance JUnit) :

```powershell
powershell -ExecutionPolicy Bypass -File .\build-and-test.ps1
```

## Licence

MIT — voir [LICENSE](LICENSE).
