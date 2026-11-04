# MailChat – Google Play Store Content

Zentrale Vorlage für alle Texte, die in der Play Console eingetragen werden müssen.
Jeder Abschnitt ist kopierbereit. Zeichenlimits sind angegeben.

---

## 1. App-Name
```
MailChat
```

---

## 1.1 What's new / Release Notes (max. 500 Zeichen)

In der Play Console unter **Release → What's new** eintragen.

### Englisch (249 Zeichen)
```
Version 2.1:
• Unhide chats directly in the list (Hidden filter → Edit → Show)
• Export your private key with your own passphrase (or unprotected) for easy device migration
• AI summary in chat now also classifies spam and assigns tags
• Updated for Android 16 (API 36)
```

### Deutsch (280 Zeichen)
```
Version 2.1:
• Ausgeblendete Chats direkt in der Liste wieder einblenden (Filter „Ausgeblendet" → Bearbeiten → Einblenden)
• Privaten Schlüssel mit eigener Passphrase exportieren (oder ohne Schutz) — einfache Geräte-Migration
• KI-Zusammenfassung im Chat klassifiziert jetzt auch Spam und weist Tags zu
• Aktualisiert für Android 16 (API 36)
```

---

## 2. Kurzbeschreibung (max. 80 Zeichen)

### Englisch (64 Zeichen)
```
Email as private, encrypted chats with on-device AI. No tracking.
```

### Deutsch (67 Zeichen)
```
E-Mails als private, verschlüsselte Chats. KI direkt auf dem Gerät.
```

---

## 3. Vollbeschreibung (max. 4000 Zeichen)

### Englisch
```
MailChat turns your email into private, chat-style conversations — with encryption and AI that runs entirely on your device.

🔒 PRIVACY FIRST
• Your emails are stored locally and synced directly with your provider (IMAP/SMTP, TLS).
• No ads, no tracking, no analytics, no crash reporters. Your data leaves your device only to the recipient.
• OpenPGP encryption: send emails that only the recipient can read.

🤖 ON-DEVICE AI
• Summaries, spam/phishing detection, custom tags, reply suggestions and appointment detection — processed locally with the Gemma model (Google LiteRT). No email content is sent to the cloud.
• Optionally connect your own OpenAI-compatible endpoint for higher quality.

💬 EMAIL AS CHATS
• See your inbox as conversations, not a flat list.
• Group chats for multiple recipients.
• Quick replies, voice messages and file attachments.
• Smart auto-replies (e.g. "Where are you?" shares your location).

🛡️ SECURITY
• OpenPGP (RFC 4880) with key management and optional key-server lookup.
• Private key protected by a passphrase in the Android Keystore.
• Encrypted backups of your accounts and settings.

📱 FEATURES
• Spam & phishing classification with probability scores.
• Custom tags (AI-assigned or manual).
• Message summaries (5 lines).
• Reply suggestions (formal / informal).
• Real-name & appointment extraction.
• AI assistant (chat with your data).
• Image blocking for spam (anti tracking pixels).
• Compress attachments (optional password-protected ZIP).

MailChat is a private, non-commercial app. Free to use.
```

