package com.mailchat.data.dao

import androidx.room.*
import com.mailchat.data.model.EmailAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM email_accounts ORDER BY displayName ASC")
    fun getAllAccounts(): Flow<List<EmailAccount>>

    @Query("SELECT * FROM email_accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): EmailAccount?

    @Query("SELECT * FROM email_accounts ORDER BY id ASC LIMIT 1")
    suspend fun getFirstAccount(): EmailAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: EmailAccount): Long

    @Update
    suspend fun updateAccount(account: EmailAccount)

    @Delete
    suspend fun deleteAccount(account: EmailAccount)

    @Query("DELETE FROM email_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("UPDATE email_accounts SET passwordEncrypted = :password WHERE id = :id")
    suspend fun updatePassword(id: Long, password: String)
}