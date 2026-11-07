package com.school.omrscanner

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.school.omrscanner.data.local.AppDatabase
import com.school.omrscanner.data.local.ScanEntity
import com.school.omrscanner.sync.SyncWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * HistoryActivity: Displays all scans from Room database with their sync status.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var btnSyncAll: MaterialButton
    private lateinit var adapter: ScanHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        toolbar = findViewById(R.id.toolbar)
        rvHistory = findViewById(R.id.rvHistory)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnSyncAll = findViewById(R.id.btnSyncAll)

        toolbar.setNavigationOnClickListener { finish() }

        adapter = ScanHistoryAdapter { entity ->
            val intent = Intent(this, ReviewActivity::class.java).apply {
                putExtra("SCAN_ID", entity.scanId)
            }
            startActivity(intent)
        }

        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        btnSyncAll.setOnClickListener {
            Toast.makeText(this, "กำลังซิงค์ข้อมูลกับคลาวด์…", Toast.LENGTH_SHORT).show()
            SyncWorker.enqueueImmediateSync(this)
        }

        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            db.scanDao().observeAllScans().collectLatest { scans ->
                if (scans.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    rvHistory.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    rvHistory.visibility = View.VISIBLE
                    adapter.submitList(scans)
                }
            }
        }
    }

    class ScanHistoryAdapter(
        private val onItemClick: (ScanEntity) -> Unit
    ) : ListAdapter<ScanEntity, ScanHistoryAdapter.ViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scan_history, parent, false)
            return ViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(
            itemView: View,
            private val onItemClick: (ScanEntity) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val tvStudentId: TextView = itemView.findViewById(R.id.tvStudentId)
            private val tvScore: TextView = itemView.findViewById(R.id.tvScore)
            private val tvScannedAt: TextView = itemView.findViewById(R.id.tvScannedAt)
            private val tvSyncStatus: TextView = itemView.findViewById(R.id.tvSyncStatus)
            private val tvScanUuid: TextView = itemView.findViewById(R.id.tvScanUuid)

            fun bind(scan: ScanEntity) {
                tvStudentId.text = "รหัส: ${scan.studentCode}"
                tvScore.text = "${scan.score.toInt()} / ${scan.maxScore.toInt()}"
                tvScannedAt.text = scan.scannedAt
                tvScanUuid.text = "ID: ${scan.scanId}"

                when (scan.syncStatus) {
                    0 -> {
                        tvSyncStatus.text = "รอส่งขึ้นคลาวด์"
                        tvSyncStatus.setTextColor(Color.parseColor("#F59E0B"))
                    }
                    1 -> {
                        tvSyncStatus.text = "ซิงค์สำเร็จแล้ว"
                        tvSyncStatus.setTextColor(Color.parseColor("#10B981"))
                    }
                    2 -> {
                        tvSyncStatus.text = "ปฏิเสธ/ผิดพลาด"
                        tvSyncStatus.setTextColor(Color.parseColor("#EF4444"))
                    }
                    3 -> {
                        tvSyncStatus.text = "ต้องตรวจสอบ (?)"
                        tvSyncStatus.setTextColor(Color.parseColor("#8B5CF6"))
                    }
                }

                itemView.setOnClickListener { onItemClick(scan) }
            }
        }

        companion object {
            private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ScanEntity>() {
                override fun areItemsTheSame(oldItem: ScanEntity, newItem: ScanEntity): Boolean =
                    oldItem.scanId == newItem.scanId

                override fun areContentsTheSame(oldItem: ScanEntity, newItem: ScanEntity): Boolean =
                    oldItem == newItem
            }
        }
    }
}