### Deutsch
```
MailChat verwandelt deine E-Mails in private, chatartige Gespräche — mit Verschlüsselung und KI, die komplett auf deinem Gerät läuft.

🔒 PRIVATHEIT VOR ALLEM
• Deine E-Mails werden lokal gespeichert und direkt mit deinem Provider synchronisiert (IMAP/SMTP, TLS).
• Keine Werbung, kein Tracking, keine Analyse, keine Crash-Reporter. Deine Daten verlassen dein Gerät nur zum Empfänger.
• OpenPGP-Verschlüsselung: Sende E-Mails, die nur der Empfänger lesen kann.

🤖 KI AUF DEM GERÄT
• Zusammenfassungen, Spam-/Phishing-Erkennung, eigene Tags, Antwortvorschläge und Termin-Erkennung — lokal mit dem Gemma-Modell (Google LiteRT). Kein E-Mail-Inhalt wird in die Cloud gesendet.
• Optional: Eigener OpenAI-kompatibler Endpunkt für höhere Qualität.

💬 E-MAIL ALS CHATS
• Sieh dein Postfach als Gespräche, nicht als flache Liste.
• Gruppenchats für mehrere Empfänger.
• Schnelle Antworten, Sprachnachrichten und Dateianhänge.
• Clevere Auto-Antworten (z. B. "Wo bist Du?" teilt deinen Standort).

🛡️ SICHERHEIT
• OpenPGP (RFC 4880) mit Schlüsselverwaltung und optionalem Key-Server-Lookup.
• Privater Schlüssel durch Passphrase im Android Keystore geschützt.
• Verschlüsselte Backups deiner Konten und Einstellungen.

📱 FUNKTIONEN
• Spam- & Phishing-Klassifizierung mit Wahrscheinlichkeitswerten.
• Eigene Tags (KI-zugeordnet oder manuell).
• Nachrichten-Zusammenfassungen (5 Zeilen).
• Antwortvorschläge (formell / locker).
• Name- & Termin-Erkennung.
• KI-Assistent (Chat mit deinen Daten).
• Bild-Blockierung für Spam (Anti-Tracking-Pixel).
• Anhänge komprimieren (optional passwortgeschütztes ZIP).

MailChat ist eine private, nicht-gewerbliche App. Kostenlos in der Nutzung.
```

---

## 4. Data Safety (Daten-Deklaration)

In der Console unter **App Information → Data safety**. Wichtige Punkte für MailChat:

### Daten, die die App SAMMELT (collected):
| Datenkategorie | Gesammelt? | Hinweis |
|---|---|---|
| **E-Mail-Adressen / Kontakte** | ✅ Ja | Nur lokal gelesen (Anzeige/Auswahl), nicht an Dritte |
| **Messages (Nachrichten)** | ✅ Ja | E-Mail-Inhalte, lokal gespeichert |
| **Location (Standort)** | ✅ Ja | Nur mit Berechtigung, nur an gewählten Kontakt |
| **Photos/Videos (Kamera)** | ✅ Ja | Lokal, nur als Anhang an Empfänger |
| **Audio (Mikrofon)** | ✅ Ja | Lokal, nur als Anhang an Empfänger |
| **Device IDs** | ❌ Nein | — |
| **Usage data / Diagnostics** | ❌ Nein | Kein Tracking, keine Crash-Reporter |

### Daten, die GETEILT werden (shared):
| Datenkategorie | Geteilt mit wem | Zweck |
|---|---|---|
| **Messages / E-Mail-Inhalt** | E-Mail-Provider (IMAP/SMTP) + Empfänger | Sync & Versand |
| **E-Mail-Adresse des Empfängers** | keys.openpgp.org (optional) | OpenPGP-Schlüssel-Lookup |
| **Standort-Koordinaten** | open-meteo.com (nur bei aktiver Wetter-Option) | Wetter-Vorhersage |
| **E-Mail-Inhalt (optional)** | Eigener OpenAI-Endpunkt (nur wenn konfiguriert) | KI-Verarbeitung |

### Wichtige Angaben im Formular:
- **Zweck der Datenverarbeitung:** App-Funktionalität (Sync, Versand, KI).
- **Art der Daten:** Wie oben.
- **Geteilt mit:** E-Mail-Provider, keys.openpm.org (optional), open-meteo.com (optional), eigener OpenAI-Endpunkt (optional).
- **Verschlüsselung:** In Transit (TLS) + lokal (OpenPGP / Android Keystore).
- **Löschung:** „You can delete data by uninstalling the app" (Daten sind nur lokal).
- **Kinder:** App ist **nicht** an Kinder gerichtet (E-Mail-App, Altersfreigabe 12+/16+ je nach KI/Standort).

> ⚠️ **Wichtig:** Die optionalen Cloud-Dienste (OpenAI-Endpunkt, open-meteo, keys.openpgp.org) müssen als „optional" deklariert werden, da sie standardmäßig deaktiviert sind.

---

## 5. Content Rating (IARC-Fragebogen)

