package com.mailchat.di

import android.content.Context
import com.mailchat.data.dao.AccountDao
import com.mailchat.data.dao.AttachmentDao
import com.mailchat.data.dao.ChatDao
import com.mailchat.data.dao.DraftDao
import com.mailchat.data.dao.MessageDao
import com.mailchat.data.dao.SyncStateDao
import com.mailchat.data.database.MailchatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MailchatDatabase =
        MailchatDatabase.getInstance(context)

    @Provides
    fun provideAccountDao(db: MailchatDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideChatDao(db: MailchatDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: MailchatDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideAttachmentDao(db: MailchatDatabase): AttachmentDao = db.attachmentDao()

    @Provides
    fun provideDraftDao(db: MailchatDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideSyncStateDao(db: MailchatDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    fun provideMessageEmbeddingDao(db: MailchatDatabase): com.mailchat.data.dao.MessageEmbeddingDao = db.messageEmbeddingDao()
}