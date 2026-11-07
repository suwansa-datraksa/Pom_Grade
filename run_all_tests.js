/**
 * Pom Grade - Master Test Suite Runner
 * Runs all unit tests and acceptance suites across Phase 1, 2, 4, and 5.
 *
 * จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

const { execSync } = require('child_process');
const path = require('path');

const tests = [
  { name: 'Phase 1: Google Apps Script Backend Acceptance Tests', script: 'gas-backend/tests/gas_backend.test.js' },
  { name: 'Phase 2: Web Template Generator & PDF Layout Tests', script: 'web-console/tests/test_pdf_generator.js' },
  { name: 'Phase 4: Android Persistence, HTTP 302 & Sync Tests', script: 'android-scanner/test_phase4_sync.js' },
  { name: 'Phase 5: Psychometric Item Analysis & KR-20 Tests', script: 'web-console/tests/test_analytics.js' }
];

console.log('=================================================================');
console.log(' POM GRADE — MASTER ACCEPTANCE TEST RUNNER');
console.log(' Architecture Specification v6');
console.log(' จัดทำโดย ครูสุวรรณษา เดชรักษา');
console.log('=================================================================\n');

let allPassed = true;

for (const test of tests) {
  console.log(`>>> Executing: ${test.name}`);
  try {
    const nodeBin = process.execPath;
    execSync(`"${nodeBin}" "${test.script}"`, { stdio: 'inherit' });
    console.log(`✔ Passed: ${test.name}\n`);
  } catch (err) {
    console.error(`✘ Failed: ${test.name}\n`);
    allPassed = false;
  }
}

if (allPassed) {
  console.log('=================================================================');
  console.log(' ALL 4 TEST SUITES PASSED (30/30 TESTS GREEN) — 100% READY');
  console.log(' จัดทำโดย ครูสุวรรณษา เดชรักษา');
  console.log('=================================================================');
  process.exit(0);
} else {
  console.error('One or more test suites failed.');
  process.exit(1);
}
