package com.school.omrscanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local OMR Scans.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScans(scans: List<ScanEntity>): List<Long>

    @Update
    suspend fun updateScan(scan: ScanEntity)

    @Query("SELECT * FROM scans WHERE scan_id = :scanId LIMIT 1")
    suspend fun getScanById(scanId: String): ScanEntity?

    @Query("SELECT * FROM scans WHERE sync_status = 0 ORDER BY scanned_at ASC LIMIT :limit")
    suspend fun getPendingScans(limit: Int = 50): List<ScanEntity>

    @Query("SELECT * FROM scans WHERE sync_status = 3 ORDER BY scanned_at DESC")
    suspend fun getReviewNeededScans(): List<ScanEntity>

    @Query("UPDATE scans SET sync_status = 1, received_at = :receivedAt WHERE scan_id IN (:scanIds)")
    suspend fun markScansAsSynced(scanIds: List<String>, receivedAt: String)

    @Query("UPDATE scans SET sync_status = 2 WHERE scan_id IN (:scanIds)")
    suspend fun markScansAsRejected(scanIds: List<String>)

    @Query("UPDATE scans SET sync_status = 0, answers = :updatedAnswers, student_code = :updatedStudentCode, flag = 'OK' WHERE scan_id = :scanId")
    suspend fun markScanAsReviewedAndReady(scanId: String, updatedStudentCode: String, updatedAnswers: String)

    @Query("UPDATE scans SET sync_status = :syncStatus, answers = :answers, student_code = :studentCode, flag = :status WHERE scan_id = :scanId")
    suspend fun updateReview(scanId: String, studentCode: String, answers: String, status: String, syncStatus: Int)

    @Query("SELECT * FROM scans ORDER BY scanned_at DESC")
    fun getAllScansFlow(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY scanned_at DESC")
    fun observeAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT COUNT(*) FROM scans WHERE sync_status = 0")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scans WHERE sync_status = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scans WHERE sync_status = 3")
    fun getReviewNeededCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scans")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scans")
    fun observeTotalCount(): Flow<Int>
}
