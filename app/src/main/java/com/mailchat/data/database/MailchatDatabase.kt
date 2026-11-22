package com.mailchat.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mailchat.data.dao.AccountDao
import com.mailchat.data.dao.AttachmentDao
import com.mailchat.data.dao.ChatDao
import com.mailchat.data.dao.DraftDao
import com.mailchat.data.dao.MessageDao
import com.mailchat.data.dao.MessageEmbeddingDao
import com.mailchat.data.dao.PollDao
import com.mailchat.data.dao.SyncStateDao
import com.mailchat.data.model.Attachment
import com.mailchat.data.model.Chat
import com.mailchat.data.model.Draft
import com.mailchat.data.model.EmailAccount
import com.mailchat.data.model.Message
import com.mailchat.data.model.MessageEmbedding
import com.mailchat.data.model.Poll
import com.mailchat.data.model.PollVote
import com.mailchat.data.model.SyncState

@Database(
    entities = [EmailAccount::class, Chat::class, Message::class, Attachment::class, Draft::class, SyncState::class, MessageEmbedding::class, Poll::class, PollVote::class],
    version = 27,
    exportSchema = true
)
abstract class MailchatDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun draftDao(): DraftDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun messageEmbeddingDao(): MessageEmbeddingDao
    abstract fun pollDao(): PollDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN smtpHost TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN smtpPort INTEGER NOT NULL DEFAULT 587")
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN smtpSsl INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN messageId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN syncFolders TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageSubject TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN important INTEGER NOT NULL DEFAULT 0")
            }
        }

            val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isSpam INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN summaryLanguage TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isProcessed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN isTodo INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `drafts` (" +
                        "`chatId` INTEGER NOT NULL, " +
                        "`subject` TEXT NOT NULL DEFAULT '', " +
                        "`body` TEXT NOT NULL DEFAULT '', " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`chatId`), " +
                        "FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_messageId` ON `messages` (`messageId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (" +
                        "`accountId` INTEGER NOT NULL, " +
                        "`folder` TEXT NOT NULL, " +
                        "`uidValidity` INTEGER NOT NULL, " +
                        "`lastUid` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`accountId`, `folder`))"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `contentId` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `spamCategory` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `cc` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `bcc` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_embeddings` (" +
                        "`messageId` INTEGER NOT NULL, " +
                        "`embedding` BLOB NOT NULL, " +
                        "PRIMARY KEY(`messageId`), " +
                        "FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                        ")"
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `isStarred` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `noReply` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sync_state` ADD COLUMN `periodStart` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `customTag` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `isEncrypted` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `customTag` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `signatureStatus` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `isEncrypted` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // The default custom tag "Newsletter" was renamed to "Marketing" (to avoid
        // colliding with the spam category "newsletter"). Rename already-applied tag
        // values. customTag can hold up to two comma-separated names, so REPLACE on the
        // whole column is appropriate.
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE messages SET customTag = REPLACE(customTag, 'Newsletter', 'Marketing') WHERE customTag LIKE '%Newsletter%'")
                db.execSQL("UPDATE chats SET customTag = REPLACE(customTag, 'Newsletter', 'Marketing') WHERE customTag LIKE '%Newsletter%'")
            }
        }

        // Polls (Umfragen): new tables for poll definitions and votes, plus two columns on
        // messages to mark poll / poll-answer messages (answers are hidden in the chat).
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `polls` (" +
                        "`id` TEXT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, " +
                        "`chatId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL DEFAULT '', " +
                        "`question` TEXT NOT NULL DEFAULT '', " +
                        "`options` TEXT NOT NULL DEFAULT '[]', " +
                        "`multi` INTEGER NOT NULL DEFAULT 0, " +
                        "`creatorEmail` TEXT NOT NULL DEFAULT '', " +
                        "`pollMessageId` TEXT NOT NULL DEFAULT '', " +
                        "`status` TEXT NOT NULL DEFAULT 'open', " +
                        "`closedBy` TEXT NOT NULL DEFAULT '', " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`closedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`, `accountId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_polls_chatId` ON `polls` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_polls_accountId` ON `polls` (`accountId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `poll_votes` (" +
                        "`pollId` TEXT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, " +
                        "`voterEmail` TEXT NOT NULL, " +
                        "`optionIndices` TEXT NOT NULL DEFAULT '[]', " +
                        "`messageId` TEXT NOT NULL DEFAULT '', " +
                        "`answeredAt` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`pollId`, `accountId`, `voterEmail`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_poll_votes_pollId` ON `poll_votes` (`pollId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_poll_votes_accountId` ON `poll_votes` (`accountId`)")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `pollId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `isPollAnswer` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Per-recipient poll-IDs: the creator sends one poll email per recipient, each with its
        // own poll-ID, all linked by a shared groupId (the logical poll). recipientEmail records
        // who a per-recipient poll-ID was sent to (creator's records only).
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `groupId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `recipientEmail` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_polls_groupId` ON `polls` (`groupId`)")
            }
        }

        // Numeric polls (Zahlen): a poll can be a numeric poll (numberMode) where each respondent
        // enters a number per option and the result is the SUM of those numbers. PollVote.values
        // stores the entered numbers (JSON array aligned to the options).
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `numberMode` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `poll_votes` ADD COLUMN `values` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // Person-related polls (Personenbezogene Umfrage): the result is a table (persons ×
        // options, bottom row = column sums). The answer format is unchanged — only the result
        // view differs, so only the polls table gains a flag.
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `personMode` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Appointment (Termin) polls: the polls table gains the appointment details (fixed
        // Ja/Nein/Vielleicht options + date/time/location), and poll_votes gains the optional
        // answer comment (e.g. "Ja, aber erst ab 15 Uhr").
        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `appointmentMode` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `startDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `startTime` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `endDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `endTime` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `allDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `location` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `poll_votes` ADD COLUMN `comment` TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: MailchatDatabase? = null

        fun getInstance(context: Context): MailchatDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MailchatDatabase::class.java,
                    "mailchat_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)
                .build().also { INSTANCE = it }
            }
        }

        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}