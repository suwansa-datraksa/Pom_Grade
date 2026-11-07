package com.pomgrade.omr.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Scans.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScans(scans: List<ScanEntity>): List<Long>

    @Update
    suspend fun updateScan(scan: ScanEntity)

    @Query("SELECT * FROM scans ORDER BY created_timestamp DESC")
    fun getAllScansFlow(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE sync_status = 'PENDING' ORDER BY created_timestamp ASC")
    suspend fun getPendingScans(): List<ScanEntity>

    @Query("UPDATE scans SET sync_status = 'SYNCED', synced_at = :syncedAt WHERE scan_id IN (:scanIds)")
    suspend fun markScansAsSynced(scanIds: List<String>, syncedAt: String)

    @Query("UPDATE scans SET sync_status = 'FAILED' WHERE scan_id IN (:scanIds)")
    suspend fun markScansAsFailed(scanIds: List<String>)

    @Query("SELECT COUNT(*) FROM scans")
    fun getTotalScansCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scans WHERE sync_status = 'PENDING'")
    fun getPendingScansCountFlow(): Flow<Int>

    @Query("SELECT * FROM scans WHERE scan_id = :scanId LIMIT 1")
    suspend fun getScanById(scanId: String): ScanEntity?
}
