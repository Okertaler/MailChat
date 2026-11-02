package com.mailchat.data.repository

import android.content.Context
import com.mailchat.data.dao.AccountDao
import com.mailchat.data.model.EmailAccount
import com.mailchat.util.Secrets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val syncStateDao: com.mailchat.data.dao.SyncStateDao,
    @ApplicationContext private val appContext: Context
) {
    private val passwordsMigrated = AtomicBoolean(false)

    fun getAllAccounts(): Flow<List<EmailAccount>> = accountDao.getAllAccounts().map { accounts ->
        if (passwordsMigrated.compareAndSet(false, true)) {
            for (account in accounts) {
                migratePlaintextPassword(account)
            }
        }
        accounts.map { it.withSecretPassword() }
    }

    suspend fun getAccountById(id: Long): EmailAccount? =
        accountDao.getAccountById(id)?.let { migrateAndEnrich(it) }

    suspend fun insertAccount(account: EmailAccount): Long {
        val password = account.passwordEncrypted
        val id = accountDao.insertAccount(account.copy(passwordEncrypted = ""))
        if (password.isNotBlank()) {
            Secrets.putSecret(appContext, Secrets.accountPasswordKey(id), password)
        }
        return id
    }

    suspend fun updateAccount(account: EmailAccount) {
        if (account.passwordEncrypted.isNotBlank()) {
            Secrets.putSecret(appContext, Secrets.accountPasswordKey(account.id), account.passwordEncrypted)
        }
        accountDao.updateAccount(account.copy(passwordEncrypted = ""))
    }

    suspend fun deleteAccount(account: EmailAccount) {
        Secrets.removeSecret(appContext, Secrets.accountPasswordKey(account.id))
        syncStateDao.deleteByAccount(account.id)
        accountDao.deleteAccount(account)
    }

    suspend fun deleteAccountById(id: Long) {
        Secrets.removeSecret(appContext, Secrets.accountPasswordKey(id))
        syncStateDao.deleteByAccount(id)
        accountDao.deleteAccountById(id)
    }

    private suspend fun migratePlaintextPassword(account: EmailAccount) {
        if (account.passwordEncrypted.isNotBlank() &&
            Secrets.getSecret(appContext, Secrets.accountPasswordKey(account.id)) == null
        ) {
            Secrets.putSecret(appContext, Secrets.accountPasswordKey(account.id), account.passwordEncrypted)
            accountDao.updatePassword(account.id, "")
        }
    }

    private suspend fun migrateAndEnrich(account: EmailAccount): EmailAccount {
        migratePlaintextPassword(account)
        return account.withSecretPassword()
    }

    private fun EmailAccount.withSecretPassword(): EmailAccount {
        val secret = Secrets.getSecret(appContext, Secrets.accountPasswordKey(id))
        return if (!secret.isNullOrBlank()) copy(passwordEncrypted = secret) else this
    }
}
