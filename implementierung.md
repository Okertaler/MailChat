# Implementierungsplan: Mailchat – WhatsApp-ähnlicher E-Mail-Client für Android

## 1. Projektübersicht

Ein Android-E-Mail-Client, der Chats wie WhatsApp-Sprechblasen darstellt. E-Mails werden nach Absender zusammengefasst, in Chats organisiert und mit Sprechblasen-UI angezeigt. Mehrere IMAP-Konten werden unterstützt.

## 2. Tech-Stack

- **Sprache:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architektur:** MVVM (ViewModel + StateFlow)
- **IMAP:** JavaMail (javax.mail) via IMAP-Store
- **Bildverarbeitung:** Coil (Image Loading) + Android Bitmap
- **Datenpersistenz:** Room (lokale Cache-Datenbank)
- **DI:** Hilt (Dependency Injection)
- **Build:** Gradle Kotlin DSL
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34

## 3. Projektstruktur

```
app/
├── src/main/
│   ├── java/com/mailchat/
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   ├── EmailAccount.kt
│   │   │   │   ├── Chat.kt
│   │   │   │   ├── Message.kt
│   │   │   │   └── Attachment.kt
│   │   │   ├── dao/
│   │   │   │   ├── AccountDao.kt
│   │   │   │   ├── ChatDao.kt
│   │   │   │   ├── MessageDao.kt
│   │   │   │   └── AttachmentDao.kt
│   │   │   ├── database/
│   │   │   │   ├── MailchatDatabase.kt
│   │   │   │   └── Converters.kt
│   │   │   └── repository/
│   │   │       ├── AccountRepository.kt
│   │   │       ├── ChatRepository.kt
│   │   │       └── MessageRepository.kt
│   │   ├── network/
│   │   │   └── ImapService.kt
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Theme.kt
│   │   │   │   └── Type.kt
│   │   │   ├── screen/
│   │   │   │   ├── ChatListScreen.kt
│   │   │   │   ├── ChatDetailScreen.kt
│   │   │   │   ├── AccountSetupScreen.kt
│   │   │   │   └── SettingsScreen.kt
│   │   │   ├── component/
│   │   │   │   ├── ChatBubble.kt
│   │   │   │   ├── ChatListItem.kt
│   │   │   │   ├── MessageBubble.kt
│   │   │   │   ├── AttachmentBar.kt
│   │   │   │   └── AccountAvatar.kt
│   │   │   └── viewmodel/
│   │   │       ├── ChatListViewModel.kt
│   │   │       ├── ChatDetailViewModel.kt
│   │   │       └── AccountSetupViewModel.kt
│   │   ├── service/
│   │   │   └── EmailSyncService.kt
│   │   ├── util/
│   │   │   ├── EmailGrouping.kt
│   │   │   ├── AttachmentUtils.kt
│   │   │   └── DateUtils.kt
│   │   └── MailchatApp.kt
│   ├── res/
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   └── colors.xml
│   │   └── drawable/
│   └── AndroidManifest.xml
├── build.gradle.kts
└── gradle.properties
```

## 4. Datenmodelle

### 4.1 EmailAccount
- `id: Long` (PK, auto)
- `displayName: String`
- `emailAddress: String`
- `imapHost: String`
- `imapPort: Int`
- `imapSsl: Boolean`
- `username: String`
- `password: String` (verschlüsselt gespeichert)
- `color: Int` (Avatar-Farbe)

### 4.2 Chat
- `id: Long` (PK, auto)
- `participantEmail: String` (zusammengefasste Adresse)
- `participantName: String` (Anzeigename)
- `accountId: Long` (FK → EmailAccount)
- `lastMessageTimestamp: Long`
- `unreadCount: Int`
- `backgroundImageUri: String?` (erster angehängter Bild-URI)

### 4.3 Message
- `id: Long` (PK, auto)
- `chatId: Long` (FK → Chat)
- `accountId: Long` (FK → EmailAccount)
- `from: String`
- `subject: String`
- `body: String` (gekürzt auf 5 Zeilen)
- `bodyFull: String` (vollständiger Text)
- `timestamp: Long`
- `isRead: Boolean`
- `isIncoming: Boolean`

### 4.4 Attachment
- `id: Long` (PK, auto)
- `messageId: Long` (FK → Message)
- `fileName: String`
- `mimeType: String`
- `uri: String` (lokaler URI)
- `isImage: Boolean`
- `sizeBytes: Long`
- `sortOrder: Int` (Reihenfolge im Chat)

## 5. IMAP-Service

### 5.1 ImapService
- Verbindet sich per JavaMail `IMAPStore` zu jedem konfigurierten Konto
- Unterstützt parallele Verbindungen für mehrere Konten
- Fetcht neue Nachrichten seit letztem Sync-Timestamp
- Parst Anhänge herunter und speichert sie lokal
- Extrahiert: Absender, Betreff, Body (gekürzt auf 5 Zeilen), Anhänge
- Lädt das erste Bild-Anhang als Hintergrundbild für den Chat

