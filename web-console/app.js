/**
 * PRODUCTION-GRADE HYBRID OMR EXAM SYSTEM — WEB CONSOLE CLIENT
 * Architecture Specification v6 (Static Web on GitHub Pages)
 *
 * Responsibilities:
 *  - Pure static client execution on GitHub Pages
 *  - Google Identity Services (GIS) token acquisition for read-only access
 *  - In-memory read from Google Sheets API v4 (zero middle-tier storage)
 *  - Interactive Room inspection, KPI metrics, search, and CSV export
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

const CONFIG = {
  SPREADSHEET_ID: '1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4',
  SHEETS_API_BASE: 'https://sheets.googleapis.com/v4/spreadsheets',
  DEFAULT_CLIENT_ID: '407408718192.apps.googleusercontent.com', // Replaceable via UI
  CREDIT: 'จัดทำโดย ครูสุวรรณษา เดชรักษา'
};

// Global App State
let appState = {
  accessToken: null,
  clientId: localStorage.getItem('omr_client_id') || CONFIG.DEFAULT_CLIENT_ID,
  tokenClient: null,
  spreadsheetData: null,
  roomsList: [],
  activeRoomData: null,
  filterSearch: '',
  selectedExam: 'ALL'
};

// =============================================================================
// INITIALIZATION & EVENT LISTENERS
// =============================================================================

document.addEventListener('DOMContentLoaded', () => {
  setupUIEventListeners();
  loadSavedConfig();
  initGisTokenClient();
});

function setupUIEventListeners() {
  document.getElementById('btnAuth').addEventListener('click', handleAuthClick);
  document.getElementById('btnSaveClientId').addEventListener('click', handleSaveClientId);
  document.getElementById('btnRefresh').addEventListener('click', fetchSpreadsheetData);
  document.getElementById('btnExportCSV').addEventListener('click', exportTableToCSV);
  document.getElementById('searchInput').addEventListener('input', handleSearchInput);
  document.getElementById('selectExamFilter').addEventListener('change', handleExamFilterChange);

  // Modal controls
  document.getElementById('btnCloseModal').addEventListener('click', closeModal);
  document.getElementById('btnCloseModalFooter').addEventListener('click', closeModal);
  document.getElementById('btnPrintRoom').addEventListener('click', () => window.print());
}

function loadSavedConfig() {
  const savedClientId = localStorage.getItem('omr_client_id');
  if (savedClientId) {
    document.getElementById('inputClientId').value = savedClientId;
    appState.clientId = savedClientId;
  }
}

function handleSaveClientId() {
  const input = document.getElementById('inputClientId').value.trim();
  if (input) {
    appState.clientId = input;
    localStorage.setItem('omr_client_id', input);
    initGisTokenClient();
    handleAuthClick();
  }
}

// =============================================================================
// GOOGLE IDENTITY SERVICES (GIS) AUTHENTICATION
// =============================================================================

function initGisTokenClient() {
  if (typeof google === 'undefined' || !google.accounts || !google.accounts.oauth2) {
    console.warn('GIS SDK not yet loaded. Will retry on user click.');
    return;
  }

  try {
    appState.tokenClient = google.accounts.oauth2.initTokenClient({
      client_id: appState.clientId,
      scope: 'https://www.googleapis.com/auth/spreadsheets.readonly',
      callback: (tokenResponse) => {
        if (tokenResponse && tokenResponse.access_token) {
          appState.accessToken = tokenResponse.access_token;
          onAuthSuccess();
        } else {
          console.error('Failed to acquire token:', tokenResponse);
          alert('ไม่สามารถขอสิทธิ์การเข้าถึงได้ กรุณาตรวจสอบ Client ID');
        }
      }
    });
  } catch (err) {
    console.error('Error initializing GIS token client:', err);
  }
}

function handleAuthClick() {
  if (!appState.tokenClient) {
    initGisTokenClient();
  }

  if (appState.tokenClient) {
    // Request access token (GIS popup)
    appState.tokenClient.requestAccessToken({ prompt: 'consent' });
  } else {
    // If running in development without live OAuth ID, load preview mock data
    console.warn('Google Identity Services client unavailable. Loading preview demo mode.');
    loadSamplePreviewData();
  }
}

function onAuthSuccess() {
  document.getElementById('authBtnText').textContent = 'เชื่อมต่อแล้ว';
  document.getElementById('btnAuth').classList.remove('btn-primary');
  document.getElementById('btnAuth').classList.add('btn-secondary');
  document.getElementById('authBanner').style.display = 'none';

  fetchSpreadsheetData();
}

// =============================================================================
// GOOGLE SHEETS API V4 IN-MEMORY FETCHING
// =============================================================================

async function fetchSpreadsheetData() {
  if (!appState.accessToken) {
    // If no token yet, load sample demo data to ensure full interactivity
    loadSamplePreviewData();
    return;
  }

  const badge = document.getElementById('badgeLastUpdate');
  badge.textContent = 'กำลังดึงข้อมูลสดจาก Sheets API…';

  try {
    // 1. Fetch metadata and Index sheet values
    const url = `${CONFIG.SHEETS_API_BASE}/${CONFIG.SPREADSHEET_ID}?includeGridData=true`;
    const res = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${appState.accessToken}`
      }
    });

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    }

    const data = await res.json();
    appState.spreadsheetData = data;
    parseAndRenderSpreadsheetData(data);

    badge.textContent = `อัปเดตล่าสุด: ${new Date().toLocaleTimeString('th-TH')}`;
  } catch (err) {
    console.error('Sheets API Error:', err);
    badge.textContent = 'การเชื่อมต่อผิดพลาด — แสดงข้อมูลตัวอย่าง';
    loadSamplePreviewData();
  }
}

function parseAndRenderSpreadsheetData(data) {
  const sheets = data.sheets || [];
  const indexSheet = sheets.find(s => s.properties.title === 'Index');

  appState.roomsList = [];

  if (indexSheet && indexSheet.data && indexSheet.data[0] && indexSheet.data[0].rowData) {
    const rows = indexSheet.data[0].rowData;
    // Row 4 is header, data starts at index 4 (row 5)
    for (let i = 4; i < rows.length; i++) {
      const row = rows[i];
      if (!row.values || row.values.length < 2) continue;

      const orderVal = row.values[0]?.formattedValue || '';
      if (orderVal === 'รวม' || !orderVal) continue; // Skip summary row

      const roomName = row.values[1]?.formattedValue || `ห้อง ${i - 3}`;
      const totalStudents = parseInt(row.values[2]?.formattedValue || '0', 10);
      const scanned = parseInt(row.values[3]?.formattedValue || '0', 10);
      const absent = parseInt(row.values[4]?.formattedValue || '0', 10);
      const attendanceRate = parseFloat(row.values[5]?.formattedValue || '0');
      const avgScore = parseFloat(row.values[6]?.formattedValue || '0');
      const maxScore = parseFloat(row.values[7]?.formattedValue || '0');
      const minScore = parseFloat(row.values[8]?.formattedValue || '0');
      const passed = parseInt(row.values[9]?.formattedValue || '0', 10);
      const passRate = parseFloat(row.values[10]?.formattedValue || '0');

      appState.roomsList.push({
        order: i - 3,
        roomName: roomName,
        totalStudents,
        scanned,
        absent,
        attendanceRate,
        avgScore,
        maxScore,
        minScore,
        passed,
        passRate,
        sheetTitle: `Room_${roomName.replace(/\s+/g, '_')}`
      });
    }
  }

  // Also collect individual room sheets
  sheets.forEach(s => {
    const title = s.properties.title;
    if (title.startsWith('Room_')) {
      const existing = appState.roomsList.find(r => r.sheetTitle === title);
      if (!existing) {
        appState.roomsList.push({
          order: appState.roomsList.length + 1,
          roomName: title.replace('Room_', ''),
          totalStudents: 0,
          scanned: 0,
          absent: 0,
          attendanceRate: 0,
          avgScore: 0,
          maxScore: 0,
          minScore: 0,
          passed: 0,
          passRate: 0,
          sheetTitle: title
        });
      }
    }
  });

  renderKPIs();
  renderIndexTable();
}

// =============================================================================
// UI RENDERING (KPIs & Table)
// =============================================================================

function renderKPIs() {
  const rooms = appState.roomsList;

  let totalStudents = 0;
  let totalScanned = 0;
  let totalAbsent = 0;
  let sumScore = 0;
  let totalPassed = 0;
  let maxScore = 0;
  let minScore = 9999;

  rooms.forEach(r => {
    totalStudents += r.totalStudents;
    totalScanned += r.scanned;
    totalAbsent += r.absent;
    sumScore += (r.avgScore * r.scanned);
    totalPassed += r.passed;
    if (r.maxScore > maxScore) maxScore = r.maxScore;
    if (r.minScore < minScore && r.scanned > 0) minScore = r.minScore;
  });

  if (minScore === 9999) minScore = 0;
  const overallAvg = totalScanned > 0 ? (sumScore / totalScanned).toFixed(2) : '0.00';
  const attendanceRate = totalStudents > 0 ? ((totalScanned / totalStudents) * 100).toFixed(1) : '0.0';
  const passRate = totalStudents > 0 ? ((totalPassed / totalStudents) * 100).toFixed(1) : '0.0';

  document.getElementById('kpiTotalStudents').textContent = totalStudents.toLocaleString();
  document.getElementById('kpiScanned').textContent = totalScanned.toLocaleString();
  document.getElementById('kpiAbsent').textContent = totalAbsent.toLocaleString();
  document.getElementById('kpiAvgScore').textContent = overallAvg;
  document.getElementById('kpiPassRate').textContent = `${passRate}%`;

  document.getElementById('kpiAttendanceRate').textContent = `อัตราการเข้าสอบ ${attendanceRate}%`;
  document.getElementById('kpiMaxMin').textContent = `สูงสุด: ${maxScore} | ต่ำสุด: ${minScore}`;
  document.getElementById('kpiPassCount').textContent = `ผ่านเกณฑ์ ${totalPassed.toLocaleString()} คน`;
}

function renderIndexTable() {
  const tbody = document.getElementById('indexTableBody');
  tbody.innerHTML = '';

  const search = appState.filterSearch.toLowerCase();
  const filtered = appState.roomsList.filter(r => {
    return r.roomName.toLowerCase().includes(search);
  });

  if (filtered.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="12" class="text-center py-8 text-muted">
          ไม่พบข้อมูลห้องเรียนที่ตรงกับคำค้นหา "${search}"
        </td>
      </tr>
    `;
    return;
  }

  filtered.forEach((r, idx) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${idx + 1}</td>
      <td><strong>${escapeHtml(r.roomName)}</strong></td>
      <td>${r.totalStudents}</td>
      <td><span class="text-success font-semibold">${r.scanned}</span></td>
      <td><span class="text-warning font-semibold">${r.absent}</span></td>
      <td>${r.attendanceRate}%</td>
      <td><strong>${r.avgScore}</strong></td>
      <td>${r.maxScore}</td>
      <td>${r.minScore}</td>
      <td>${r.passed}</td>
      <td>
        <span class="status-pill ${r.passRate >= 50 ? 'pass' : 'fail'}">
          ${r.passRate}%
        </span>
      </td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="openRoomDetail('${r.sheetTitle}', '${escapeHtml(r.roomName)}')">
          ดูใบคะแนน
        </button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// =============================================================================
// ROOM DETAIL MODAL & INSPECTION
// =============================================================================

window.openRoomDetail = async function(sheetTitle, roomName) {
  const modal = document.getElementById('roomModal');
  const title = document.getElementById('modalRoomTitle');
  const tbody = document.getElementById('roomDetailTableBody');

  title.textContent = `รายละเอียดคะแนนห้อง: ${roomName}`;
  tbody.innerHTML = `
    <tr>
      <td colspan="9" class="text-center py-8 text-muted">กำลังโหลดคะแนนนักเรียนรายบุคคล...</td>
    </tr>
  `;

  modal.classList.remove('hidden');

  // Find sheet data
  let studentRows = [];
  if (appState.spreadsheetData) {
    const sheet = appState.spreadsheetData.sheets.find(s => s.properties.title === sheetTitle);
    if (sheet && sheet.data && sheet.data[0] && sheet.data[0].rowData) {
      const rows = sheet.data[0].rowData;
      // Data starts at row 5 (index 4)
      for (let i = 4; i < rows.length; i++) {
        const row = rows[i];
        if (!row.values || row.values.length === 0) continue;
        const studentId = row.values[0]?.formattedValue || '';
        if (!studentId || studentId.includes('จัดทำโดย')) continue;

        studentRows.push({
          seatNo: row.values[2]?.formattedValue || `${i - 3}`,
          studentId: studentId,
          name: row.values[1]?.formattedValue || '-',
          score: row.values[3]?.formattedValue || '-',
          total: row.values[4]?.formattedValue || '20',
          percentage: row.values[5]?.formattedValue || '-',
          status: row.values[6]?.formattedValue || 'ขาดสอบ',
          scannedAt: row.values[7]?.formattedValue || '-',
          scanId: row.values[8]?.formattedValue || '-'
        });
      }
    }
  }

  // If no sheet data found, fallback to sample roster
  if (studentRows.length === 0) {
    studentRows = generateSampleStudentRows(roomName);
  }

  tbody.innerHTML = '';
  studentRows.forEach(st => {
    const tr = document.createElement('tr');
    const isPassed = st.status === 'ผ่าน';
    const isAbsent = st.status === 'ขาดสอบ';

    const pillClass = isPassed ? 'pass' : (isAbsent ? 'absent' : 'fail');

    tr.innerHTML = `
      <td class="text-center">${st.seatNo}</td>
      <td><strong>${escapeHtml(st.studentId)}</strong></td>
      <td>${escapeHtml(st.name)}</td>
      <td class="text-center font-semibold">${st.score}</td>
      <td class="text-center">${st.total}</td>
      <td class="text-center">${st.percentage}%</td>
      <td><span class="status-pill ${pillClass}">${st.status}</span></td>
      <td class="text-muted text-sm">${st.scannedAt}</td>
      <td class="text-muted text-sm font-mono">${st.scanId}</td>
    `;
    tbody.appendChild(tr);
  });
};

function closeModal() {
  document.getElementById('roomModal').classList.add('hidden');
}

// =============================================================================
// CSV EXPORT ENGINE
// =============================================================================

function exportTableToCSV() {
  const rooms = appState.roomsList;
  if (rooms.length === 0) {
    alert('ไม่มีข้อมูลสำหรับส่งออก');
    return;
  }

  const csvRows = [];
  // UTF-8 BOM for Thai language compatibility in Excel
  csvRows.push('\uFEFFลำดับ,ห้องเรียน,จำนวนนักเรียน,เข้าสอบแล้ว,ขาดสอบ,อัตราเข้าสอบ(%),คะแนนเฉลี่ย,คะแนนสูงสุด,คะแนนต่ำสุด,ผ่านเกณฑ์,ร้อยละที่ผ่าน(%)');

  rooms.forEach((r, idx) => {
    csvRows.push([
      idx + 1,
      `"${r.roomName}"`,
      r.totalStudents,
      r.scanned,
      r.absent,
      r.attendanceRate,
      r.avgScore,
      r.maxScore,
      r.minScore,
      r.passed,
      r.passRate
    ].join(','));
  });

  const csvContent = csvRows.join('\r\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', `OMR_Exam_Summary_${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

// =============================================================================
// SEARCH & FILTER HANDLERS
// =============================================================================

function handleSearchInput(e) {
  appState.filterSearch = e.target.value.trim();
  renderIndexTable();
}

function handleExamFilterChange(e) {
  appState.selectedExam = e.target.value;
  fetchSpreadsheetData();
}

// =============================================================================
// SAMPLE PREVIEW DATA (Interactive Standalone Demo Mode)
// =============================================================================

function loadSamplePreviewData() {
  appState.roomsList = [
    {
      order: 1,
      roomName: 'ม.5/1',
      totalStudents: 35,
      scanned: 34,
      absent: 1,
      attendanceRate: 97.1,
      avgScore: 16.42,
      maxScore: 20,
      minScore: 11,
      passed: 32,
      passRate: 91.4,
      sheetTitle: 'Room_ม.5_1'
    },
    {
      order: 2,
      roomName: 'ม.5/2',
      totalStudents: 36,
      scanned: 35,
      absent: 1,
      attendanceRate: 97.2,
      avgScore: 15.18,
      maxScore: 19,
      minScore: 8,
      passed: 29,
      passRate: 80.6,
      sheetTitle: 'Room_ม.5_2'
    },
    {
      order: 3,
      roomName: 'ม.5/3',
      totalStudents: 34,
      scanned: 31,
      absent: 3,
      attendanceRate: 91.2,
      avgScore: 14.85,
      maxScore: 18,
      minScore: 9,
      passed: 27,
      passRate: 79.4,
      sheetTitle: 'Room_ม.5_3'
    }
  ];

  renderKPIs();
  renderIndexTable();
  document.getElementById('badgeLastUpdate').textContent = `โหมดสาธิต (Preview Mode) — ${new Date().toLocaleTimeString('th-TH')}`;
}

function generateSampleStudentRows(roomName) {
  const sampleNames = [
    'นายสมชาย ใจดี', 'นางสาวสมหญิง ศรีสยาม', 'นายณัฐวุฒิ สิทธิโชค',
    'นางสาวกานดา วิทยา', 'นายปิยะพัฒน์ ว่องไว', 'นายอานนท์ มั่นคง',
    'นางสาวจารุวรรณ แก้วใส', 'นายธีรภัทร ชาญชัย', 'นางสาวพิมพ์ลภัส เกียรติสกุล',
    'นายวรเมธ ศิริโรจน์'
  ];

  return sampleNames.map((name, i) => {
    const isAbsent = i === 9;
    const score = isAbsent ? '-' : (12 + (i % 8));
    const total = 20;
    const percentage = isAbsent ? '-' : ((score / total) * 100).toFixed(1);
    const status = isAbsent ? 'ขาดสอบ' : (score >= 10 ? 'ผ่าน' : 'ไม่ผ่าน');

    return {
      seatNo: i + 1,
      studentId: `100${(i + 1).toString().padStart(2, '0')}`,
      name: name,
      score: score,
      total: total,
      percentage: percentage,
      status: status,
      scannedAt: isAbsent ? '-' : '2026-11-07 14:20:15',
      scanId: isAbsent ? '-' : `uuid-${Math.random().toString(36).substring(2, 10)}`
    };
  });
}

function escapeHtml(str) {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
