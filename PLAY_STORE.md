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

### Englisch (455 Zeichen)
```
New: "My Location" in the plus menu attaches an OpenStreetMap map image of your position. Sticky notes: yellow notes in the chat (never sent), tap to edit, own filter. Attachments show the file type (PDF, TXT, DOCX…) in the circle. Filters are now kept; new "Clear filters" entry. Maps are generated faster and zoomed 30% in around your position. Fixed: repeated re-fetching of the last email (battery); auto-reply with map also works with a locked phone.
```

### Deutsch (474 Zeichen)
```
Neu: „Mein Standort" im Plus-Menü hängt eine OpenStreetMap-Karte mit deinem Standort als Bild an. Notizzettel: gelbe Notizen im Chat (nie gesendet), per Tipp bearbeiten, eigener Filter. Anhänge zeigen im Kreis das Dateiformat (PDF, TXT, DOCX…). Filter bleiben erhalten; neuer Eintrag „Filter löschen". Karten werden schneller erstellt und 30 % näher um den Standort gezeigt. Behoben: doppelte Abrufe der letzten Mail (Akku); Auto-Antwort mit Karte auch bei gesperrtem Handy.
```

> Hinweis: In der Play Console unter **Release → What's new** wird der Text **ohne** die `<de-DE>`/`<en-GB>`-Tags eingetragen (die Tags sind nur für `version.md` im Repo). Pro Sprache ein eigener Eintrag, jeweils ≤ 500 Zeichen.

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
• Using of local AI
• OpenPGP encryption: send emails that only the recipient can read.

🤖 ON-DEVICE AI
• Summaries, spam/phishing detection, custom tags, reply suggestions and appointment detection — processed locally with the Gemma-4 model (Google LiteRT). No email content is sent to the cloud.
• Optionally connect your own OpenAI-compatible endpoint for higher quality.

💬 EMAIL AS CHATS
• See your inbox as conversations, not a flat list.
• Group chats for multiple recipients.
• Quick replies, voice messages and file attachments.
• Rich text formatting in replies: bold, italic, underline, alignment, font size, font family and lists — sent as clean HTML.
• Smart auto-replies (e.g. "Where are you?" shares your location, an optional OpenStreetMap map image and the current weather).
• "My Location" (plus menu): attach an OpenStreetMap map image of your position plus map links.
• Sticky notes: local yellow notes in the chat (never sent), tap a note to edit it, own filter.
• Attachment circles show the file type (PDF, TXT, DOCX…).
• Chat-list filters are kept between sessions; "Clear filters" resets everything.

🛡️ SECURITY
• OpenPGP (RFC 4880) with key management and optional key-server lookup.
• Digital signatures: sign your emails and verify the sender's signature (✓ / ✗ / ⚠).
• Private key protected by a passphrase in the Android Keystore.
• Encrypted backups of your accounts and settings.

📱 FEATURES
• Spam & phishing classification with probability scores.
• Phishing blacklist: flagged senders stay flagged, even after deleting the chat.
• Custom tags (AI-assigned or manual).
• Message summaries (5 lines).
• Reply suggestions (formal / informal).
• Real-name & appointment extraction.
• AI assistant (chat with your data).
• Polls in chat (single/multiple choice, number mode, person-related) with a bar chart or table and a result email; works with regular email clients.
• Appointment polls (date/time, all-day, location) with Yes/No/Maybe answers, live responses and a graphical result; add the appointment to your calendar.
• Image mosaic in the chat bubble (up to 4 images) with a full-screen zoom viewer (pinch-to-zoom).
• Home-screen widget showing your latest or unread emails (adjustable font size).
• Image blocking for spam (anti tracking pixels).
• Compress attachments (optional password-protected ZIP).

MailChat is a private, non-commercial app. Free to use.
Documents: https://okertaler.github.io/MailChat/docs/
Source code: https://github.com/Okertaler/MailChat/
```

### Deutsch
```
Fast jeder besitzt eine E-mail Adresse. Die Serverstrucktur ist vorhanden. IMAP Push ist schnell, aber nicht Echtzeit (kommt später mit der Integration von SMS). 
Ich benötige Feedback für Fehler und neue Funktionen für die Weiterentwicklung.
MailChat verwandelt deine E-Mails in private, chatartige Gespräche — mit Verschlüsselung, die jeder einfach einrichten kann und KI, die komplett auf deinem Gerät läuft.