Unter **App Information → Content rating**. Empfohlene Antworten:
- **Violence:** None
- **Alcohol/Drugs:** None
- **Sex/Nudity:** None
- **Language:** None (oder "Mild" falls KI-Antworten unvorhersehbar sein können)
- **Medical/Health:** None
- **Products/Brands:** None
- **Sharing of user data:** Yes (E-Mail-Versand)
- **In-App Purchases:** None (die PayPal-Spende ist extern, kein IAP)

→ Ergebnis: voraussichtlich **Everyone** oder **Teen** (je nach KI-Antworten).

---

## 6. App-spezifische Deklarationen

### Standort (Pflicht, Frist 27.01.2027)
Unter **App Information → Targeting → Location** oder im Data-Safety-Formular:
- Die App verwendet `ACCESS_FINE_LOCATION` für:
  - „Mein Standort" (Plus-Menü)
  - Auto-Antwort „Wo bist Du?"
- Deklarationstext (Beispiel):
  > „Die App kann mit deiner Erlaubnis deinen genauen Standort abrufen, um ihn auf Wunsch als Karten-Link an einen von dir gewählten Kontakt zu senden (Funktion 'Wo bist Du?'). Der Standort wird nicht an Dritte übermittelt."

### Kontakte
- Die App liest Kontakte **nur lokal** (zur Anzeige und Auswahl als Empfänger).
- Keine Übermittlung an Dritte.

### Kamera & Mikrofon
- Für Anhänge (Fotos) und Sprachnachrichten.
- Lokal gespeichert, nur als E-Mail-Anhang an den gewählten Empfänger gesendet.

---

## 7. Privacy-Policy-Hosting (Pflicht)

Die Play Console verlangt eine **öffentliche Privacy-Policy-URL**.

### Option A: GitHub Pages (kostenlos, passt zu deinem Repo)
1. In der Console: **Setup → Account details** → Privacy policy URL eintragen.
2. Hosting der `legal/`-Dateien:
   - **Einfachste Variante:** Die Dateien `legal/datenschutz.html` + `legal/privacy.html` auf GitHub Pages hosten.
   - Oder: Eine statische Seite mit beiden Versionen (DE + EN).

### ✅ Live-URLs (GitHub Pages, aktiviert):
- **Landing Page (beide Sprachen):** `https://okertaler.github.io/MailChat/`
- **Privacy Policy (EN):** `https://okertaler.github.io/MailChat/privacy.html`
- **Datenschutzerklärung (DE):** `https://okertaler.github.io/MailChat/datenschutz.html`
- **Disclaimer (EN):** `https://okertaler.github.io/MailChat/disclaimer.html`
- **Hinweise (DE):** `https://okertaler.github.io/MailChat/hinweise.html`
- **Impressum (DE):** `https://okertaler.github.io/MailChat/impressum.html`
- **Imprint (EN):** `https://okertaler.github.io/MailChat/imprint.html`
- **Open-Source-Lizenzen (DE):** `https://okertaler.github.io/MailChat/lizenzen.html`
- **Open Source Licenses (EN):** `https://okertaler.github.io/MailChat/licenses.html`

> **Für die Play Console:** Unter **Setup → Account details → Privacy policy** die **englische Version** eintragen (internationaler Standard):
> `https://okertaler.github.io/MailChat/privacy.html`
> …oder die Landing Page (bietet beide Sprachen): `https://okertaler.github.io/MailChat/`

---

## 8. Offene Play-Store-Fristen (aus dem Kontext)

| Frist | Was |
|---|---|
| **01.12.2026** | Japan-PSA (Provider-Specific Agreement) |
| **27.01.2027** | Standort-Deklaration (`ACCESS_FINE_LOCATION`) |
| **Feb 2027** | App-Qualitäts-Anforderungen |
| **Nov 2026** | Trader-Status (falls gewerblich — hier: privat) |

---

## 9. Technische Bereitschaft

- ✅ Release-AAB: `app/build/outputs/bundle/release/app-release.aab` (27,4 MB, signiert)
- ✅ Source: `https://github.com/Okertaler/MailChat`
- ✅ Legal-Dokumente: in der App + `legal/`-Ordner
- ✅ Version: 2.0 (versionCode 80)
