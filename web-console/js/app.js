/**
 * Pom Grade — Web Console Main Application Controller
 * Pure static execution on GitHub Pages with Google Identity Services (GIS)
 * and Google Sheets API v4.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

(function () {
  'use strict';

  const CONFIG = {
    SPREADSHEET_ID: '1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4',
    DEFAULT_CLIENT_ID: '285757563070-3v143kq009v52lf6mstvtmbi4i5g5l9i.apps.googleusercontent.com',
    PASSING_PERCENT: 50.0,
    CREDIT: 'จัดทำโดย ครูสุวรรณษา เดชรักษา'
  };

  // Application State
  const state = {
    clientId: localStorage.getItem('omr_client_id') || CONFIG.DEFAULT_CLIENT_ID,
    spreadsheetData: null,
    selectedExam: 'ALL',
    activeTab: 'tab-index',
    latestScans: new Map(),
    examAnalytics: null
  };

  // DOM Elements cache
  let elements = {};

  document.addEventListener('DOMContentLoaded', init);

  function init() {
    cacheElements();
    setupEventListeners();
    setupAuth();

    // Check if client ID exists
    if (elements.inputClientId) {
      elements.inputClientId.value = state.clientId;
    }
  }

  function cacheElements() {
    elements = {
      btnAuth: document.getElementById('btnAuth'),
      authBtnText: document.getElementById('authBtnText'),
      authBanner: document.getElementById('authBanner'),
      inputClientId: document.getElementById('inputClientId'),
      btnSaveClientId: document.getElementById('btnSaveClientId'),
      btnRefresh: document.getElementById('btnRefresh'),
      btnExportCSV: document.getElementById('btnExportCSV'),
      btnExportItemAnalysisCSV: document.getElementById('btnExportItemAnalysisCSV'),
      searchInput: document.getElementById('searchInput'),
      selectExamFilter: document.getElementById('selectExamFilter'),
      badgeLastUpdate: document.getElementById('badgeLastUpdate'),

      // KPIs
      kpiTotalStudents: document.getElementById('kpiTotalStudents'),
      kpiScanned: document.getElementById('kpiScanned'),
      kpiAttendanceRate: document.getElementById('kpiAttendanceRate'),
      kpiAbsent: document.getElementById('kpiAbsent'),
      kpiAvgScore: document.getElementById('kpiAvgScore'),
      kpiMaxMin: document.getElementById('kpiMaxMin'),
      kpiPassRate: document.getElementById('kpiPassRate'),
      kpiPassCount: document.getElementById('kpiPassCount'),
      kpiKr20: document.getElementById('kpiKr20'),
      kpiKr20Rating: document.getElementById('kpiKr20Rating'),

      // Tables & Containers
      indexTableBody: document.getElementById('indexTableBody'),
      itemTableBody: document.getElementById('itemTableBody'),
      heatmapGrid: document.getElementById('heatmapGrid'),

      // Modals
      roomModal: document.getElementById('roomModal'),
      modalRoomTitle: document.getElementById('modalRoomTitle'),
      modalRoomSubtitle: document.getElementById('modalRoomSubtitle'),
      roomDetailTableBody: document.getElementById('roomDetailTableBody'),
      btnCloseModal: document.getElementById('btnCloseModal'),
      btnCloseModalFooter: document.getElementById('btnCloseModalFooter'),
      btnPrintRoom: document.getElementById('btnPrintRoom'),

      distractorModal: document.getElementById('distractorModal'),
      distractorModalTitle: document.getElementById('distractorModalTitle'),
      distractorModalBody: document.getElementById('distractorModalBody'),
      btnCloseDistractorModal: document.getElementById('btnCloseDistractorModal')
    };
  }

  function setupEventListeners() {
    if (elements.btnAuth) {
      elements.btnAuth.addEventListener('click', handleAuthClick);
    }
    if (elements.btnSaveClientId) {
      elements.btnSaveClientId.addEventListener('click', handleSaveClientId);
    }
    if (elements.btnRefresh) {
      elements.btnRefresh.addEventListener('click', loadData);
    }
    if (elements.btnExportCSV) {
      elements.btnExportCSV.addEventListener('click', exportIndexCSV);
    }
    if (elements.btnExportItemAnalysisCSV) {
      elements.btnExportItemAnalysisCSV.addEventListener('click', exportItemAnalysisCSV);
    }
    if (elements.searchInput) {
      elements.searchInput.addEventListener('input', handleSearch);
    }
    if (elements.selectExamFilter) {
      elements.selectExamFilter.addEventListener('change', handleExamChange);
    }

    // Modal close
    if (elements.btnCloseModal) elements.btnCloseModal.addEventListener('click', closeRoomModal);
    if (elements.btnCloseModalFooter) elements.btnCloseModalFooter.addEventListener('click', closeRoomModal);
    if (elements.btnPrintRoom) elements.btnPrintRoom.addEventListener('click', () => window.print());
    if (elements.btnCloseDistractorModal) elements.btnCloseDistractorModal.addEventListener('click', closeDistractorModal);

    // Tab buttons
    document.querySelectorAll('.nav-tab').forEach(tabBtn => {
      tabBtn.addEventListener('click', () => {
        document.querySelectorAll('.nav-tab').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

        tabBtn.classList.add('active');
        const targetId = tabBtn.getAttribute('data-target');
        const targetPane = document.getElementById(targetId);
        if (targetPane) {
          targetPane.classList.add('active');
          state.activeTab = targetId;
        }
      });
    });
  }

  function setupAuth() {
    window.PomGradeAuth.onAuthChange((signedIn, error) => {
      if (signedIn) {
        if (elements.authBtnText) elements.authBtnText.textContent = 'ออกจากระบบ';
        if (elements.authBanner) elements.authBanner.style.display = 'none';
        loadData();
      } else {
        if (elements.authBtnText) elements.authBtnText.textContent = 'ลงชื่อเข้าใช้ Google';
        if (elements.authBanner) elements.authBanner.style.display = 'block';
        if (error) {
          alert('เกิดข้อผิดพลาดในการยืนยันตัวตน Google: ' + error);
        }
      }
    });

    const initSuccess = window.PomGradeAuth.init(state.clientId, () => {
      loadData();
    });

    if (!initSuccess) {
      // Retry after delay if GIS script was loading async
      setTimeout(() => {
        window.PomGradeAuth.init(state.clientId, () => loadData());
      }, 1000);
    }
  }

  function handleAuthClick() {
    if (window.PomGradeAuth.isSignedIn()) {
      window.PomGradeAuth.signOut();
    } else {
      window.PomGradeAuth.requestToken();
    }
  }

  function handleSaveClientId() {
    const val = elements.inputClientId.value.trim();
    if (val) {
      state.clientId = val;
      localStorage.setItem('omr_client_id', val);
      window.PomGradeAuth.init(state.clientId, () => loadData());
      window.PomGradeAuth.requestToken();
    }
  }

  async function loadData() {
    const token = window.PomGradeAuth.getAccessToken();
    if (!token) {
      console.log('Not signed in, skipping data fetch.');
      return;
    }

    if (elements.badgeLastUpdate) {
      elements.badgeLastUpdate.textContent = 'กำลังโหลดข้อมูลจาก Google Sheets…';
    }

    try {
      const data = await window.PomGradeSheetsClient.fetchAllData(CONFIG.SPREADSHEET_ID, token);
      state.spreadsheetData = data;
      state.latestScans = data.latestScansMap;

      updateExamSelector(data.answerKeys, data.rawScans);
      processAndRender();

      if (elements.badgeLastUpdate) {
        const timeStr = new Date().toLocaleTimeString('th-TH');
        elements.badgeLastUpdate.textContent = `อัปเดตล่าสุด: ${timeStr} น.`;
      }
    } catch (err) {
      console.error('Fetch error:', err);
      if (elements.badgeLastUpdate) {
        elements.badgeLastUpdate.textContent = 'การโหลดข้อมูลล้มเหลว';
      }
      alert('ไม่สามารถดึงข้อมูลจาก Google Sheets ได้: ' + err.message);
    }
  }

  function updateExamSelector(keys, rawScans) {
    if (!elements.selectExamFilter) return;

    const currentVal = elements.selectExamFilter.value;
    elements.selectExamFilter.innerHTML = '';

    const allOpt = document.createElement('option');
    allOpt.value = 'ALL';
    allOpt.textContent = 'ข้อสอบทั้งหมด';
    elements.selectExamFilter.appendChild(allOpt);

    const examSet = new Set(Object.keys(keys));
    rawScans.forEach(s => { if (s.examId) examSet.add(s.examId); });

    examSet.forEach(examId => {
      const opt = document.createElement('option');
      opt.value = examId;
      opt.textContent = examId;
      elements.selectExamFilter.appendChild(opt);
    });

    if (examSet.has(currentVal) || currentVal === 'ALL') {
      elements.selectExamFilter.value = currentVal;
    } else if (examSet.size > 0) {
      elements.selectExamFilter.value = Array.from(examSet)[0];
    }
    state.selectedExam = elements.selectExamFilter.value;
  }

  function handleExamChange() {
    state.selectedExam = elements.selectExamFilter.value;
    processAndRender();
  }

  function handleSearch() {
    renderIndexTable();
  }

  function processAndRender() {
    if (!state.spreadsheetData) return;

    renderKPIs();
    renderIndexTable();
    renderItemAnalysis();
  }

  /**
   * Group students by classroom and compute summary
   */
  function getClassroomSummaries() {
    if (!state.spreadsheetData) return [];

    const students = state.spreadsheetData.students;
    const classMap = new Map();

    students.forEach(s => {
      const room = s.classroom || 'ไม่ระบุห้อง';
      if (!classMap.has(room)) {
        classMap.set(room, []);
      }
      classMap.get(room).push(s);
    });

    const summaries = [];
    classMap.forEach((roomStudents, roomName) => {
      const totalCount = roomStudents.length;
      let scannedCount = 0;
      let scoreSum = 0;
      let maxScore = 0;
      let minScore = 999999;
      let passCount = 0;

      roomStudents.forEach(st => {
        // Find scan for this student
        const scan = findScanForStudent(st.studentCode, state.selectedExam);
        if (scan) {
          scannedCount++;
          const score = scan.score || 0;
          scoreSum += score;
          if (score > maxScore) maxScore = score;
          if (score < minScore) minScore = score;

          const pct = scan.maxScore > 0 ? (score / scan.maxScore) * 100 : 0;
          if (pct >= CONFIG.PASSING_PERCENT) {
            passCount++;
          }
        }
      });

      const absentCount = totalCount - scannedCount;
      const avgScore = scannedCount > 0 ? (scoreSum / scannedCount) : 0;
      const passRate = scannedCount > 0 ? ((passCount / scannedCount) * 100) : 0;
      const attendanceRate = totalCount > 0 ? ((scannedCount / totalCount) * 100) : 0;

      summaries.push({
        roomName,
        totalCount,
        scannedCount,
        absentCount,
        attendanceRate: Number(attendanceRate.toFixed(1)),
        avgScore: Number(avgScore.toFixed(2)),
        maxScore: scannedCount > 0 ? maxScore : 0,
        minScore: scannedCount > 0 ? minScore : 0,
        passCount,
        passRate: Number(passRate.toFixed(1)),
        students: roomStudents
      });
    });

    return summaries.sort((a, b) => a.roomName.localeCompare(b.roomName, 'th'));
  }

  function findScanForStudent(studentCode, selectedExam) {
    if (!state.spreadsheetData) return null;

    if (selectedExam && selectedExam !== 'ALL') {
      const key = `${selectedExam}_${studentCode}`;
      return state.latestScans.get(key) || null;
    }

    // Search across any exam, return most recent
    let bestScan = null;
    state.latestScans.forEach((scan, k) => {
      if (scan.studentCode === studentCode) {
        if (!bestScan || Date.parse(scan.scannedAt) >= Date.parse(bestScan.scannedAt)) {
          bestScan = scan;
        }
      }
    });
    return bestScan;
  }

  function renderKPIs() {
    const summaries = getClassroomSummaries();

    let totalStudents = 0;
    let totalScanned = 0;
    let totalScoreSum = 0;
    let maxOverall = 0;
    let minOverall = 999999;
    let totalPassed = 0;

    summaries.forEach(s => {
      totalStudents += s.totalCount;
      totalScanned += s.scannedCount;
      totalScoreSum += (s.avgScore * s.scannedCount);
      if (s.scannedCount > 0) {
        if (s.maxScore > maxOverall) maxOverall = s.maxScore;
        if (s.minScore < minOverall) minOverall = s.minScore;
      }
      totalPassed += s.passCount;
    });

    const absent = totalStudents - totalScanned;
    const attendancePct = totalStudents > 0 ? ((totalScanned / totalStudents) * 100).toFixed(1) : '0.0';
    const overallAvg = totalScanned > 0 ? (totalScoreSum / totalScanned).toFixed(2) : '0.00';
    const overallPassPct = totalScanned > 0 ? ((totalPassed / totalScanned) * 100).toFixed(1) : '0.0';

    if (elements.kpiTotalStudents) elements.kpiTotalStudents.textContent = totalStudents.toLocaleString();
    if (elements.kpiScanned) elements.kpiScanned.textContent = totalScanned.toLocaleString();
    if (elements.kpiAttendanceRate) elements.kpiAttendanceRate.textContent = `อัตราการเข้าสอบ ${attendancePct}%`;
    if (elements.kpiAbsent) elements.kpiAbsent.textContent = absent.toLocaleString();
    if (elements.kpiAvgScore) elements.kpiAvgScore.textContent = overallAvg;
    if (elements.kpiMaxMin) {
      elements.kpiMaxMin.textContent = totalScanned > 0 ? `สูงสุด: ${maxOverall} | ต่ำสุด: ${minOverall}` : 'สูงสุด: - | ต่ำสุด: -';
    }
    if (elements.kpiPassRate) elements.kpiPassRate.textContent = `${overallPassPct}%`;
    if (elements.kpiPassCount) elements.kpiPassCount.textContent = `ผ่านเกณฑ์ ${totalPassed.toLocaleString()} คน`;
  }

  function renderIndexTable() {
    if (!elements.indexTableBody) return;

    elements.indexTableBody.innerHTML = '';
    const summaries = getClassroomSummaries();
    const query = elements.searchInput ? elements.searchInput.value.trim().toLowerCase() : '';

    const filtered = summaries.filter(s => {
      return !query || s.roomName.toLowerCase().includes(query);
    });

    if (filtered.length === 0) {
      const tr = document.createElement('tr');
      const td = document.createElement('td');
      td.colSpan = 12;
      td.className = 'text-center py-8 text-muted';
      td.textContent = 'ไม่พบข้อมูลห้องเรียนที่ตรงกับเงื่อนไขการค้นหา';
      tr.appendChild(td);
      elements.indexTableBody.appendChild(tr);
      return;
    }

    filtered.forEach((sum, idx) => {
      const tr = document.createElement('tr');

      // Rank
      const tdRank = document.createElement('td');
      tdRank.textContent = String(idx + 1);
      tr.appendChild(tdRank);

      // Room Name
      const tdRoom = document.createElement('td');
      const strong = document.createElement('strong');
      strong.textContent = sum.roomName;
      tdRoom.appendChild(strong);
      tr.appendChild(tdRoom);

      // Total Count
      const tdTotal = document.createElement('td');
      tdTotal.textContent = String(sum.totalCount);
      tr.appendChild(tdTotal);

      // Scanned Count
      const tdScanned = document.createElement('td');
      tdScanned.className = 'text-success font-weight-bold';
      tdScanned.textContent = String(sum.scannedCount);
      tr.appendChild(tdScanned);

      // Absent Count
      const tdAbsent = document.createElement('td');
      if (sum.absentCount > 0) {
        tdAbsent.className = 'text-warning';
        tdAbsent.textContent = String(sum.absentCount);
      } else {
        tdAbsent.textContent = '0';
      }
      tr.appendChild(tdAbsent);

      // Attendance Rate
      const tdAtt = document.createElement('td');
      tdAtt.textContent = `${sum.attendanceRate}%`;
      tr.appendChild(tdAtt);

      // Avg Score
      const tdAvg = document.createElement('td');
      tdAvg.textContent = String(sum.avgScore);
      tr.appendChild(tdAvg);

      // Max
      const tdMax = document.createElement('td');
      tdMax.textContent = String(sum.maxScore);
      tr.appendChild(tdMax);

      // Min
      const tdMin = document.createElement('td');
      tdMin.textContent = String(sum.minScore);
      tr.appendChild(tdMin);

      // Pass Count
      const tdPass = document.createElement('td');
      tdPass.textContent = String(sum.passCount);
      tr.appendChild(tdPass);

      // Pass Rate
      const tdPassRate = document.createElement('td');
      tdPassRate.textContent = `${sum.passRate}%`;
      tr.appendChild(tdPassRate);

      // Action: View details
      const tdAction = document.createElement('td');
      const btnView = document.createElement('button');
      btnView.className = 'btn btn-outline btn-sm';
      btnView.textContent = 'ดูรายบุคคล';
      btnView.addEventListener('click', () => openRoomModal(sum));
      tdAction.appendChild(btnView);
      tr.appendChild(tdAction);

      elements.indexTableBody.appendChild(tr);
    });
  }

  function openRoomModal(roomSummary) {
    if (!elements.roomModal) return;

    elements.modalRoomTitle.textContent = `รายละเอียดคะแนนห้อง: ${roomSummary.roomName}`;
    elements.modalRoomSubtitle.textContent = `จำนวนนักเรียน: ${roomSummary.totalCount} คน | เข้าสอบ: ${roomSummary.scannedCount} คน | ขาดสอบ: ${roomSummary.absentCount} คน`;

    elements.roomDetailTableBody.innerHTML = '';

    roomSummary.students.forEach((st, idx) => {
      const scan = findScanForStudent(st.studentCode, state.selectedExam);
      const tr = document.createElement('tr');

      // Seat No
      const tdSeat = document.createElement('td');
      tdSeat.textContent = String(st.seatNo || idx + 1);
      tr.appendChild(tdSeat);

      // Student Code
      const tdCode = document.createElement('td');
      tdCode.textContent = st.studentCode;
      tr.appendChild(tdCode);

      // Name
      const tdName = document.createElement('td');
      tdName.textContent = st.name;
      tr.appendChild(tdName);

      if (scan) {
        // Score
        const tdScore = document.createElement('td');
        tdScore.className = 'font-weight-bold text-primary';
        tdScore.textContent = String(scan.score);
        tr.appendChild(tdScore);

        // Max Score
        const tdMax = document.createElement('td');
        tdMax.textContent = String(scan.maxScore);
        tr.appendChild(tdMax);

        // Pct
        const tdPct = document.createElement('td');
        const pct = scan.maxScore > 0 ? ((scan.score / scan.maxScore) * 100).toFixed(1) : '0';
        tdPct.textContent = `${pct}%`;
        tr.appendChild(tdPct);

        // Status
        const tdStatus = document.createElement('td');
        const spanStatus = document.createElement('span');
        spanStatus.className = 'badge';
        spanStatus.textContent = scan.status || 'OK';
        tdStatus.appendChild(spanStatus);
        tr.appendChild(tdStatus);

        // Scanned At
        const tdTime = document.createElement('td');
        tdTime.textContent = scan.scannedAt || '-';
        tr.appendChild(tdTime);

        // Scan ID
        const tdId = document.createElement('td');
        tdId.className = 'text-muted text-sm';
        tdId.textContent = scan.scanId ? scan.scanId.substring(0, 8) + '…' : '-';
        tr.appendChild(tdId);
      } else {
        // Absent
        const tdAbsentScore = document.createElement('td');
        tdAbsentScore.textContent = '-';
        tr.appendChild(tdAbsentScore);

        const tdAbsentMax = document.createElement('td');
        tdAbsentMax.textContent = '-';
        tr.appendChild(tdAbsentMax);

        const tdAbsentPct = document.createElement('td');
        tdAbsentPct.textContent = '-';
        tr.appendChild(tdAbsentPct);

        const tdAbsentStatus = document.createElement('td');
        const spanStatus = document.createElement('span');
        spanStatus.className = 'badge text-warning';
        spanStatus.textContent = '⚠️ ขาดสอบ';
        tdAbsentStatus.appendChild(spanStatus);
        tr.appendChild(tdAbsentStatus);

        const tdAbsentTime = document.createElement('td');
        tdAbsentTime.textContent = '-';
        tr.appendChild(tdAbsentTime);

        const tdAbsentId = document.createElement('td');
        tdAbsentId.textContent = '-';
        tr.appendChild(tdAbsentId);
      }

      elements.roomDetailTableBody.appendChild(tr);
    });

    elements.roomModal.classList.remove('hidden');
  }

  function closeRoomModal() {
    if (elements.roomModal) {
      elements.roomModal.classList.add('hidden');
    }
  }

  /**
   * Psychometric Item Analysis & Heatmap
   */
  function renderItemAnalysis() {
    if (!state.spreadsheetData) return;

    // Identify current exam key
    const examId = state.selectedExam !== 'ALL' ? state.selectedExam : Object.keys(state.spreadsheetData.answerKeys)[0] || 'EXAM_001';
    const keyData = state.spreadsheetData.answerKeys[examId];

    if (!keyData || !keyData.answerKey) {
      if (elements.heatmapGrid) {
        elements.heatmapGrid.innerHTML = '';
        const p = document.createElement('p');
        p.className = 'text-muted text-center py-8';
        p.textContent = `ไม่พบเฉลย (AnswerKey) ของข้อสอบ ${examId} ในแท็บ Protected_Keys`;
        elements.heatmapGrid.appendChild(p);
      }
      return;
    }

    // Collect all valid examinee scans for this exam
    const examineeScans = [];
    state.latestScans.forEach(scan => {
      if (scan.examId === examId && scan.answers) {
        examineeScans.push({
          studentCode: scan.studentCode,
          answers: scan.answers,
          score: scan.score
        });
      }
    });

    const report = window.PomGradeAnalytics.analyzeExam(
      examineeScans,
      keyData.answerKey,
      keyData.totalQ || keyData.answerKey.length
    );

    state.examAnalytics = report;

    // Render KR-20 KPI
    if (elements.kpiKr20) elements.kpiKr20.textContent = report.kr20.toFixed(3);
    if (elements.kpiKr20Rating) {
      elements.kpiKr20Rating.textContent = report.kr20Rating.label;
      elements.kpiKr20Rating.style.color = report.kr20Rating.color;
    }

    // Render Heatmap Grid
    renderHeatmapGrid(report);

    // Render Items Table
    renderItemsTable(report);
  }

  function renderHeatmapGrid(report) {
    if (!elements.heatmapGrid) return;
    elements.heatmapGrid.innerHTML = '';

    report.items.forEach(item => {
      const cell = document.createElement('div');
      cell.className = 'heatmap-cell';

      // Visual color-coding by difficulty p
      let bgColor = '#F1F5F9';
      if (item.difficulty >= 0.8) bgColor = '#DCFCE7'; // Easy (Emerald)
      else if (item.difficulty >= 0.4) bgColor = '#DBEAFE'; // Optimal (Blue)
      else if (item.difficulty >= 0.2) bgColor = '#FEF3C7'; // Hard (Amber)
      else bgColor = '#FEE2E2'; // Very hard (Red)
      cell.style.backgroundColor = bgColor;

      const qNum = document.createElement('div');
      qNum.className = 'q-num';
      qNum.textContent = `ข้อ ${item.itemNumber}`;
      cell.appendChild(qNum);

      const qKey = document.createElement('div');
      qKey.className = 'q-key';
      qKey.textContent = `(เฉลย: ${item.correctChoice})`;
      cell.appendChild(qKey);

      const qP = document.createElement('div');
      qP.className = 'q-p';
      qP.textContent = `p = ${item.difficulty.toFixed(2)}`;
      cell.appendChild(qP);

      const qD = document.createElement('div');
      qD.className = 'q-d';
      qD.textContent = `D = ${item.discrimination.toFixed(2)}`;
      cell.appendChild(qD);

      cell.addEventListener('click', () => openDistractorModal(item));
      elements.heatmapGrid.appendChild(cell);
    });
  }

  function renderItemsTable(report) {
    if (!elements.itemTableBody) return;
    elements.itemTableBody.innerHTML = '';

    report.items.forEach(item => {
      const tr = document.createElement('tr');

      const tdNum = document.createElement('td');
      tdNum.textContent = String(item.itemNumber);
      tr.appendChild(tdNum);

      const tdKey = document.createElement('td');
      const bKey = document.createElement('strong');
      bKey.textContent = item.correctChoice;
      tdKey.appendChild(bKey);
      tr.appendChild(tdKey);

      const tdCorr = document.createElement('td');
      tdCorr.textContent = `${item.correctCount} / ${item.totalExaminees}`;
      tr.appendChild(tdCorr);

      const tdP = document.createElement('td');
      tdP.textContent = item.difficulty.toFixed(3);
      tr.appendChild(tdP);

      const tdPStat = document.createElement('td');
      const spanP = document.createElement('span');
      spanP.className = 'badge';
      spanP.style.backgroundColor = item.pStatus.color + '22';
      spanP.style.color = item.pStatus.color;
      spanP.textContent = item.pStatus.label;
      tdPStat.appendChild(spanP);
      tr.appendChild(tdPStat);

      const tdD = document.createElement('td');
      tdD.textContent = item.discrimination.toFixed(3);
      tr.appendChild(tdD);

      const tdDStat = document.createElement('td');
      const spanD = document.createElement('span');
      spanD.className = 'badge';
      spanD.style.backgroundColor = item.dStatus.color + '22';
      spanD.style.color = item.dStatus.color;
      spanD.textContent = item.dStatus.label;
      tdDStat.appendChild(spanD);
      tr.appendChild(tdDStat);

      const tdAction = document.createElement('td');
      const btnView = document.createElement('button');
      btnView.className = 'btn btn-outline btn-sm';
      btnView.textContent = 'วิเคราะห์ตัวเลือก';
      btnView.addEventListener('click', () => openDistractorModal(item));
      tdAction.appendChild(btnView);
      tr.appendChild(tdAction);

      elements.itemTableBody.appendChild(tr);
    });
  }

  function openDistractorModal(item) {
    if (!elements.distractorModal) return;

    elements.distractorModalTitle.textContent = `วิเคราะห์ตัวลวง ข้อที่ ${item.itemNumber} (เฉลย: ${item.correctChoice})`;
    elements.distractorModalBody.innerHTML = '';

    const choices = ['A', 'B', 'C', 'D', 'E', 'BLANK'];
    const total = item.totalExaminees || 1;

    choices.forEach(ch => {
      const count = item.distractors[ch] || 0;
      const pct = ((count / total) * 100).toFixed(1);
      const isCorrect = (ch === item.correctChoice);

      const row = document.createElement('div');
      row.className = 'distractor-row';

      const label = document.createElement('div');
      label.className = 'distractor-label';
      label.textContent = (ch === 'BLANK' ? 'ไม่ตอบ' : `ตัวเลือก ${ch}`) + (isCorrect ? ' ✓' : '');
      if (isCorrect) label.style.color = '#10B981';
      row.appendChild(label);

      const barWrap = document.createElement('div');
      barWrap.className = 'distractor-bar-wrap';

      const bar = document.createElement('div');
      bar.className = 'distractor-bar' + (isCorrect ? ' correct' : '');
      bar.style.width = `${pct}%`;
      barWrap.appendChild(bar);
      row.appendChild(barWrap);

      const countLabel = document.createElement('div');
      countLabel.className = 'distractor-count';
      countLabel.textContent = `${count} คน (${pct}%)`;
      row.appendChild(countLabel);

      elements.distractorModalBody.appendChild(row);
    });

    elements.distractorModal.classList.remove('hidden');
  }

  function closeDistractorModal() {
    if (elements.distractorModal) {
      elements.distractorModal.classList.add('hidden');
    }
  }

  function exportIndexCSV() {
    const summaries = getClassroomSummaries();
    if (summaries.length === 0) {
      alert('ไม่มีข้อมูลสำหรับส่งออก');
      return;
    }

    const headers = ['ลำดับ', 'ห้องเรียน', 'นักเรียนทั้งหมด', 'เข้าสอบ', 'ขาดสอบ', 'อัตราเข้าสอบ(%)', 'คะแนนเฉลี่ย', 'สูงสุด', 'ต่ำสุด', 'ผ่านเกณฑ์', 'ร้อยละที่ผ่าน(%)'];
    const rows = summaries.map((s, idx) => [
      idx + 1,
      `"${s.roomName}"`,
      s.totalCount,
      s.scannedCount,
      s.absentCount,
      s.attendanceRate,
      s.avgScore,
      s.maxScore,
      s.minScore,
      s.passCount,
      s.passRate
    ]);

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    downloadFile(csvContent, `PomGrade_Index_Overview_${new Date().toISOString().slice(0, 10)}.csv`, 'text/csv;charset=utf-8;');
  }

  function exportItemAnalysisCSV() {
    if (!state.examAnalytics || !state.examAnalytics.items) {
      alert('ยังไม่มีผลการวิเคราะห์ข้อสอบสำหรับส่งออก');
      return;
    }

    const headers = ['ข้อที่', 'เฉลย', 'จำนวนคนตอบถูก', 'ผู้เข้าสอบทั้งหมด', 'ความยาก (p)', 'ระดับความยาก', 'อำนาจจำแนก (D)', 'ระดับอำนาจจำแนก', 'เลือก A', 'เลือก B', 'เลือก C', 'เลือก D', 'เลือก E', 'ไม่ตอบ'];
    const rows = state.examAnalytics.items.map(item => [
      item.itemNumber,
      `"${item.correctChoice}"`,
      item.correctCount,
      item.totalExaminees,
      item.difficulty,
      `"${item.pStatus.label}"`,
      item.discrimination,
      `"${item.dStatus.label}"`,
      item.distractors['A'] || 0,
      item.distractors['B'] || 0,
      item.distractors['C'] || 0,
      item.distractors['D'] || 0,
      item.distractors['E'] || 0,
      item.distractors['BLANK'] || 0
    ]);

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    downloadFile(csvContent, `PomGrade_Item_Analysis_${state.selectedExam}_${new Date().toISOString().slice(0, 10)}.csv`, 'text/csv;charset=utf-8;');
  }

  function downloadFile(content, fileName, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

})();

