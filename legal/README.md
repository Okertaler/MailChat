# Rechtliche Dokumente für den Play Store

Drei fertige HTML-Seiten (einfaches, responsives Design, keine Abhängigkeiten):

| Datei | Inhalt |
|---|---|
| `impressum.html` | Impressum (private, nicht-gewerbliche Veröffentlichung) |
| `datenschutz.html` | Datenschutzerklärung (DSGVO, auf die App zugeschnitten) |
| `hinweise.html` | Hinweise & Disclaimer (KI-Funktionen, Haftung) |

## Veröffentlichen (eine öffentliche URL wird für die Play Console benötigt)

**Option A – GitHub Pages (empfohlen, kostenlos):**
1. Repo anlegen, z. B. `mailchat-legal`
2. Die drei HTML-Dateien hochladen
3. Settings → Pages → Branch `main` auswählen
4. URLs:
   - `https://<benutzer>.github.io/mailchat-legal/datenschutz.html`
   - `https://<benutzer>.github.io/mailchat-legal/impressum.html`
   - `https://<benutzer>.github.io/mailchat-legal/hinweise.html`

**Option B – beliebiger Webhosting-Anbieter:** Dateien einfach in das Web-Verzeichnis kopieren.

## In der Play Console eintragen
- **Datenschutz-URL:** `datenschutz.html` (Pflichtfeld im Store-Listing)
- **Website:** z. B. `impressum.html` oder die Hauptseite
- Impressum-Link zusätzlich in die Langbeschreibung aufnehmen (gute Praxis)

## Vor dem Hochladen prüfen
- [ ] E-Mail-Adresse in allen drei Dateien korrekt (`florescu@web.de`)?
- [ ] App-Name in den Titeln passt?
- [x] targetSdk auf 35 gesetzt (Play-Pflicht seit 31.08.2026)
- [x] Release-Keystore erstellt und AAB gebaut (`./gradlew :app:bundleRelease`)

## WICHTIG – Signing-Daten sicher aufbewahren
- Keystore: `release/mailchat-release.keystore`
- Passwort + Alias: `signing.properties` (Projekt-Root, chmod 600)
- **Ohne Keystore + Passwort sind keine Updates im Play Store möglich!**
  Beides an einem sicheren Ort sichern (Passwort-Manager, externer Speicher).
  `signing.properties` niemals in eine Versionskontrolle einchecken.
