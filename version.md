<!--
  MailChat — Release Notes / Versionshinweise
  Version 2.2.1 (versionCode 84)
  Für die Google Play Store Veröffentlichung (Deutsch + Englisch, als HTML).

  Hinweis: Das Play-Console-Feld "Was ist neu?" akzeptiert nur PLAINTEXT (max. 500 Zeichen).
  Die reinen Textversionen für dieses Feld stehen am Ende dieser Datei (ohne HTML-Tags).
  Das HTML unten ist für die App-Website / Landingpage oder das "Vollständige Beschreibung"-Feld
  (begrenzte HTML-Unterstützung) gedacht.
-->

<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MailChat 2.2.1 — Versionshinweise</title>
<style>
  :root { --teal: #128C7E; --teal-dark: #0c6156; }
  body { font-family: -apple-system, "Segoe UI", Roboto, sans-serif; color: #1a1a1a; line-height: 1.6; max-width: 760px; margin: 0 auto; padding: 24px; }
  h1 { color: var(--teal-dark); border-bottom: 3px solid var(--teal); padding-bottom: 8px; }
  h2 { color: var(--teal); margin-top: 32px; }
  .version-badge { display: inline-block; background: var(--teal); color: #fff; border-radius: 999px; padding: 4px 14px; font-weight: 600; font-size: 14px; }
  ul { padding-left: 20px; }
  li { margin-bottom: 8px; }
  .tag { display: inline-block; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; border-radius: 4px; padding: 1px 7px; margin-right: 6px; }
  .tag-new { background: #e6f4f2; color: var(--teal-dark); }
  .tag-improved { background: #fff4e0; color: #8a5b00; }
  .tag-fixed { background: #fde8e8; color: #a12626; }
  hr { border: none; border-top: 1px solid #e0e0e0; margin: 32px 0; }
  .lang { font-size: 13px; color: #666; }
</style>
</head>
<body>

<h1>MailChat <span class="version-badge">2.2.1</span></h1>

<!-- ============================ DEUTSCH ============================ -->
<h2 id="de">🇩🇪 Deutsch</h2>
<p class="lang">Versionshinweise für die Google Play Store Veröffentlichung</p>
<ul>
  <li><span class="tag tag-new">Neu</span><strong>Digitale Signatur (OpenPGP)</strong> — deine ausgehenden E-Mails werden signiert; Empfänger sehen ein Badge in der Chatblase: verifiziert ✓, ungültig ✗ oder nicht signiert ⚠</li>
  <li><span class="tag tag-new">Neu</span><strong>Gruppenchat</strong> — der Betreff ist immer der Gruppenname (fest), ein Gruppen-Hinweis wird zum Text hinzugefügt, und optional Ende-zu-Ende-Verschlüsselung für alle Empfänger (beim Anlegen der Gruppe wählbar)</li>
  <li><span class="tag tag-new">Neu</span><strong>Startbildschirm-Widget</strong> mit deinen letzten oder ungelesenen E-Mails — die Anzahl passt sich der Widget-Höhe an, die Hintergrund-Durchsichtigkeit ist einstellbar, und ein Tippen auf eine E-Mail öffnet den Chat (Standard) oder das E-Mail-Detail (in den Einstellungen konfigurierbar)</li>
  <li><span class="tag tag-new">Neu</span>Der <strong>„+"-Button</strong> in der Übersicht erstellt oder öffnet jetzt einen Chat/Gruppenchat (Kontakte auswählen, Gruppe benennen) — er verschickt keine E-Mail mehr</li>
  <li><span class="tag tag-new">Neu</span><strong>Phishing-Blacklist</strong> — die Markierung eines Absenders als Phishing wird dauerhaft gespeichert (auch nach Chat-Löschung); neue E-Mails von diesem Absender werden sofort als Phishing markiert</li>
  <li><span class="tag tag-new">Neu</span>Die <strong>Detail-E-Mail</strong> zeigt die Spam-Kategorie und deinen eigenen Tag als Chips in der oberen Zeile; die Spam-Taste öffnet einen kombinierten Dialog für beide</li>
  <li><span class="tag tag-improved">Verbessert</span><strong>Config-Backup</strong> — der Export wird jetzt gestreamt (keine Crashes mehr bei großen Anhängen); Anhänge sind standardmäßig ausgeschlossen (sie liegen auf dem IMAP-Server und werden nach dem Restore neu geladen); optional für ein Vollbackup einschließen</li>
  <li><span class="tag tag-improved">Verbessert</span>Der <strong>Spam-Kategorien-Dialog</strong> in der Übersicht entspricht jetzt dem Chat (vertikale Liste, alle Optionen inkl. Phishing wählbar)</li>
  <li><span class="tag tag-improved">Verbessert</span><strong>IMAP-Ordner</strong> — „Posteingang" (INBOX) ist jetzt wählbar; die Standard-Ordner sind Posteingang + Postausgang</li>
  <li><span class="tag tag-fixed">Behoben</span>Die <strong>„Ungelesene"-Liste</strong> des Widgets aktualisiert sich jetzt, wenn eine E-Mail gelesen wird (macht Platz für andere ungelesene E-Mails)</li>
  <li><span class="tag tag-fixed">Behoben</span>Gelegentlicher Crash beim ersten Laden des lokalen KI-Modells während der App-Nutzung (Engine-Zugriff wird jetzt synchronisiert)</li>
  <li><span class="tag tag-fixed">Behoben</span>Crash im IMAP-Push-Service unter bestimmten Bedingungen</li>
</ul>

<hr>

<!-- ============================ ENGLISH ============================ -->
<h2 id="en">🇬🇧 English</h2>
<p class="lang">Release notes for the Google Play Store</p>
<ul>
  <li><span class="tag tag-new">New</span><strong>Digital signature (OpenPGP)</strong> — your outgoing emails are signed; recipients see a badge in the chat bubble: verified ✓, invalid ✗ or not signed ⚠</li>
  <li><span class="tag tag-new">New</span><strong>Group chat</strong> — the subject is always the group name (fixed), a group notice is added to the body, and optional end-to-end encryption for all recipients (choose when creating the group)</li>
  <li><span class="tag tag-new">New</span><strong>Home-screen widget</strong> showing your latest or unread emails — the count adapts to the widget height, the background transparency is adjustable, and tapping an email opens the chat (default) or the email detail (configurable in Settings)</li>
  <li><span class="tag tag-new">New</span>The <strong>"+" button</strong> on the overview now creates or opens a chat / group chat (select contacts, name the group) — it no longer sends an email</li>
  <li><span class="tag tag-new">New</span><strong>Phishing blacklist</strong> — marking a sender as phishing is remembered permanently (even after deleting the chat); new emails from that sender are flagged as phishing immediately</li>
  <li><span class="tag tag-new">New</span>The <strong>full email view</strong> shows the spam category and your custom tag as chips in the top row; the spam button opens a combined dialog for both</li>
  <li><span class="tag tag-improved">Improved</span><strong>Config backup</strong> — the export is now streamed (no more crashes with large attachments); attachments are excluded by default (they are on the IMAP server and re-downloaded after a restore); optionally include them for a full backup</li>
  <li><span class="tag tag-improved">Improved</span>The <strong>spam category dialog</strong> in the overview now matches the chat (vertical list, all options incl. phishing selectable)</li>
  <li><span class="tag tag-improved">Improved</span><strong>IMAP folders</strong> — "Inbox" (INBOX) is now selectable; the default folders are Inbox + Sent</li>
  <li><span class="tag tag-fixed">Fixed</span>The widget's <strong>"unread" list</strong> now updates when an email is read, making room for other unread emails</li>
  <li><span class="tag tag-fixed">Fixed</span>Occasional crash when the local AI model was loaded for the first time while using the app (engine access is now synchronized)</li>
  <li><span class="tag tag-fixed">Fixed</span>Crash in the IMAP push service under certain conditions</li>
</ul>

</body>
</html>

<!-- =====================================================================
     PLAINTEXT für das Play-Console-Feld "Was ist neu?" (max. 500 Zeichen)
     ===================================================================== -->

## DE (Plaintext, für "Was ist neu?")

Neu: Digitale Signatur (OpenPGP) mit Verifizierungs-Badge. Neu: Gruppenchat mit festem Betreff und optionaler Verschlüsselung. Neu: Startbildschirm-Widget (letzte/ungelesene Mails, Größe anpassbar). Neu: Phishing-Blacklist. Verbessert: Backup-Export (gestreamt, Anhänge optional). Behoben: Widget-Liste, KI- und Push-Crashes.

## EN (Plaintext, for "What's new?")

New: OpenPGP digital signature with verification badge. New: group chat with fixed subject and optional encryption. New: home-screen widget (latest/unread emails, resizable). New: phishing blacklist. Improved: backup export (streamed, optional attachments). Fixed: widget list, AI and push crashes.