🔒 PRIVATHEIT VOR ALLEM
• Deine E-Mails werden lokal gespeichert und direkt mit deinem Provider synchronisiert (IMAP/SMTP, TLS).
• Keine Werbung, kein Tracking, keine Analyse, keine Crash-Reporter. Deine Daten verlassen dein Gerät nur zum Empfänger.
• Nutzung von lokaler KI
• OpenPGP-Verschlüsselung: Sende E-Mails, die nur der Empfänger lesen kann.

🤖 KI AUF DEM GERÄT
• Zusammenfassungen, Spam-/Phishing-Erkennung, eigene Tags, Antwortvorschläge und Termin-Erkennung — lokal mit dem Gemma-4-Modell (Google LiteRT). Kein E-Mail-Inhalt wird in die Cloud gesendet.
• Optional: Eigener OpenAI-kompatibler Endpunkt für höhere Qualität.

💬 E-MAIL ALS CHATS
• Sieh dein Postfach als Gespräche, nicht als flache Liste.
• Gruppenchats für mehrere Empfänger.
• Schnelle Antworten, Sprachnachrichten und Dateianhänge.
• Text-Formatierung in Antworten: fett, kursiv, unterstreichen, Ausrichtung, Schriftgröße, Schriftart und Listen — als sauberes HTML gesendet.
• Clevere Auto-Antworten (z. B. "Wo bist Du?" teilt deinen Standort, ein optionales OpenStreetMap-Kartenbild und das aktuelle Wetter).
• "Mein Standort" (Plus-Menü): OpenStreetMap-Kartenbild deines Standorts plus Karten-Links anhängen.
• Notizzettel: lokale gelbe Notizen im Chat (nie gesendet), per Tipp bearbeiten, eigener Filter.
• Anhang-Kreise zeigen das Dateiformat (PDF, TXT, DOCX…).
• Chat-Filter bleiben zwischen den Sitzungen erhalten; "Filter löschen" setzt alles zurück.

🛡️ SICHERHEIT
• OpenPGP (RFC 4880) mit Schlüsselverwaltung und optionalem Key-Server-Lookup.
• Digitale Signatur: Signiere deine E-Mails und prüfe die Signatur des Absenders (✓ / ✗ / ⚠).
• Privater Schlüssel durch Passphrase im Android Keystore geschützt.
• Verschlüsselte Backups deiner Konten und Einstellungen.

📱 FUNKTIONEN
• Spam- & Phishing-Klassifizierung mit Wahrscheinlichkeitswerten.
• Phishing-Blacklist: Markierte Absender bleiben markiert, auch nach Chat-Löschung.
• Eigene Tags (KI-zugeordnet oder manuell).
• Nachrichten-Zusammenfassungen (5 Zeilen).
• Antwortvorschläge (formell / locker).
• Name- & Termin-Erkennung.
• KI-Assistent (Chat mit deinen Daten).
• Umfragen im Chat (Einzel-/Mehrfachauswahl, Zahlen-Modus, personenbezogen) mit Balkendiagramm oder Tabelle und Ergebnis per E-Mail; funktioniert auch mit normalen E-Mail-Programmen.
• Terminabfragen (Datum/Zeit, ganztägig, Ort) mit Ja/Nein/Vielleicht-Antworten, live Antworten und graphischem Ergebnis; Termin in den Kalender übernehmen.
• Bild-Mosaik in der Chatblase (bis zu 4 Bilder) mit Vollbild-Zoom-Viewer (Pinch-to-Zoom).
• Startbildschirm-Widget mit deinen letzten oder ungelesenen E-Mails (Schriftgröße einstellbar).
• Bild-Blockierung für Spam (Anti-Tracking-Pixel).
• Anhänge komprimieren (optional passwortgeschütztes ZIP).

MailChat ist eine private, nicht-gewerbliche App. Kostenlos in der Nutzung.
Dokumente: https://okertaler.github.io/MailChat/docs/
Quellcode: https://github.com/Okertaler/MailChat/
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
| **Standort-Kacheln (ungefähre Position)** | tile.openstreetmap.org (nur bei Kartenbild) | Kartenbild-Erstellung |
| **E-Mail-Inhalt (optional)** | Eigener OpenAI-Endpunkt (nur wenn konfiguriert) | KI-Verarbeitung |

### Wichtige Angaben im Formular:
- **Zweck der Datenverarbeitung:** App-Funktionalität (Sync, Versand, KI).
- **Art der Daten:** Wie oben.
- **Geteilt mit:** E-Mail-Provider, keys.openpgp.org (optional), open-meteo.com (optional), tile.openstreetmap.org (optional), eigener OpenAI-Endpunkt (optional).
- **Verschlüsselung:** In Transit (TLS) + lokal (OpenPGP / Android Keystore).
- **Löschung:** „You can delete data by uninstalling the app" (Daten sind nur lokal).
- **Kinder:** App ist **nicht** an Kinder gerichtet (E-Mail-App, Altersfreigabe 12+/16+ je nach KI/Standort).

