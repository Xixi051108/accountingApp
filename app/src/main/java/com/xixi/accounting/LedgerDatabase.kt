package com.xixi.accounting

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "imported_images")
data class ImportedImage(@PrimaryKey val hash: String)

@Entity(tableName = "merchant_categories")
data class MerchantCategory(@PrimaryKey val merchant: String, val category: String)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger ORDER BY date DESC, time DESC, id")
    fun observe(): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger ORDER BY date DESC, time DESC, id")
    suspend fun all(): List<LedgerEntry>
    @Upsert suspend fun save(entries: List<LedgerEntry>)
    @Query("DELETE FROM ledger WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT EXISTS(SELECT 1 FROM imported_images WHERE hash = :hash)") suspend fun imported(hash: String): Boolean
    @Insert suspend fun markImported(image: ImportedImage)
    @Query("SELECT * FROM merchant_categories") suspend fun categories(): List<MerchantCategory>
    @Upsert suspend fun remember(rule: MerchantCategory)
    @Query("DELETE FROM ledger") suspend fun clearEntries()
    @Query("DELETE FROM imported_images") suspend fun clearImports()
    @Query("DELETE FROM merchant_categories") suspend fun clearRules()
}

@Database(entities = [LedgerEntry::class, ImportedImage::class, MerchantCategory::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LedgerDao
    companion object {
        fun open(context: Context): LedgerDatabase = Room.databaseBuilder(
            context.applicationContext, LedgerDatabase::class.java, "screenshot-ledger.db").build()
    }
}

data class ImportReport(val posted: Int = 0, val pending: Int = 0, val skipped: Int = 0, val duplicateImage: Boolean = false)

class LedgerStore(private val db: LedgerDatabase) {
    val entries = db.ledger().observe()
    suspend fun snapshot() = db.ledger().all()

    suspend fun import(candidates: List<LedgerEntry>, hash: String): ImportReport = db.withTransaction {
        require(hash.isNotBlank())
        val dao = db.ledger()
        if (dao.imported(hash)) return@withTransaction ImportReport(duplicateImage = true)
        if (candidates.isEmpty()) return@withTransaction ImportReport()
        val rules = dao.categories().associate { it.merchant to it.category }
        val classified = candidates.map { entry ->
            if (entry.category == "其他" && entry.merchant in rules) entry.copy(category = rules.getValue(entry.merchant)) else entry
        }
        val result = LedgerRules.prepareImport(dao.all(), classified, hash)
        dao.save(result.entries)
        dao.markImported(ImportedImage(hash))
        ImportReport(result.entries.count { it.posted }, result.entries.count { !it.posted }, result.skipped)
    }

    suspend fun save(entry: LedgerEntry, rememberCategory: Boolean = false) = db.withTransaction {
        val confirmed = LedgerRules.confirm(entry)
        db.ledger().save(listOf(confirmed))
        if (rememberCategory && confirmed.merchant.isNotBlank())
            db.ledger().remember(MerchantCategory(confirmed.merchant, confirmed.category))
    }

    suspend fun delete(id: String) = db.ledger().delete(id)
    suspend fun clear() = db.withTransaction {
        db.ledger().clearEntries(); db.ledger().clearImports(); db.ledger().clearRules()
    }
    fun close() = db.close()
}
