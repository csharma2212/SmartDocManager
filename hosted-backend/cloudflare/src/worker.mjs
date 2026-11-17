// SmartDocManager: Multimodal and text classification; never log document content or credentials.
const encoder = new TextEncoder();
const json = (data, status = 200, extra = {}) =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff',
      ...extra
    }
  });

function equal(a, b) {
  const x = encoder.encode(a), y = encoder.encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] || 0) ^ (y[i] || 0);
  return diff === 0;
}

const USER_PROMPT_QUESTION =
  "Which kind of document is it, what is its logical category domain, what is its issue date, and what key metadata entities are present?";

const CATEGORY_CODES = {
  identity: 'IDN', idn: 'IDN',
  education: 'EDU', edu: 'EDU',
  medical: 'MED', med: 'MED',
  finance: 'FIN', fin: 'FIN',
  work: 'WRK', wrk: 'WRK',
  property: 'PRO', pro: 'PRO',
  business: 'BIZ', biz: 'BIZ',
  legal: 'LAW', law: 'LAW',
  transport: 'TPT', tpt: 'TPT',
  travel: 'TRV', trv: 'TRV',
  insurance: 'INS', ins: 'INS',
  utilities: 'UTL', utl: 'UTL',
  agriculture: 'AGR', agr: 'AGR'
};

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTH_LOOKUP = {
  jan: 0, january: 0, feb: 1, february: 1, mar: 2, march: 2, apr: 3, april: 3,
  may: 4, jun: 5, june: 5, jul: 6, july: 6, aug: 7, august: 7, sep: 8, sept: 8, september: 8,
  oct: 9, october: 9, nov: 10, november: 10, dec: 11, december: 11
};

export function resolveCategoryCode(cat) {
  if (!cat) return 'OTH';
  const c = String(cat).trim().toLowerCase();
  if (CATEGORY_CODES[c]) return CATEGORY_CODES[c];
  if (c.includes('ident') || c.includes('aadhaar') || c.includes('pan') || c.includes('passport') || c.includes('voter') || c.includes('driving') || c.includes('govt')) return 'IDN';
  if (c.includes('edu') || c.includes('school') || c.includes('degree') || c.includes('marksheet') || c.includes('college')) return 'EDU';
  if (c.includes('med') || c.includes('health') || c.includes('doctor') || c.includes('hospital') || c.includes('lab') || c.includes('prescription')) return 'MED';
  if (c.includes('insur') || c.includes('policy')) return 'INS';
  if (c.includes('util') || c.includes('telecom') || c.includes('broadband') || c.includes('electric') || c.includes('water') || c.includes('gas')) return 'UTL';
  if (c.includes('work') || c.includes('employ') || c.includes('salary') || c.includes('payslip') || c.includes('reliev') || c.includes('appraisal')) return 'WRK';
  if (c.includes('prop') || c.includes('rent') || c.includes('lease') || c.includes('deed') || c.includes('patta')) return 'PRO';
  if (c.includes('agri') || c.includes('kisan') || c.includes('crop') || c.includes('khatauni') || c.includes('khasra')) return 'AGR';
  if (c.includes('biz') || c.includes('gst') || c.includes('incorporation') || c.includes('trade')) return 'BIZ';
  if (c.includes('law') || c.includes('court') || c.includes('affidavit') || c.includes('legal') || c.includes('contract')) return 'LAW';
  if (c.includes('trans') || c.includes('vehicle') || c.includes('fastag') || c.includes('traffic')) return 'TPT';
  if (c.includes('trav') || c.includes('visa') || c.includes('ticket') || c.includes('flight')) return 'TRV';
  if (c.includes('bank') || c.includes('tax') || c.includes('invest') || c.includes('credit') || c.includes('bill') || c.includes('invoice') || c.includes('receipt') || c.includes('challan')) return 'FIN';
  return 'OTH';
}

