package com.pomgrade.omr.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pomgrade.omr.OmrApplication
import com.pomgrade.omr.R
import com.pomgrade.omr.data.local.ScanEntity
import com.pomgrade.omr.data.local.SyncStatus
import com.pomgrade.omr.databinding.ActivityHistoryBinding
import com.pomgrade.omr.databinding.ItemScanHistoryBinding
import com.pomgrade.omr.sync.SyncWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Scan History and Cloud Synchronization Audit Activity.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val app by lazy { application as OmrApplication }
    private lateinit var adapter: ScanHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ScanHistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.btnSyncAll.setOnClickListener {
            SyncWorker.scheduleSync(this)
            Toast.makeText(this, "กำลังเริ่มส่งข้อมูลขึ้นคลาวด์…", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            app.scanRepository.allScansFlow.collectLatest { list ->
                adapter.submitList(list)
                binding.tvEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private class ScanHistoryAdapter : ListAdapter<ScanEntity, ScanViewHolder>(ScanDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanViewHolder {
            val binding = ItemScanHistoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ScanViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ScanViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class ScanViewHolder(private val binding: ItemScanHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScanEntity) {
            binding.tvStudentId.text = "รหัสนักเรียน: ${item.studentId}"
            binding.tvScore.text = "${item.score} / ${item.totalQuestions} (${item.percentage}%)"
            binding.tvScannedAt.text = "เวลาสแกน: ${item.scannedAt}"
            binding.tvScanUuid.text = "UUID: ${item.scanId}"

            when (item.syncStatus) {
                SyncStatus.SYNCED -> {
                    binding.tvSyncStatus.text = "ซิงค์สำเร็จ"
                    binding.tvSyncStatus.setTextColor(itemView.context.getColor(R.color.status_success))
                    binding.tvSyncStatus.setBackgroundColor(itemView.context.getColor(R.color.status_success_bg))
                }
                SyncStatus.PENDING -> {
                    binding.tvSyncStatus.text = "รอซิงค์"
                    binding.tvSyncStatus.setTextColor(itemView.context.getColor(R.color.status_warning))
                    binding.tvSyncStatus.setBackgroundColor(itemView.context.getColor(R.color.status_warning_bg))
                }
                SyncStatus.FAILED -> {
                    binding.tvSyncStatus.text = "ซิงค์ไม่สำเร็จ"
                    binding.tvSyncStatus.setTextColor(itemView.context.getColor(R.color.status_error))
                    binding.tvSyncStatus.setBackgroundColor(itemView.context.getColor(R.color.status_error_bg))
                }
            }
        }
    }

    private class ScanDiffCallback : DiffUtil.ItemCallback<ScanEntity>() {
        override fun areItemsTheSame(oldItem: ScanEntity, newItem: ScanEntity): Boolean =
            oldItem.scanId == newItem.scanId

        override fun areContentsTheSame(oldItem: ScanEntity, newItem: ScanEntity): Boolean =
            oldItem == newItem
    }
}
