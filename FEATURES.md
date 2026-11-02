# MailChat - Feature List

Complete feature list sorted by function category.

## Email & Sync

- IMAP email sync (multiple accounts)
- Background sync (WorkManager) + foreground sync
- Configurable sync period (days)
- "E-Mails erneuern" - full re-sync of all emails
- Incremental sync with UID tracking
- Multi-folder sync (Inbox, Sent, custom folders)
- SMTP sending (SSL 465, STARTTLS 587)
- GMX/Google/other provider auto-detection
- Connection test (IMAP + SMTP)
- Sync state tracking per folder
- Period extension re-fetch
- Chat list always shows the latest subject under the contact name (one-time backfill for existing chats after update)

## Chat Interface

- WhatsApp-style chat bubbles
- Chat list with avatar, name, last subject + message preview
- Group chats (toggle, custom name, rename)
- Split a merged chat back apart (move one participant's incoming messages into a new chat)
- Two-pane view (tablet)
- Dark mode (Light/Dark/Auto)
- 12 account colors
- Contact photo and name display
- Sender name in group chats
- Unread count badge
- Favorite/Important star indicators
- No-reply icon on avatar
- Lock icon for encrypted messages
- Custom tag badge on avatar
- Location icon (bottom-left) and weather icon (bottom-right) on the contact photo when the auto-reply is enabled
- Swipe gestures (right: favorite, left: delete)
- Long-press for selection mode
- Selection mode with batch actions
- Swipe to select/deselect in selection mode
- "Full email" view (Ganze E-Mail) with raw headers and the spam classification menu in the top bar

## Filters & Search

- Filter dropdown menu (top to bottom)
- Favorites filter
- Unread filter
- Processed (Erledigt) filter
- Todo (Offen) filter
- Hidden filter
- Phishing filter
- Unwanted ads filter
- Newsletter filter
- Custom tags filter
- Filter badge with active count (includes custom tags)
- Full-text search (name, email, body)
- Search bar toggle

## Message Actions

- Reply with 5-line quote
- Forward with 5-line quote
- Copy 5-line excerpt
- Mark as read
- Mark as processed (Erledigt)
- Mark as todo (Offen)
- Delete messages
- Set spam category (phishing/unwanted/newsletter/ham)
- Spam category menu in the chat header (whole conversation) and in the full email view (single message)
- Set custom tag
- Hide chat
- No-reply toggle
- Merge chats
- Select all / Deselect all
- Reverse selection
- Select incoming only
- Select outgoing only

## AI Features

### Providers

- **On-Device (default):** Gemma models via Google LiteRT (GPU accelerated). The model runs fully locally — no email content ever leaves the phone and no internet is needed after the one-time model download (0.5–3 GB). Limitations: the small models (2–4B parameters) are slower, less accurate and consume more battery than cloud models.
- **OpenAI API (OpenAI-compatible):** You configure your own endpoint, API key and model. This usually gives better quality and faster answers, but your email content is sent to that external server.
- All AI features work with both providers — only the engine behind them changes.

### Features

- AI spam detection (heuristic + AI classification)
- Phishing probability (min 40% threshold)
- AI summary per message (5 lines, in your language)
- AI reply (formal/informal tone, own subject, uses existing reply text as starting point)
- AI real name extraction
- AI custom tag classification (with sender/subject context)
- AI appointment extraction
- Chat with your data (assistant)
- Shared AI pipeline (AiMessageProcessor): spam classification, custom tags and summaries run identically on all sync and processing paths (background sync, foreground sync, chat detail, batch processing)
- AI batch processing (all or selected chats)
- AI Summarize in edit menu (with battery warning)
- Configurable context size (default 100,000 tokens)
- AI enable/disable toggle
- Not yet available: streaming/real-time answers, translation, and rewriting/editing of existing text

## Security & Encryption

- OpenPGP (RFC 4880) via BouncyCastle — standard PGP, compatible with other OpenPGP clients
- Key generation (OpenPGP identity)
- Key exchange via QR code, QR scan, NFC (phone-to-phone) and email
- Automatic key lookup on keys.openpgp.org (freshness check before every send, stored key as fallback)
- Fingerprint-based verification (replaces PIN)
- Side-by-side fingerprint comparison
- Peers dialog with verify button
- PGP/MIME support (inline encrypted parts + .gpg attachments)
- PGP/MIME repair: re-fetches and re-parses messages that were cached before PGP/MIME support
- Legacy MAILCHAT_ENC format (fallback for older app versions)
- Attachment encryption (.enc files)
- Encrypted config backup (AES-256-GCM + PBKDF2)
- Config export/import with password
- Min password length 12
- Phishing reply ban
- Image blocking for spam/phishing

## Auto-Reply ("Wo bist Du?")

- Automatic reply to "wo bist du / wo bist ihr / wo seid ihr / where are you"
- Per contact, enabled in the recipient dialog (tap the chat header)
- Sends the current location as OpenStreetMap / Google Maps / Apple Maps links
- Optional weather for the next 4 hours (open-meteo.com, no API key)
- GPS fallback: a fresh GPS/network fix is requested when no last known position exists (up to ~20 s)
- Sent encrypted (OpenPGP, legacy fallback) like a normal email — shows up in your own chat with the lock icon
- Deduplication by Message-ID (no double replies on re-sync)
- Weather warning dialog before enabling
- "Where are you?" entry in the compose (+) menu

## Attachments & Media

- Attachment download and storage
- Inline CID image display (fixed MIME type handling)
- First CID image shown in chat bubble
- Attachment icons with file suffix
- File size display
- Open attachments (Office, PDF, images, etc.)
- Camera capture (compressed to 800px)
- Attach contact as .vcf
- My location (OSM/Google/Apple map links)
- Image compression on send
- Pinch-to-zoom in detail view
- External images blocked by default (load via icon)

## Settings & Configuration

- Account management (add/edit/delete)
- IMAP/SMTP server configuration
- Sync period configuration
- Language selection
- Dark mode (Light/Dark/Auto)
- AI settings (enable, provider, context size)
- AI provider configuration (OpenAI endpoint, API key, model)
- Custom tags management
- App log viewer
- Backup and restore
- Config export/import
- Version history
- Help screen
- Active chat selection

## UI & UX

- Material 3 design
- Compose UI
- Dropdown menus (filter, edit)
- Bottom sheets (message context menu)
- Dialogs (confirm, warning, input)
- Snackbar notifications
- Loading indicators (sync, AI processing)
- Empty state messages
- Help icons (?) on major sections
- Horizontal dividers in menus
- Filter badge on icon
- Spinning refresh button
- Haptic feedback

## Data & Storage

- Room database (messages, chats, accounts, attachments)
- Local file storage (attachments, images)
- SharedPreferences (settings, active chat)
- Encrypted backup files
- Config backup includes PGP keys and peers