### 5.2 EmailSyncService (WorkManager)
- Periodischer Sync im Hintergrund (alle 15 Min konfigurierbar)
- Inkrementeller Sync basierend auf UID-Validität und Modseq
- Benachrichtigt UI über neue Nachrichten via `StateFlow`

## 6. UI-Komponenten

### 6.1 ChatBubble (Sprechblasen)
- Zeigt maximal **5 Zeilen** des Nachrichtentexts
- Farbe je nach eingehend/ausgehend (grau vs. blau)
- Rounded-Corners wie WhatsApp
- Absendername über der Sprechblase
- Zeitstempel unterhalb
- **Anhänge** in der untersten Zeile der Sprechblase als Icon-Bar
- **Erstes Bild-Anhang** als Hintergrund der Sprechblase (blurred/overlay)

### 6.2 ChatListItem
- Zeigt den Chat mit:
  - Avatar (Farbe pro Account)
  - Teilnehmername
  - Letzte Nachricht (5 Zeilen)
  - Unread-Badge
  - Zeitstempel
- Zusammenfassung gleicher E-Mail-Adressen (Gruppierung)

### 6.3 AttachmentBar
- Zeigt alle Anhänge einer Nachricht in der untersten Chatblasenzeile
- Bilder als kleine Thumbnails
- Dateien als Icon mit Dateityp-Indikator
- Klick öffnet Anhang-Vorschau

### 6.4 ChatDetailScreen
- Vollständige Nachrichtenansicht
- Alle Nachrichten als Sprechblasen gestapelt
- Anhänge inline oder als Bar
- Toolbar mit Teilnehmername und Account-Info

## 7. E-Mail-Zuordnung und Gruppierung

### 7.1 EmailGrouping
- Gleiche E-Mail-Adressen (nach Normalisierung: lowercase, trim) werden einem Chat zugeordnet
- Wenn ein Absender mehrere Chats hat (z.B. verschiedene Accounts), werden diese zusammengeführt
- Chat-Name wird aus dem Display-Name oder der E-Mail-Adresse abgeleitet

### 7.2 ChatAssignment
- Jede E-Mail-Adresse kann einem Chat zugeordnet werden
- Ein Chat gehört zu genau einem IMAP-Konto
- Bei mehreren Konten mit gleicher Adresse: separate Chats, aber in der Chat-Liste gruppiert

## 8. Anhang-Handling

### 8.1 Speicherung
- Anhänge werden im App-internen Speicher gespeichert (`context.filesDir`)
- Bilder werden als Thumbnail generiert (128x128) für die Chat-Liste
- Vollbild wird bei Klick geladen

### 8.2 Chat-Hintergrund
- Das **erste Bild-Anhang** einer Nachricht wird als Hintergrund der Sprechblase verwendet
- Bild wird mit Blur-Overlay und halbtransparenter Tinte überlagert, damit Text lesbar bleibt
- Nur das erste Bild pro Chat wird als Hintergrund verwendet

### 8.3 Anzeige in der untersten Zeile
- Alle Anhänge werden als kleine Icons in der untersten Zeile der Sprechblase angezeigt
- Bilder: Thumbnail-Icon
- Dokumente: Dateityp-Icon (PDF, DOC, etc.)
- Max 4 Anhänge sichtbar, Rest als "+N" Badge

## 9. Sicherheit

- Passwörter werden mit Android Keystore verschlüsselt gespeichert
- IMAP-Verbindungen nur über SSL/TLS
- Anhänge werden in App-internem Speicher isoliert gelagert
- Kein Zugriff auf externe Speicherberechtigungen (Scoped Storage)

## 10. Abhängigkeiten (build.gradle.kts)

```kotlin
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")

    // JavaMail (IMAP)
    implementation("com.sun.mail:javax.mail:1.6.2")

    // Coil (Image Loading)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
```

## 11. Implementierungsreihenfolge

1. Projekt-Setup (Gradle, Manifest, Theme)
2. Datenmodelle + Room-Datenbank
3. IMAP-Service (Grundlegende Verbindung + Fetch)
4. Repository-Schicht (Datenabstraktion)
5. Chat-Gruppierungslogik
6. UI: ChatListScreen + ChatListItem
7. UI: ChatBubble + MessageBubble
8. UI: AttachmentBar (unterste Zeile + Hintergrundbild)
9. UI: ChatDetailScreen
10. Account-Management (Setup + Multi-Account)
11. Sync-Service (WorkManager)
12. Anhang-Handling (Speicherung + Vorschau)
13. Testing + Polish