> ⚠️ **Wichtig:** Die optionalen Cloud-Dienste (OpenAI-Endpunkt, open-meteo, tile.openstreetmap.org, keys.openpgp.org) müssen als „optional" deklariert werden, da sie standardmäßig deaktiviert sind.

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
- Die App verwendet `ACCESS_BACKGROUND_LOCATION` für:
  - Auto-Antwort „Wo bist Du?" auch bei gesperrtem Handy (Standort wird nur an den von dir gewählten Kontakt gesendet)
- Deklarationstext (Beispiel):
  > „Die App kann mit deiner Erlaubnis deinen genauen Standort abrufen, um ihn auf Wunsch als Karten-Link und optionales Kartenbild an einen von dir gewählten Kontakt zu senden (Funktion 'Wo bist Du?'). Der Standort wird nicht an Dritte übermittelt."

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

| Frist | Was | Status |
|---|---|---|
| **Nov 2026 (JETZT)** | Händlerstatus in der Play Console angeben | ⏳ offen |
| **01.12.2026** | Japan-PSA — **nur bei Zahlungen** (hier: keine IAP → nicht relevant) | ✅ N/A |
| **27.01.2027** | Standort-Deklaration (`ACCESS_FINE_LOCATION`) | ⏳ offen (Text in Abschnitt 10) |
| **Feb 2027** | App-Qualitäts-Anforderungen (RAM/Code-Optimierung) | ⏳ beobachten |

---

## 9. Technische Bereitschaft

- ✅ Release-AAB: `app/build/outputs/bundle/release/app-release.aab` (signiert)
- ✅ Source: `https://github.com/Okertaler/MailChat`
- ✅ Legal-Dokumente: in der App + `legal/`-Ordner
- ✅ Version: 2.8 (versionCode 91, targetSdk 36 / Android 16)

---

## 10. Richtlinien-Compliance (Mitteilung vom 29.10.2026)

Analyse der Play-Richtlinienänderungen für MailChat. Die meisten neuen Richtlinien
betreffen MailChat **nicht** (keine lokalen Netzwerk-Permissions, nur `READ_CONTACTS`
ohne `WRITE_CONTACTS`, keine AppFunctions, keine Abos, kein `MANAGE_EXTERNAL_STORAGE`,
keine Accessibility/Crypto/SMS/Anrufliste, keine In-App-Käufe, kein Spiel/XR).

### 10.1 Händlerstatus (Frist: November 2026 — JETZT)
In der Play Console unter **Setup → Account-Details** den Händlerstatus angeben.
MailChat wird in **privater, nicht-gewerblicher Tätigkeit** veröffentlicht →
**Privatperson / nicht-gewerblich** wählen (passt zum Impressum).

### 10.2 Standort-Erklärung (Frist: 27.01.2027)
MailChat nutzt `ACCESS_FINE_LOCATION` für die „Wo bist Du?"-Auto-Antwort.
Eine Play-Console-Erklärung ist erforderlich, warum ungefähre Genauigkeit nicht
ausreicht. Der neue Android-„Standort"-Button gilt **nur** für Apps mit
Ziel-Android 17 (API 37+) — MailChat targetet API 36, daher **nicht** Pflicht.

**Beispieltertext (kopierbereit):**
```
Die App teilt auf Wunsch des Nutzers dessen genauen Standort als Karten-Link an einen von ihm gewählten Kontakt (Funktion „Wo bist Du?"). Der genaue Standort (GPS) ist erforderlich, damit der Empfänger die Person auf einer Karte eindeutig finden kann — ein ungefährer Standort (Zellstufe/WLAN) wäre dafür zu ungenau. Der Standort wird nur an den gewählten Kontakt gesendet, nicht an Dritte.
```

### 10.3 App-Qualität (Frist: Februar 2027)
Grenzwerte für Arbeitsspeicher- und Code-Optimierung einhalten. Aktuelles AAB:
19,3 MB (R8-Minification + Resource-Shrinking aktiv) — im grünen Bereich.
Kein sofortiges Handeln, aber beobachten.

### 10.4 Japan (Frist: 01.12.2026) — nicht relevant
Betrifft nur den Verkauf von Produkten/Dienstleistungen (Zahlungen) nach/von Japan.
MailChat ist kostenlos ohne In-App-Käufe → **nicht anwendbar**.