export function formatDdMMMyy(rawDate) {
  if (rawDate && typeof rawDate === 'string' && rawDate.trim()) {
    const s = rawDate.trim();
    // 1. ISO format: YYYY-MM-DD or YYYY/MM/DD or YYYY.MM.DD
    let m = s.match(/\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b/);
    if (m) {
      const y = parseInt(m[1], 10) % 100;
      const mo = parseInt(m[2], 10) - 1;
      const d = parseInt(m[3], 10);
      if (mo >= 0 && mo < 12 && d >= 1 && d <= 31) {
        return `${String(d).padStart(2, '0')}${MONTH_NAMES[mo]}${String(y).padStart(2, '0')}`;
      }
    }
    // 2. DD-MM-YYYY or DD/MM/YYYY
    m = s.match(/\b(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2}|\d{2})\b/);
    if (m) {
      const d = parseInt(m[1], 10);
      const mo = parseInt(m[2], 10) - 1;
      let y = parseInt(m[3], 10);
      if (y > 1900) y = y % 100;
      if (mo >= 0 && mo < 12 && d >= 1 && d <= 31) {
        return `${String(d).padStart(2, '0')}${MONTH_NAMES[mo]}${String(y).padStart(2, '0')}`;
      }
    }
    // 3. DD-Mon-YYYY or DD Mon YYYY or DDMonYY
    m = s.match(/\b(\d{1,2})[-/\s]*([a-zA-Z]{3,})[-/\s]*(20\d{2}|\d{2})\b/);
    if (m) {
      const d = parseInt(m[1], 10);
      const mStr = m[2].toLowerCase();
      let y = parseInt(m[3], 10);
      if (y > 1900) y = y % 100;
      const mo = MONTH_LOOKUP[mStr] ?? MONTH_LOOKUP[mStr.slice(0, 3)];
      if (mo !== undefined && d >= 1 && d <= 31) {
        return `${String(d).padStart(2, '0')}${MONTH_NAMES[mo]}${String(y).padStart(2, '0')}`;
      }
    }
    // 4. Mon DD, YYYY
    m = s.match(/\b([a-zA-Z]{3,})[-/\s]+(\d{1,2})[-/\s,'"]+(20\d{2}|\d{2})\b/);
    if (m) {
      const mStr = m[1].toLowerCase();
      const d = parseInt(m[2], 10);
      let y = parseInt(m[3], 10);
      if (y > 1900) y = y % 100;
      const mo = MONTH_LOOKUP[mStr] ?? MONTH_LOOKUP[mStr.slice(0, 3)];
      if (mo !== undefined && d >= 1 && d <= 31) {
        return `${String(d).padStart(2, '0')}${MONTH_NAMES[mo]}${String(y).padStart(2, '0')}`;
      }
    }
  }
  // Fallback to today's date formatted as ddMMMyy
  const now = new Date();
  const d = String(now.getUTCDate()).padStart(2, '0');
  const mo = MONTH_NAMES[now.getUTCMonth()];
  const y = String(now.getUTCFullYear() % 100).padStart(2, '0');
  return `${d}${mo}${y}`;
}

const PHRASE_ABBREVIATIONS = {
  'permanent account number card': 'PANCard',
  'permanent account number': 'PANCard',
  'pan card': 'PANCard',
  'aadhaar card': 'AadhaarCard',
  'aadhar card': 'AadhaarCard',
  'voter identity card': 'VoterID',
  'voter id card': 'VoterID',
  'driving license': 'DrivingLic',
  'driving licence': 'DrivingLic',
  'ration card': 'RationCard',
  'income tax return verification form': 'ITRVerification',
  'income tax return': 'ITR',
  'income tax form 16': 'IncomeTaxForm16',
  'form 16': 'Form16',
  'form 16a': 'Form16A',
  'form 26as': 'Form26AS',
  'hospital discharge summary': 'DischSumm',
  'diagnostic laboratory report': 'DiagLabRpt',
  'blood test report': 'BloodTestRpt',
  'pathology report': 'PathRpt',
  'medical prescription': 'MedicalRx',
  'builder-buyer agreement': 'BuilderBuyerAgrmt',
  'rent agreement': 'RentAgrmt',
  'rental agreement': 'RentAgrmt',
  'sale deed': 'SaleDeed',
  'lease deed': 'LeaseDeed',
  'electricity bill': 'ElecBill',
  'water bill': 'WaterBill',
  'piped gas bill': 'GasBill',
  'class 10 marksheet': 'Class10MrkSht',
  'class 12 marksheet': 'Class12MrkSht',
  'school leaving certificate': 'SchlLvgCert',
  'birth certificate': 'BirthCert',
  'death certificate': 'DeathCert',
  'marriage certificate': 'MarriageCert',
  'caste certificate': 'CasteCert',
  'domicile certificate': 'DomicileCert',
  'transfer certificate': 'TransferCert',
  'migration certificate': 'MigrationCert',
  'character certificate': 'CharacterCert',
  'bonafide certificate': 'BonafideCert',
  'degree certificate': 'DegreeCert',
  'provisional degree certificate': 'ProvDegreeCert',
  'health insurance policy': 'HealthInsPolicy',
  'life insurance policy': 'LifeInsPolicy',
  'motor vehicle insurance policy': 'MotorInsPolicy',
  'vehicle registration certificate': 'VehicleRC',
  'pollution under control certificate': 'PollutionPUC'
};

const WORD_ABBREVIATIONS = {
  certificate: 'Cert', certificates: 'Certs',
  agreement: 'Agrmt', agreements: 'Agrmts',
  statement: 'Stmt', statements: 'Stmts',
  registration: 'Reg', application: 'Appln',
  declaration: 'Decl', marksheet: 'MrkSht',
  provisional: 'Prov', permanent: 'Perm',
  account: 'Acct', number: 'No',
  verification: 'Verif', department: 'Dept',
  authority: 'Auth', transfer: 'Trfr',
  migration: 'Migr', character: 'Char',
  examination: 'Exam', secondary: 'Sec',
  higher: 'Hgr', undergraduate: 'UG',
  postgraduate: 'PG', degree: 'Deg',
  school: 'Schl', leaving: 'Lvg',
  semester: 'Sem', scorecard: 'Scorecard',
  confirmation: 'Conf', allotment: 'Allot',
  receipt: 'Rcpt', receipts: 'Rcpts',
  submission: 'Subm', engineering: 'Engg',
  diploma: 'Dipl', industrial: 'Ind',
  training: 'Trng', institute: 'Inst',
  national: 'Natl', development: 'Dev',
  corporation: 'Corp', apprenticeship: 'Appr',
  internship: 'Intern', completion: 'Compl',
  fellowship: 'Fellow', scholarship: 'Schol',
  sanction: 'Sanc', research: 'Rsch',
  proficiency: 'Prof', disability: 'Disab',
  government: 'Govt', health: 'Hlth',
  scheme: 'Schm', employees: 'Emp',
  employee: 'Emp', medical: 'Med',
  fitness: 'Fit', hospital: 'Hosp',
  discharge: 'Disch', summary: 'Summ',
  diagnostic: 'Diag', laboratory: 'Lab',
  prescription: 'Rx', vaccination: 'Vacc',
  report: 'Rpt', reports: 'Rpts',
  pathology: 'Path', treatment: 'Trtmnt',
  deduction: 'Deduc', collection: 'Coll',
  passbook: 'Passbk', recurring: 'Recur',
  consolidated: 'Consol', provident: 'PF',
  pension: 'Pens', payment: 'Pymt',
  savings: 'Sav', saving: 'Sav',
  current: 'Curr', credit: 'Credit',
  card: 'Card', universal: 'Univ',
  withdrawal: 'Wdrwl', appointment: 'Appt',
  relieving: 'Reliev', experience: 'Exp',
  salary: 'Sal', slip: 'Slip',
  appraisal: 'Appr', increment: 'Incr',
  contract: 'Cntrct', settlement: 'Sttlmnt',
  identification: 'ID', commercial: 'Comm',
  residential: 'Res', mutation: 'Mut',
  property: 'Prop', building: 'Bldg',
  approval: 'Apprv', occupancy: 'Occup',
  insurance: 'Ins', premium: 'Prem',
  electricity: 'Elec', telephone: 'Phone',
  broadband: 'BB', internet: 'Net',
  mobile: 'Mob', municipal: 'Muni',
  pollution: 'Poll', driving: 'Drive',
  license: 'Lic', licence: 'Lic',
  vehicle: 'Veh', incorporation: 'Incorp',
  association: 'Assn', limited: 'Ltd',
  clearance: 'Clr', invoice: 'Inv',
  voucher: 'Voucher', booking: 'Book'
};

export function abbreviateDocType(docType, maxLen = 24) {
  if (!docType || !docType.trim()) return 'Document'.slice(0, maxLen);
  const clean = docType.trim();
  const lower = clean.toLowerCase();

  // 1. Exact phrase match
  if (PHRASE_ABBREVIATIONS[lower]) {
    return PHRASE_ABBREVIATIONS[lower].slice(0, maxLen);
  }

  // 2. Tokenize and replace individual words
  const words = clean.split(/[\s_\-]+/).filter(Boolean);
  let pascalParts = [];
  for (const w of words) {
    const wLow = w.toLowerCase();
    if (WORD_ABBREVIATIONS[wLow]) {
      pascalParts.push(WORD_ABBREVIATIONS[wLow]);
    } else {
      const cleaned = w.replace(/[^a-zA-Z0-9]/g, '');
      if (cleaned) {
        pascalParts.push(cleaned.charAt(0).toUpperCase() + cleaned.slice(1));
      }
    }
  }

  const result = pascalParts.join('') || 'Document';
  return result.slice(0, maxLen);
}

export function generateCompliantFilename(category, docType, issuedDate, originalFilename) {
  const catCode = resolveCategoryCode(category);
  const dateFormatted = formatDdMMMyy(issuedDate);

  let ext = '.pdf';
  if (originalFilename && originalFilename.includes('.')) {
    const rawExt = originalFilename.slice(originalFilename.lastIndexOf('.')).toLowerCase();
    if (rawExt.length >= 2 && rawExt.length <= 6) {
      ext = rawExt;
    }
  }

  // Strict 40-character maximum formula:
  // CAT (3) + _ (1) + DocumentName (max) + _ (1) + Date (7) + ext.length <= 40
  const maxDocLen = Math.max(4, 40 - 3 - 1 - 1 - 7 - ext.length);
  const abbrev = abbreviateDocType(docType || category, maxDocLen);

  const filename = `${catCode}_${abbrev}_${dateFormatted}${ext}`;
  if (filename.length > 40) {
    const excess = filename.length - 40;
    const truncatedAbbrev = abbrev.slice(0, Math.max(1, abbrev.length - excess));
    return `${catCode}_${truncatedAbbrev}_${dateFormatted}${ext}`;
  }
  return filename;
}

export function classifyPrompt() {
  return `${USER_PROMPT_QUESTION}

Return ONLY a valid JSON object with the following keys:
- category: The document category domain. Must be one of the following 13 standard categories:
  * Identity
  * Education
  * Medical
  * Finance
  * Work
  * Property
  * Business
  * Legal
  * Transport
  * Travel
  * Insurance
  * Utilities
  * Agriculture
  (or "Other Document" if it cannot be classified into any of these).
- document_type: The specific document type or title under that category (e.g. "Aadhaar Card", "Permanent Account Number Card", "Income Tax Form 16", "Hospital Discharge Summary", "Diagnostic Laboratory Report", "Medical Prescription", "Rent Agreement", "Sale Deed", "Electricity Bill", "Class 10 Marksheet", "Driving License", "Health Insurance Policy", "Vehicle Registration Certificate", etc.).
- issued_date: The issue date, transaction date, or document date in DD-Mon-YYYY format (e.g. "15-Nov-2026"), or null if no date is found.
- specifics: Concise explanation of why the document belongs to this category and document type (clear reasoning with evidence from the document).
- metadata: A JSON object extracting ONLY the relevant key entities and metadata actually present in the document. Do not include empty, null, or irrelevant fields. Examples of relevant fields based on document domain:
  * Medical: doctor_name, patient_name, hospital_name, lab_name, test_name, diagnosis, report_date
  * Property: lessor_name, lessee_name, property_address, registration_number, agreement_period
  * Finance: bank_name, account_number, pan_number, ifsc_code, tax_year, amount, transaction_date
  * Identity: full_name, id_number, date_of_birth, father_name, address
  * Work: employee_name, employer_name, designation, salary_amount, uan_number
  * Education: student_name, institution_name, degree_name, roll_number, passing_year
  * Business: company_name, gst_number, cin_number, invoice_number, counterparty_name
  * Legal: court_name, case_number, party_names, advocate_name
  * Transport/Travel: vehicle_number, registration_number, passport_number, pnr_number
  * Insurance: insurer_name, policy_number, insured_name, sum_insured, premium_amount
  * Utilities: utility_provider, consumer_number, bill_amount, due_date
  * Agriculture: land_parcel_id, khasra_number, khatauni_number, farmer_name
  * General: title, issuer_name, issued_date, amount, identifier, and any other specific key details found in the document.`;
}

function valid(obj) {
  if (!obj || typeof obj.category !== 'string' || obj.category.trim().length === 0) {
    return false;
  }
  const spec = obj.specifics || obj.reasoning || obj.evidence || obj.details || '';
  if (typeof spec !== 'string' || spec.length > 5000) {
    return false;
  }
  obj.specifics = spec || 'Classified by Google Gemini 2.5 Flash';
  return true;
}

function parseModelJson(raw) {
  if (!raw) return null;
  let clean = String(raw).trim();
  if (clean.startsWith('```json')) clean = clean.slice(7);
  else if (clean.startsWith('```')) clean = clean.slice(3);
  if (clean.endsWith('```')) clean = clean.slice(0, -3);
  clean = clean.trim();
  try {
    return JSON.parse(clean);
  } catch {
    const match = clean.match(/\{[\s\S]*\}/);
    if (match) {
      try {
        return JSON.parse(match[0]);
      } catch {}
    }
  }
  return null;
}

async function gemini(payload, env) {
  const candidateModels = [
    env.GEMINI_MODEL,
    'gemini-2.0-flash',
    'gemini-2.0-flash-lite',
    'gemini-1.5-flash',
    'gemini-1.5-flash-8b',
    'gemini-2.5-flash',
    'gemini-3.5-flash-lite',
    'gemini-flash-latest'
  ].filter(Boolean);

  const parts = [];
  const promptText = classifyPrompt();

  if (typeof payload === 'object' && payload.file_data && payload.mime_type) {
    parts.push({
      inlineData: {
        mimeType: payload.mime_type,
        data: payload.file_data
      }
    });
    parts.push({ text: promptText });
  } else {
    const textContent = typeof payload === 'string' ? payload : (payload.text || '');
    parts.push({ text: `${promptText}\n\nDocument text content:\n${textContent}` });
  }

  let lastError = null;
  for (const model of candidateModels) {
    try {
      const response = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${env.GEMINI_API_KEY}`,
        {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({
            contents: [{ role: 'user', parts }],
            generationConfig: { responseMimeType: 'application/json', temperature: 0 }
          }),
          signal: AbortSignal.timeout(60000)
        }
      );
      if (!response.ok) {
        let errBody = null;
        try {
          errBody = await response.json();
        } catch {
          try { errBody = { error: { message: await response.text() } }; } catch {}
        }
        const gErr = errBody?.error || {};
        lastError = {
          error: true,
          status: response.status,
          retry: response.headers.get('retry-after'),
          gemini_model: model,
          gemini_code: gErr.code || response.status,
          gemini_status: gErr.status || (response.status === 404 ? 'NOT_FOUND' : response.status === 429 ? 'RESOURCE_EXHAUSTED' : 'API_ERROR'),
          gemini_message: gErr.message || response.statusText || 'Unknown Gemini error'
        };
        // 404 = model retired/unavailable for this key; 503 = temporary demand spike; try next model
        if (response.status === 404 || response.status === 503) {
          continue;
        }
        return lastError;
      }
      const body = await response.json();
      const candidate = body.candidates?.[0]?.content?.parts?.[0]?.text || 'null';
      const obj = parseModelJson(candidate);
      if (valid(obj)) {
        return { result: obj, model };
      }
      lastError = {
        error: true,
        status: 502,
        gemini_model: model,
        gemini_status: 'SCHEMA_VALIDATION_FAILED',
        gemini_message: 'Model response was not valid document taxonomy JSON',
        raw_candidate: candidate
      };
    } catch (e) {
      lastError = {
        error: true,
        status: 502,
        gemini_model: model,
        gemini_status: 'FETCH_EXCEPTION',
        gemini_message: e?.message || 'Network/timeout connecting to Gemini API'
      };
    }
  }
  return lastError || { error: true, status: 502, gemini_status: 'ALL_MODELS_FAILED', gemini_message: 'All candidate models failed' };
}

async function groq(payload, env) {
  const textContent = typeof payload === 'string' ? payload : (payload.text || '');
  if (!textContent.trim()) return { error: true, status: 502 };

  const candidateModels = [
    env.GROQ_MODEL,
    'llama-3.1-8b-instant',
    'llama-3.3-70b-versatile'
  ].filter(Boolean);

  let lastError = null;
  for (const model of candidateModels) {
    try {
      const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
        method: 'POST',
        headers: { authorization: `Bearer ${env.GROQ_API_KEY}`, 'content-type': 'application/json' },
        body: JSON.stringify({
          model,
          temperature: 0,
          response_format: { type: 'json_object' },
          messages: [
            { role: 'system', content: classifyPrompt() },
            { role: 'user', content: textContent }
          ]
        }),
        signal: AbortSignal.timeout(45000)
      });
      if (!response.ok) {
        let errBody = null;
        try { errBody = await response.json(); } catch {}
        lastError = {
          error: true,
          provider: 'groq',
          groq_model: model,
          status: response.status,
          retry: response.headers.get('retry-after'),
          groq_status: errBody?.error?.code || errBody?.error?.type || 'GROQ_API_ERROR',
          groq_message: errBody?.error?.message || response.statusText
        };
        if (response.status === 404 || (response.status === 400 && String(errBody?.error?.message).toLowerCase().includes('model'))) {
          continue;
        }
        return lastError;
      }
      const body = await response.json();
      const obj = parseModelJson(body.choices?.[0]?.message?.content || 'null');
      if (valid(obj)) {
        return { result: obj, model };
      }
      lastError = {
        error: true,
        provider: 'groq',
        groq_model: model,
        status: 502,
        groq_status: 'SCHEMA_VALIDATION_FAILED',
        groq_message: 'Groq response was not valid taxonomy JSON'
      };
    } catch (e) {
      lastError = {
        error: true,
        provider: 'groq',
        groq_model: model,
        status: 502,
        groq_status: 'FETCH_EXCEPTION',
        groq_message: e?.message || 'Network error connecting to Groq'
      };
    }
  }
  return lastError || { error: true, provider: 'groq', status: 502, groq_status: 'ALL_MODELS_FAILED', groq_message: 'All Groq models failed' };
}

// Validate Google-issued OIDC ID tokens server-side.
async function authenticate(request, env) {
  const auth = request.headers.get('authorization') || '';
  if (!auth.startsWith('Bearer ')) return { id: null, reason: 'MISSING_BEARER_AUTH', message: 'Authorization header must start with Bearer' };
  const token = auth.slice(7);
  // Optional migration for existing private clients; disable for public deployment.
  const legacyToken = env.SMARTDOC_CLIENT_TOKEN || env.BACKEND_ACCESS_TOKEN;
  if ((env.ALLOW_LEGACY_TOKEN === 'true' || legacyToken) && legacyToken && equal(auth, `Bearer ${legacyToken}`)) {
    return { id: 'legacy' };
  }
  const defaultClientIds = '492541392963-up746ino0bjdhufq32g1cnq2tqqbqs8o.apps.googleusercontent.com,492541392963-b0mf650fohoas9m6a24fa32enrstjrr6.apps.googleusercontent.com';
  const rawAllowed = [
    env.GOOGLE_CLIENT_IDS,
    env.GOOGLE_DESKTOP_CLIENT_ID,
    env.GOOGLE_WEB_CLIENT_ID,
    defaultClientIds
  ].filter(Boolean).join(',');
  const allowedClientIds = rawAllowed.split(',').map(x => x.trim()).filter(Boolean);
  if (!allowedClientIds.length) return { id: null, reason: 'NO_ALLOWED_CLIENTS', message: 'No Google client IDs configured' };
  let response;
  try {
    response = await fetch('https://oauth2.googleapis.com/tokeninfo?id_token=' + encodeURIComponent(token), {
      signal: AbortSignal.timeout(8000)
    });
    if (!response.ok) {
      let errText = '';
      try { errText = await response.text(); } catch {}
      return { id: null, reason: 'GOOGLE_TOKEN_REJECTED', message: `Google oauth2 tokeninfo failed (HTTP ${response.status}): ${errText}` };
    }
    const info = await response.json();
    const tokenAud = String(info.aud || '').trim();
    const tokenAzp = String(info.azp || '').trim();
    if (!allowedClientIds.includes(tokenAud) && !allowedClientIds.includes(tokenAzp)) {
      return { id: null, reason: 'AUDIENCE_MISMATCH', message: `Token aud '${tokenAud}' or azp '${tokenAzp}' does not match allowed client IDs` };
    }
    if (!['accounts.google.com', 'https://accounts.google.com'].includes(info.iss)) {
      return { id: null, reason: 'INVALID_ISSUER', message: `Invalid issuer: ${info.iss}` };
    }
    if (Number(info.exp) <= Math.floor(Date.now() / 1000)) {
      return { id: null, reason: 'TOKEN_EXPIRED', message: `Token expired at ${info.exp} (current: ${Math.floor(Date.now() / 1000)})` };
    }
    if (!info.sub) {
      return { id: null, reason: 'MISSING_SUB', message: 'Token missing subject (sub)' };
    }
    if (String(info.email_verified).toLowerCase() !== 'true') {
      return { id: null, reason: 'EMAIL_NOT_VERIFIED', message: 'Google email is not verified' };
    }
    const emails = (env.ALLOWED_GOOGLE_EMAILS || '').split(',').map(x => x.trim().toLowerCase()).filter(Boolean);
    if (emails.length > 0 && !emails.includes('*') && !emails.includes(String(info.email || '').toLowerCase())) {
      return { id: null, reason: 'EMAIL_NOT_ALLOWED', message: `Email '${info.email}' is not in allowed list` };
    }
    return { id: info.sub };
  } catch (e) {
    return { id: null, reason: 'NETWORK_EXCEPTION', message: e?.message || 'Error verifying token with Google' };
  }
}

export async function handle(request, env) {
  const url = new URL(request.url);
  if (url.pathname === '/health' && request.method === 'GET') return json({ status: 'ok' });
  if (url.pathname === '/v1/auth/token' && request.method === 'POST') {
    let payload;
    try {
      payload = await request.json();
    } catch {
      return json({ error: 'Invalid JSON' }, 400);
    }
    const { code, code_verifier, redirect_uri, client_id } = payload || {};
    if (!code || !code_verifier || !redirect_uri) return json({ error: 'Missing required OAuth parameters' }, 400);
    const clientId = client_id || env.GOOGLE_DESKTOP_CLIENT_ID || (env.GOOGLE_CLIENT_IDS || '').split(',')[0].trim();
    const clientSecret = env.GOOGLE_DESKTOP_CLIENT_SECRET;
    const tokenParams = new URLSearchParams({
      client_id: clientId,
      code,
      code_verifier,
      redirect_uri,
      grant_type: 'authorization_code'
    });
    if (clientSecret) tokenParams.set('client_secret', clientSecret);
    const tokenResp = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: tokenParams.toString()
    });
    const data = await tokenResp.json();
    return json(data, tokenResp.status);
  }

  if (url.pathname !== '/v1/classify') return json({ error: 'Not found' }, 404);
  if (request.method !== 'POST') return json({ error: 'Method not allowed' }, 405);
  if (!env.GEMINI_API_KEY && !env.GROQ_API_KEY) return json({ error: 'Service not configured' }, 503);

  const principal = await authenticate(request, env);
  if (!principal || !principal.id) {
    return json({
      error: 'Unauthorized',
      reason: principal?.reason || 'AUTH_FAILED',
      message: principal?.message || 'Unauthorized'
    }, 401);
  }

  if (env.CLASSIFY_LIMITER) {
    const allowed = await env.CLASSIFY_LIMITER.limit({ key: principal.id });
    if (!allowed.success) return json({ error: 'Rate limit exceeded' }, 429);
  }

  const MAX_PAYLOAD_BYTES = 25 * 1024 * 1024;
  if (Number(request.headers.get('content-length') || 0) > MAX_PAYLOAD_BYTES) {
    return json({ error: 'Request too large' }, 413);
  }
  let payload;
  try {
    const body = await request.text();
    if (encoder.encode(body).length > MAX_PAYLOAD_BYTES) return json({ error: 'Request too large' }, 413);
    payload = JSON.parse(body);
  } catch {
    return json({ error: 'Invalid JSON' }, 400);
  }

  const hasFile = Boolean(payload && typeof payload.file_data === 'string' && payload.file_data.length > 0);
  const hasText = Boolean(payload && typeof payload.text === 'string' && payload.text.trim().length > 0);
  if (!hasFile && !hasText) {
    return json({ error: 'Document file_data or text required' }, 422);
  }
  if (!hasFile && hasText && payload.text.length > 4000000) {
    return json({ error: 'text must be 1-4000000 characters' }, 422);
  }

  // Runtime provider selection: 'gemini' (default) or 'groq' from request payload or X-Provider header
  const chosenProvider = (payload.provider || payload.preferred_provider || request.headers.get('x-provider') || '').toLowerCase().trim();
  let providers;
  if (chosenProvider === 'groq') {
    providers = [
      ...(env.GROQ_API_KEY && hasText ? [['groq', groq]] : [])
    ];
  } else if (chosenProvider === 'gemini') {
    providers = [
      ...(env.GEMINI_API_KEY ? [['gemini', gemini]] : [])
    ];
  } else {
    // Default: Gemini is always default unless explicitly set to groq
    const useOnlyGroq = env.USE_GROQ_ONLY === 'true';
    if (useOnlyGroq && env.GROQ_API_KEY && hasText) {
      providers = [['groq', groq]];
    } else {
      providers = [
        ...(env.GEMINI_API_KEY ? [['gemini', gemini]] : []),
        ...(env.GROQ_API_KEY && hasText ? [['groq', groq]] : [])
      ];
    }
  }

  let last = null;
  for (const [name, fn] of providers) {
    let outcome;
    try {
      outcome = await fn(payload, env);
    } catch {
      outcome = { error: true, status: 502 };
    }
    if (outcome.result) {
      const r = outcome.result;
      const docType = r.document_type || r.document_kind || r.category || 'Document';
      const issuedDate = r.issued_date || (r.metadata && r.metadata.issued_date) || null;
      const proposed = generateCompliantFilename(r.category, docType, issuedDate, payload.filename);
      const docKind = typeof r.document_kind === 'string' ? r.document_kind : (r.document_type || r.category);
      return json({
        document_kind: docKind,
        document_type: r.document_type || docKind,
        category: r.category,
        issued_date: issuedDate,
        specifics: r.specifics,
        suggested_name: proposed,
        proposed_name: proposed,
        metadata: (r.metadata && typeof r.metadata === 'object') ? r.metadata : {},
        provider: name === 'gemini' ? (outcome.model || env.GEMINI_MODEL || 'gemini-2.0-flash') : name,
        provider_name: name === 'gemini' ? 'Google Gemini' : 'Groq',
        model: outcome.model || (name === 'gemini' ? (env.GEMINI_MODEL || 'gemini-2.0-flash') : (env.GROQ_MODEL || 'llama-3.1-8b-instant')),
        prompt: {
          system_instruction: classifyPrompt(),
          prompt_question: USER_PROMPT_QUESTION,
          user_question: USER_PROMPT_QUESTION,
          mode: hasFile ? 'multimodal_file' : 'text_content',
          mime_type: hasFile ? (payload.mime_type || 'application/pdf') : null
        },
        raw_response: r
      });
    }
    last = outcome;
    console.error('Inference provider error', { provider: name, status: outcome.status });
  }

  const status = last?.status === 429 ? 429 : 502;
  const headers = last?.retry ? { 'retry-after': last.retry } : {};
  const message =
    status === 429 ? 'All services have surpassed their free quota. Please try after some time.' : 'Inference provider unavailable';
  return json(
    {
      error: status === 429 ? 'AI quota or rate limit reached' : 'Inference provider unavailable',
      message: (last?.groq_message || last?.gemini_message || message),
      primary_provider: chosenProvider || 'gemini',
      provider: last?.provider || (chosenProvider === 'groq' ? 'groq' : 'gemini'),
      provider_status: last?.status || 502,
      retry_after: last?.retry || null,
      gemini_model: last?.gemini_model || (chosenProvider === 'gemini' ? (env.GEMINI_MODEL || 'gemini-3.5-flash-lite') : null),
      gemini_code: last?.gemini_code || null,
      gemini_status: last?.gemini_status || null,
      gemini_message: last?.gemini_message || null,
      groq_model: last?.groq_model || (chosenProvider === 'groq' ? (env.GROQ_MODEL || 'openai/gpt-oss-20b') : null),
      groq_status: last?.groq_status || null,
      groq_message: last?.groq_message || null,
      prompt: {
        system_instruction: classifyPrompt(),
        prompt_question: USER_PROMPT_QUESTION,
        user_question: USER_PROMPT_QUESTION,
        mode: hasFile ? 'multimodal_file' : 'text_content'
      },
      raw_response: last || null
    },
    status,
    headers
  );
}

export default { fetch: handle };
