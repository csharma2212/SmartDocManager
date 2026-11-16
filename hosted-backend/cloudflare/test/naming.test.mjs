import test from 'node:test';
import assert from 'node:assert/strict';
import {
  resolveCategoryCode,
  formatDdMMMyy,
  abbreviateDocType,
  generateCompliantFilename
} from '../src/worker.mjs';

test('resolveCategoryCode maps all 13 categories correctly', () => {
  const expected = {
    'Identity': 'IDN',
    'Education': 'EDU',
    'Medical': 'MED',
    'Finance': 'FIN',
    'Work': 'WRK',
    'Property': 'PRO',
    'Business': 'BIZ',
    'Legal': 'LAW',
    'Transport': 'TPT',
    'Travel': 'TRV',
    'Insurance': 'INS',
    'Utilities': 'UTL',
    'Agriculture': 'AGR'
  };

  for (const [cat, code] of Object.entries(expected)) {
    assert.equal(resolveCategoryCode(cat), code, `Category ${cat} should resolve to ${code}`);
    assert.equal(resolveCategoryCode(cat.toLowerCase()), code, `Lowercase category ${cat} should resolve to ${code}`);
  }
  // Fallbacks
  assert.equal(resolveCategoryCode('Tax Document'), 'FIN');
  assert.equal(resolveCategoryCode('Bank Statement'), 'FIN');
  assert.equal(resolveCategoryCode('Medical Record'), 'MED');
  assert.equal(resolveCategoryCode('Random Unknown Domain'), 'OTH');
});

test('formatDdMMMyy formats various date strings to ddMMMyy', () => {
  assert.equal(formatDdMMMyy('15-Nov-2026'), '15Nov26');
  assert.equal(formatDdMMMyy('2026-11-15'), '15Nov26');
  assert.equal(formatDdMMMyy('15/11/2026'), '15Nov26');
  assert.equal(formatDdMMMyy('Nov 15, 2026'), '15Nov26');
  assert.equal(formatDdMMMyy('01-Jan-2025'), '01Jan25');
  assert.equal(formatDdMMMyy('31-Dec-2024'), '31Dec24');
  
  // Empty or invalid falls back to 7 chars ddMMMyy of today
  const fallback = formatDdMMMyy(null);
  assert.equal(fallback.length, 7);
  assert.match(fallback, /^\d{2}[A-Z][a-z]{2}\d{2}$/);
});

test('abbreviateDocType compresses common terms in PascalCase', () => {
  assert.equal(abbreviateDocType('Income Tax Form 16', 24), 'IncomeTaxForm16');
  assert.equal(abbreviateDocType('Permanent Account Number Card', 24), 'PANCard');
  assert.equal(abbreviateDocType('Aadhaar Card', 24), 'AadhaarCard');
  assert.equal(abbreviateDocType('Hospital Discharge Summary', 24), 'DischSumm');
  assert.equal(abbreviateDocType('Rent Agreement', 24), 'RentAgrmt');
});

test('generateCompliantFilename strictly adheres to CAT_Doc_ddMMMyy.ext and <= 40 chars', () => {
  const example1 = generateCompliantFilename('Finance', 'Income Tax Form 16', '15-Nov-2026', 'sample.pdf');
  assert.equal(example1, 'FIN_IncomeTaxForm16_15Nov26.pdf');
  assert.ok(example1.length <= 40);

  const example2 = generateCompliantFilename('Identity', 'Permanent Account Number Card', '2026-11-15', 'pan.pdf');
  assert.equal(example2, 'IDN_PANCard_15Nov26.pdf');
  assert.ok(example2.length <= 40);

  // Very long document type that exceeds normal bounds
  const longType = 'Super Extremely Long Non-Agricultural Land Conversion Approval Certificate And Order';
  const longFile = generateCompliantFilename('Property', longType, '15-Nov-2026', 'document.pdf');
  assert.ok(longFile.length <= 40, `Filename "${longFile}" has length ${longFile.length} > 40`);
  assert.ok(longFile.startsWith('PRO_'));
  assert.ok(longFile.endsWith('_15Nov26.pdf'));

  // Test with jpeg extension (5 chars: .jpeg)
  const jpegFile = generateCompliantFilename('Medical', 'Diagnostic Laboratory Report', '15-Nov-2026', 'scan.jpeg');
  assert.ok(jpegFile.length <= 40, `Filename "${jpegFile}" has length ${jpegFile.length} > 40`);
  assert.ok(jpegFile.startsWith('MED_'));
  assert.ok(jpegFile.endsWith('_15Nov26.jpeg'));
});
