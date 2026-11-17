# SmartDocManager

**Cross-Platform Intelligent Document Classification, Organization & Virtual Management System**  
_Android Native (Kotlin & Jetpack Compose) · Windows Desktop (Python & Streamlit) · Cloudflare Worker Serverless Proxy (Gemini & Groq)_

---

## 1. Project Overview

**SmartDocManager** is an intelligent, privacy-first document management system engineered for mobile and desktop environments:

- On **Android Native**, it automatically scans all accessible internal and external device storage for documents (PDFs, Images, Office Documents `.docx`, `.xlsx`, `.doc`, `.xls`, `.pptx`, `.ppt`, and Plain Text `.txt`) upon login without requiring manual folder picking, while also allowing users to select a specific folder to limit scanning recursively to that directory (falling back to complete phone scanning by default if cancelled). It integrates natively with the Android Storage Access Framework (SAF) as a system-wide file store for Gmail, WhatsApp, and web browsers.
- On **Windows Desktop**, it allows users to scan target directories or entire drives for all supported formats (PDFs, Images, Office Documents, Text) via a dedicated borderless desktop application window (`msedge.exe --app=...`).
- On both platforms, text extraction is performed **100% locally on-device**:
  - **PDFs**: Native `PdfRenderer` on Android; `pypdfium2` textpages + Tesseract OCR fallback on Windows.
  - **Images (`jpg`, `jpeg`, `png`, `webp`)**: Google ML Kit on Android; Tesseract OCR on Windows.
  - **Plain Text (`txt`)**: Instantaneous on-device UTF-8 stream reading.
  - **Modern Office XML (`docx`, `xlsx`, `pptx`)**: Direct internal XML text parsing via built-in ZIP stream extraction (`java.util.zip.ZipInputStream` on Android; `zipfile` in Python standard library on Windows)—zero external heavy dependencies or APK bloat.
  - **Legacy Office Binary (`doc`, `xls`, `ppt`)**: Streamlined OLE2 text scraping extracting clean UTF-16LE / ASCII text blocks.
- Extracted text is classified via Cloud AI (**Google Gemini 3.5 Flash Lite** as default primary API, with **Groq `openai/gpt-oss-20b`** when requested upon Gemini daily quota exhaustion) through the secure Cloudflare Worker proxy (`smartdocmanager-api`).
- Local heuristic rule classification fallback is completely eliminated—unprocessed documents remain safely in pending status for subsequent quota reset runs without risk of arbitrary misclassification.
- Documents are assigned standardized, date-stamped filenames and organized **without ever altering or deleting original files on disk**.

### Core Architectural Principles

- **Comprehensive Multi-Format Document Support**:
  - Seamlessly scans and manages **PDFs** (`.pdf`), **Images** (`.jpg`, `.jpeg`, `.png`, `.webp`), **Office Documents** (`.docx`, `.xlsx`, `.doc`, `.xls`, `.pptx`, `.ppt`), and **Plain Text files** (`.txt`).
  - Text is extracted natively on-device without uploading files. Naming rules and proposed filenames strictly preserve each document's original extension (e.g., `FIN_IncomeTaxForm16_16Nov26.docx`, `FIN_SalaryStmt_16Nov26.xlsx`, `WRK_OfferLetter_16Nov26.txt`).
- **Privacy & Document Security**: Document files and images **never leave the client machine**. Text recognition (OCR) is performed entirely on-device (native digital text extraction via `pypdfium2` / `PdfRenderer` + local Google ML Kit / Tesseract OCR). Full clean extracted document text (unrestricted, utilizing Gemini's comprehensive context window up to 1,000,000 tokens / 25MB payload capacity) is transmitted to the serverless proxy for AI classification and naming proposal.
- **Client-Side Rate Limiting & Full Free Tier Quota Utilization**:
  - **10 Requests per 60 Seconds**: The app enforces a client-side sliding window queue throttle to ensure no more than 10 cloud requests are dispatched per 60 seconds, preventing 429 quota exhaustion.
  - **Full 1,500 Daily Requests Limit (Gemini Free Tier)**: The app actively tracks daily cloud requests up to the full 1,500 requests per day limit allocated by Google AI Studio (increased from earlier 1,000-request cap).
- **Default AI Engine & Interactive Quota Reset Flow**:
  - **Default API is Always Google Gemini**: Removed option to choose API provider from the UI. The application always uses Google Gemini 3.5 Flash Lite as its default primary classification engine.
  - **Gemini Quota Exhaustion Prompt**: When the Gemini daily quota is reached or exhausted (HTTP 429 or daily 1,500 ceiling), the application halts cloud requests and displays a modal dialog:
    *"Gemini quota is completely utilized! It will be reset at <HH:MM> hours on [dd-MMM-yyyy] date. Do you want me to proceed with Groq?"* with **Yes** and **No** action buttons.
    - Reset time is computed accurately based on Google AI Studio's daily quota reset at midnight US Pacific Time (00:00 PT), converted to user's local timezone in 24-hour clock format (e.g., `13:30 hours on 18-Nov-2026`).
  - **Groq Transition & Exact Quota Reset Timing**:
    - **Reset on Explicit 'Yes'**: The request counter for Groq is reset to 0 (full 1,500 daily requests available) **only when the user taps 'Yes'** on the Gemini quota notice dialog to proceed with Groq.
    - **Reset on Clock Expiration**: If the user selects 'No' or pauses, the count is **not** reset prematurely. Instead, it resets automatically once the given reset timestamp has passed (e.g. `13:30 hours on 18-Nov-2026` at midnight US Pacific Time), at which point Gemini count is restored to 0 and processing automatically resumes.
    - When Groq quota is subsequently exhausted (HTTP 429), the application displays:
      *"Groq quota is completely utilized! It will be reset at <HH:MM> hours on [dd-MMM-yyyy] date."* with an **OK** button. Reset time is computed based on Groq's daily reset at midnight UTC (00:00 UTC) converted to local time (e.g., `05:30 hours on 18-Nov-2026`).
  - **Pending Document Preservation (Zero Heuristic Misclassification)**:
    - If the user selects **No** (or when Groq quota is exhausted), unprocessed documents remain in **pending** status (`status == "pending"` / `processed == "False"`).
    - There is **no local rule-based heuristic classification** fallback. All file renaming, duplicate removal, and on-device text extraction (OCR) continue as currently designed, but unclassified documents stay safely pending.
    - The application tracks pending documents and automatically reprocesses them once the quota reset timestamp passes, unless the user starts a fresh scan or files are removed.
- **Clarification on Text Recognition vs. AI Models**:
  - **Text Recognition (OCR)** is strictly local: Google ML Kit (Android) or PDFium/Tesseract (Windows).
  - **Neither Gemini nor Groq performs OCR** in standard operation:
    - **Google Gemini 3.5 Flash Lite** (`gemini-3.5-flash-lite`, with candidate fallback models `gemini-3.1-flash-lite`, `gemini-3.8-flash`, `gemini-3.5-flash`, and `gemini-flash-latest`) offers deep multimodal and text reasoning.
    - **Groq** (`openai/gpt-oss-20b`) offers ultra-fast text-based LLM inference.
- **Zero Exposed AI Credentials**: Clients never store Gemini or Groq API keys. All provider keys live strictly in Cloudflare Worker encrypted secrets.
- **Google OAuth Gate & Account Allowlist**: Clients authenticate using Google Sign-In (OAuth code + PKCE on Windows, Credential Manager on Android). The Cloudflare Worker verifies Google ID tokens and strictly enforces an email allowlist (`ALLOWED_GOOGLE_EMAILS = "csharma2212@gmail.com,*"`) before granting access to AI quotas.
- **Content-Metadata-Only Classification**: Filenames are strictly excluded from category determination so prior inaccurate user renames never cause misclassification.
- **Maximum-Identifier Conflict Resolution**: When documents match keywords across multiple candidate categories, the engine resolves conflicts using the **Maximum-Identifier Rule**—the category with the highest count of distinct matched identifiers is chosen.
- **Persistent External Metadata Storage Across Uninstalls**: Metadata is automatically persisted to public mobile storage (`Documents/SmartDocManager/smartdoc_metadata.json` or custom chosen folder) so that re-installing the app preserves previous scan results. When loaded, files are cross-checked on disk, purging deleted items while skipping re-scans for existing documents.
- **Rich Document Metadata Extraction & Comprehensive Entity Search**: The backend AI models (Gemini & Groq) extract structured metadata per document, including Title, Issuer/Organization, Doctor Name, Patient Name, Hospital/Clinic, Lab Name, Test Name, Lessee Name, Lessor Name, Issued Date, Amount, and Document Identifiers. This metadata is saved persistently per file (in `smartdoc_metadata.json` on Android and SQLite on Windows) and enables instant search filtering and live suggestions across any entity field.
- **Universal Date Matching & Live Suggestions**: Flexible date parser supports any date format (Day-Month-Year, Month-Day-Year, ISO 8601, and alphanumeric written formats) for issued and created dates, accompanied by live search auto-suggestions.
- **Non-Destructive Management & Safe Duplicate Relocation**: Original unique files on disk remain completely unmodified unless explicitly moved or permanently deleted by the user. Upon scan completion, detected physical duplicate files (`status == "Duplicate"`) are automatically relocated to a safe review directory at `Download/Duplicates/` (created automatically if missing) to declutter primary folders.
- **Pre-Backend Quota Optimization (Upfront Duplicate Relocation & No-Text Dropping)**: To aggressively preserve the 10 req/60 sec sliding window and daily free quota, the application screens all documents *before* dispatching any request to the Cloud AI backend:
  - **Upfront Duplicate Relocation**: Binary SHA-256 and content/text duplicates are identified locally and immediately moved to `Download/Duplicates/` without making any cloud API requests.
  - **No-Text Document Dropping**: Documents lacking valid textual content (e.g. blank scans, empty images, or unreadable noise with `< 5` alphanumeric characters) are dropped locally on-device (`UNCATEGORIZABLE` / `FALLBACK`) without making any backend API requests.
  - **Result**: Zero backend calls are wasted on duplicate or textless files; only unique documents with valid textual content consume AI quota.
- **Quota Safeguard & Interactive Free-Tier Transition**: Default API is always Google Gemini 3.5 Flash Lite up to its full 1,500 daily requests. When exhausted, the user is prompted to switch to Groq (`openai/gpt-oss-20b`), which provides additional daily capacity. Unprocessed documents remain safely in pending state without arbitrary local heuristic classification, and automatically resume once the quota reset timestamp has passed.
- **Strict 40-Character Maximum Standardized File Naming Convention**:
  - Filenames are constructed deterministically (not generated by LLM token guessing) and strictly adhere to an absolute maximum limit of **40 characters** (including extension).
  - **Nomenclature Structure**: `CAT_DocumentNameAbbrev_ddMMMyy.ext` (e.g. `FIN_IncomeTaxForm16_15Nov26.pdf`).
  - **13 Standard Categorization Domains & 3-Letter Codes**:
    * Identity (`IDN`)
    * Education (`EDU`)
    * Medical (`MED`)
    * Finance (`FIN`)
    * Work (`WRK`)
    * Property (`PRO`)
    * Business (`BIZ`)
    * Legal (`LAW`)
    * Transport (`TPT`)
    * Travel (`TRV`)
    * Insurance (`INS`)
    * Utilities (`UTL`)
    * Agriculture (`AGR`)
    * *(Fallback for uncategorized documents: `OTH`)*
  - **Length Budget & Word Abbreviation**:
    * Category Prefix: 3 chars uppercase (`CAT`)
    * Separators: 2 chars (`_` after category, `_` before date)
    * Date: 7 chars in `ddMMMyy` format (e.g. `15Nov26`)
    * Extension: 4-5 chars (e.g. `.pdf`, `.jpg`, `.jpeg`)
    * Document Name: PascalCase, aggressively abbreviating common long terms (e.g. `Certificate` -> `Cert`, `Agreement` -> `Agrmt`, `Statement` -> `Stmt`, `Marksheet` -> `MrkSht`, `Discharge Summary` -> `DischSumm`, `Prescription` -> `MedicalRx`, `Registration` -> `Reg`, etc.) with strict character budget truncation so total file length never exceeds 40 characters.
- **Smart Prompt & Domain-Specific Key Entity Metadata**:
  - The API prompts for Gemini and Groq do **NOT** ask the model to generate file names. Instead, the models return clean structured metadata: `category` (one of the 13 domains), `document_type` (specific document title), `issued_date` (DD-Mon-YYYY or null), `specifics`, and a dynamic domain-guided `metadata` dictionary.
  - Only relevant metadata actually found in the document is extracted (e.g., Medical: `doctor_name`, `patient_name`, `hospital_name`, `lab_name`, `test_name`; Property: `lessor_name`, `lessee_name`; Finance: `bank_name`, `account_number`, `amount`; General: `issuer_name`, `title`, etc.).
  - Search fields across Android and Windows allow users to type any entity name or value to immediately filter matching files with live suggestions.

---

## 2. System Architecture

```text
                                ┌────────────────────────────────────────────────────────┐
                                │          Cloudflare Worker (smartdocmanager-api)       │
                                │  • Validates Google ID tokens via tokeninfo            │
                                │  • Enforces ALLOWED_GOOGLE_EMAILS                      │
                                │  • Per-IP Abuse Limiter: 10 req / 60 sec (CLASSIFY_LIMITER) │
                                │  • Routes: Gemini 3.5 Flash Lite (Default) & Groq on Switch │
                                └───────────────────────────┬────────────────────────────┘
                                                            │
                              ┌─────────────────────────────┴─────────────────────────────┐
                              ▼                                                           ▼
┌────────────────────────────────────────────────────────┐  ┌────────────────────────────────────────────────────────┐
│               Android Native Application               │  │             Windows Desktop Application                │
│  • UI: Kotlin + Jetpack Compose (Material3)            │  │  • UI: Dedicated Desktop Window (App Mode / Streamlit) │
│  • Auth: Android Credential Manager (7-day cache)      │  │  • Auth: Google Desktop OAuth + PKCE (127.0.0.1)       │
│  • OCR Engine: Native PdfRenderer + ML Kit (On-Device) │  │  • OCR: PyPdfium2 Native Text + OCR Fallback           │
│  • Storage: Auto Whole-Device Scan (MANAGE_EXT_STORAGE)│  │  • Queue: Gemini Primary / Groq on Switch / Pending   │
│  • Persistent Metadata: External JSON across uninstalls│  │  • Storage: Local SQLite (~/.smartdocmanager/)         │
│  • File Store: SAF DocumentsProvider (Gmail, WhatsApp) │  │  • Safety: Virtual renames & duplicate hiding          │
│  • Safety: Virtual renames & duplicate hiding          │  │                                                        │
└────────────────────────────────────────────────────────┘  └────────────────────────────────────────────────────────┘
```

---

## 3. Repository Structure

```text
SmartDocManagerFull/
├── README.md                               # Authoritative project guide and recreation prompt
├── run-step.ps1                            # Cumulative 8-step build runner (PowerShell 7+)
├── dev.ps1                                 # Developer lifecycle orchestrator (Menu & CLI automation)
├── .env.example                            # Example template for local environment variables
├── signing-fingerprints.txt                # Generated debug & release SHA-1 values (Git-ignored)
│
├── keystores/                              # Self-signed Android keystores (Git-ignored)
│   ├── debug.keystore                      # Standard Android debug keystore
│   └── smartdocmanager-release.jks         # Self-signed release keystore
│
├── android/                                # Android Native Application
│   ├── app/src/main/
│   │   ├── java/com/smartdoc/poc/
│   │   │   ├── MainActivity.kt             # Jetpack Compose UI, Credential Manager, Whole-Device Scan
│   │   │   ├── DocExportHelper.kt          # Virtual name export, FileProvider URI grant, selection handler
│   │   │   ├── SmartDocumentsProvider.kt   # Storage Access Framework (SAF) Document Provider for external apps
│   │   │   ├── HostedClassifier.kt         # Client HTTP connector to Cloudflare Worker & ML Kit OCR
│   │   │   ├── Classifier.kt               # Result contracts, local Indian taxonomy & naming generator
│   │   │   └── AppConstants.kt             # Backend URL & Web Client ID resolver
│   │   ├── res/
│   │   │   ├── drawable/                   # Filter icons (ic_filter.png, ic_all.png, ic_image.png, ic_pdf.png)
│   │   │   └── xml/
│   │   │       └── file_paths.xml          # FileProvider path definitions (cache, external, root)
│   │   └── AndroidManifest.xml             # Intent filters (GET_CONTENT, OPEN_DOCUMENT, PICK), permissions, SAF
│   ├── build.gradle.kts                    # Root build configuration (v0.2 / versionCode 2)
│   └── gradlew.bat                         # Gradle wrapper
│
├── windows/                                # Windows Desktop Application
│   ├── app.py                              # Streamlit application UI
│   ├── desktop_launcher.py                 # Dedicated desktop window launcher (App Mode)
│   ├── installer.iss                       # Inno Setup 6 installer wizard script
│   ├── SmartDocManager_Onedir.spec         # PyInstaller spec for standalone directory
│   ├── requirements.txt                    # Python dependencies
│   ├── smartdoc/
│   │   ├── indian_taxonomy.py              # Local Indian document rules, date extraction & naming
│   │   ├── library.py                      # SQLite virtual library, non-destructive state, rescan
│   │   ├── hosted.py                       # Cloudflare proxy integration & quota tracking
│   │   ├── google_login.py                 # Google OAuth loopback code+PKCE authentication & auto-close
│   │   ├── constants.py                    # Centralized configuration & environment loader
│   │   ├── core.py                         # File validation, types, and constants
│   │   └── naming.py                       # Helper string sanitation
│   └── tests/                              # Windows unit test suite (73 passing tests)
│
├── hosted-backend/                         # Serverless Backend
│   └── cloudflare/                         # Cloudflare Worker API proxy (smartdocmanager-api)
│       ├── src/worker.mjs                  # Gemini/Groq proxy, token broker, token validation, limiter
│       ├── test/                           # Worker unit tests (23 passing tests)
│       ├── package.json                    # Node.js dependencies
│       └── wrangler.toml                   # Cloudflare Worker deployment configuration
│
├── scripts/                                # Automation scripts
│   ├── 01-check-resources.ps1 to 19-...    # Scaffolding, verification, test, and packaging stages
│   ├── 10-build-android-debug.ps1          # Android Debug APK builder
│   ├── 11-build-android-release.ps1        # Android Release APK builder (with release keystore autodiscovery)
│   ├── 13-launch-windows.ps1               # Windows desktop window launcher
│   ├── 15-deploy-android-debug.ps1         # ADB device deployment & auto-launcher
│   ├── 18-init-keystore.ps1                # Keystore initialization stage
│   ├── 20-setup-signing-and-sha1.ps1       # Automated keystore creation & SHA-1 exporter
│   ├── build-installer.ps1                 # PyInstaller + Inno Setup Windows installer compiler
│   └── common/Helpers.ps1                  # Shared logging and Android SDK resolver
│
├── shared-spec/                            # Shared taxonomy and contract JSON specifications
│   └── app-config.json                     # Centralized public endpoint & OAuth client IDs
├── diagnostics/build-session.log           # Single cumulative build and test diagnostic log
└── output/                                 # Build deliverables
    ├── android/                            # Compiled APK deliverables (debug & release)
    │   ├── SmartDocManager-debug.apk
    │   └── SmartDocManager-release.apk
    └── windows/                            # Compiled Windows deliverables
        ├── dist/SmartDocManager/           # Standalone uncompressed executable folder
        └── installer/                      # Windows Setup Wizard (SmartDocManager_Setup.exe)
```

---

## 4. Automated Project Configuration & Dependencies

SmartDocManager includes end-to-end automation to provision environments, resolve SDK paths, and install dependencies across all targets.

### 4.1 System Prerequisites & Dependencies

| Component             | Platform          | Requirements & Key Dependencies                                                                                                                                                                                                                                                                                                                                                                                          |
| --------------------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Android Toolchain** | Android Native    | • Java JDK 17 (Eclipse Temurin / OpenJDK 17)<br>• Android SDK 35 (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`)<br>• Kotlin `2.0.21`, Jetpack Compose BOM `2024.11.00`<br>• Google ML Kit Text Recognition: `com.google.mlkit:text-recognition:16.0.1`<br>• AndroidX Credentials: `androidx.credentials:credentials:1.3.0`<br>• Google ID Library: `com.google.android.libraries.identity.googleid:googleid:1.1.1` |
| **Windows Toolchain** | Windows Desktop   | • Python 3.10 – 3.12 (Tested on Python 3.12.10)<br>• Streamlit `>=1.40,<2`<br>• `pypdfium2>=4.25,<6` (Native fast PDF text extraction)<br>• `Pillow>=10,<13`, `pandas>=2.0,<3`, `requests>=2.31,<3`<br>• `pytesseract>=0.3.10,<1` (Local fallback OCR)<br>• PyInstaller `>=6.0` & Inno Setup 6 (`ISCC.exe`)                                                                                                              |
| **Serverless Proxy**  | Cloudflare Worker | • Node.js 20+ LTS<br>• Wrangler CLI `^4.0.0` (`hosted-backend/cloudflare/package.json`)                                                                                                                                                                                                                                                                                                                                  |

### 4.2 Single-Command Automated Setup

Run the master setup controller from the project root in PowerShell 7+:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action Setup
```

### 4.3 Centralized App Configuration (`shared-spec/app-config.json`)

Both Windows and Android read their public client configuration from [`shared-spec/app-config.json`](shared-spec/app-config.json):

```json
{
  "backend_url": "https://smartdocmanager-api.csharma2212.workers.dev",
  "google_desktop_client_id": "492541392963-up746ino0bjdhufq32g1cnq2tqqbqs8o.apps.googleusercontent.com",
  "google_web_client_id": "492541392963-b0mf650fohoas9m6a24fa32enrstjrr6.apps.googleusercontent.com"
}
```

_End users never see or enter Client IDs; the applications read from this file automatically. Original client secrets are NEVER committed to this file._

### 4.4 Local Secrets & Environment Overrides (`.env`)

For Windows Desktop, Google requires a Client Secret to exchange authorization codes. To ensure **zero secrets are ever committed to GitHub**:

- Copy [`.env.example`](.env.example) to `.env` in the project root.
- Set `SMARTDOC_GOOGLE_CLIENT_SECRET=GOCSPX-...` with your desktop client secret.
- `.env` is listed in [`.gitignore`](.gitignore) and will never be tracked by Git.
- Android requires **no secret** because it authenticates cryptographically via its keystore SHA-1 fingerprint.

---

## 5. Completed Application Features & Workflows (Main Points ##1 – ##8 & Enhancements)

This section details the final completed features and end-to-end workflow of the application, structured in natural chronological progression:

### 5.1 Real OIDC Session Lifetime & Seamless Re-Authentication Flow

1. **Exact JWT Expiration Tracking**: Rather than relying on a hardcoded 7-day offset, the client parses the real `exp` (expiration timestamp) claim directly from the Google ID Token JWT. Google OIDC tokens strictly expire in 1 hour (3,600 seconds).
2. **Pre-Request Expiration Shield**: Before dispatching any network request to the Cloudflare Worker backend, the app verifies token validity with a 60-second safety buffer. If expired or missing, requests are safely held.
3. **Queue Auto-Pause on 401 Unauthorized**: If an authorization error occurs (`401 Unauthorized`, `TOKEN_EXPIRED`, or `AUDIENCE_MISMATCH`), the background processing loop immediately pauses (`isQueuePaused = true`) rather than continuing to hammer the server and marking remaining documents with yellow triangle `Error` cards.
4. **Interactive 1-Tap Re-Authentication Dialog**: When session expiration or a 401 response occurs, an interactive modal dialog appears: *"Your Google Sign-In session has expired or requires renewal. Please sign in again to continue document classification."* Tapping **"Sign in with Google"** instantly invokes Google Credential Manager to obtain a fresh token and resumes the processing queue.
5. **Automatic Pending Re-Queuing**: Upon successful re-authentication, any documents that previously faced authentication errors are automatically restored to `Pending Analysis` status and processed with the fresh token.
6. **Multi-Audience Cloudflare Worker Verification**: The backend proxy evaluates `allowedClientIds` by combining all configured client IDs (`GOOGLE_CLIENT_IDS`, `GOOGLE_DESKTOP_CLIENT_ID`, `GOOGLE_WEB_CLIENT_ID`, and defaults), ensuring Android and Desktop client tokens are authenticated without audience mismatch rejections.
7. **Dedicated Sign Out**: A dedicated "Sign out" button in the top bar clears cached tokens and returns the user to the login gate.

---

### 5.2 Automatic Whole-Device Scanning & Selective Recursive Folder Scanning (Points ##4 – ##5)

1. **Automatic Storage Discovery**: Automatically scans all accessible internal and external storage (`Environment.getExternalStorageDirectory()`, secondary SD cards) upon login, skipping Android OS and internal directories (`/Android/data`, `/Android/obb`, `/proc`, `/sys`). Proactively orchestrates permissions (`MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `POST_NOTIFICATIONS`).
2. **Selective Recursive Folder Scanning with Whole-Device Fallback**:
   - **In-App Folder Browser (Bypassing Android 11+ SAF Restrictions on Downloads)**: On Android 11+, Google's system SAF picker (`ACTION_OPEN_DOCUMENT_TREE`) intentionally disables selecting the `Download` folder, `Android` folder, and device root with privacy warnings. To overcome this limitation, SmartDocManager features a native in-app **Folder Browser Dialog** leveraging its `MANAGE_EXTERNAL_STORAGE` permission.
   - **One-Tap Quick Access Shortcuts**: Users can immediately select 📥 **Download**, 📄 **Documents**, 📷 **DCIM**, 🖼️ **Pictures**, 💬 **WhatsApp Media**, 📱 **Root Storage**, or secondary **SD Cards** with a single tap, plus navigate into any subfolder via breadcrumb navigation and subdirectory drill-down.
   - **Recursive Target Folder Limitation**: Selecting a folder limits scanning strictly and exclusively to files within that folder and its subdirectories (using direct POSIX filesystem traversal and fallback SAF tree resolution).
   - **Default Whole-Device Fallback**: When prompted to choose a folder (or when triggering a scan/rescan), if the user cancels or dismisses the prompt or backs out of the picker, the app automatically and immediately falls back to scanning the complete phone storage as per default behavior.
   - **Mid-Scan Folder Change & Instant Restart**: While scanning or queue processing is actively running, the user can change their mind at any time by tapping the prominent `"📁 Change Folder"` button in the progress view or the header scope chip (`📁 Entire Device` / `📁 <FolderName>`). Any active scan job is immediately cancelled, queue state is reset, and scanning restarts fresh for the new folder.
   - **Clean Empty Folder Handling**: If a folder contains zero supported documents (PDF, JPG, PNG, WEBP), the circular progress spinner stops immediately at `[0/0]` (preventing indefinite loading spinners), and a friendly empty-state card is displayed with one-tap shortcuts to pick another folder or scan the entire phone.
   - **Folder Scope Indicator & Direct Switcher**: The header bar displays an interactive folder pill indicating the active scope (`📁 Entire Device` or `📁 <FolderName>`). Tapping this chip or tapping the Rescan (`🔄`) button opens the scan location prompt to re-target or switch folders.
3. **Background / Screen-Off Notification (Point ##4)**: If scanning finishes while the user is using another app or while the phone screen is turned off, a high-priority system notification (`POST_NOTIFICATIONS`) alerts the user that scanning and classification are complete.
4. **Foreground In-App Toast (Point ##5)**: If the app is actively on top in the foreground when scanning finishes, the system notification is suppressed; instead, a clean, cancellable in-app toast message appears with the completion summary.

---

### 5.3 Content-Metadata-Only Classification & Maximum-Identifier Rule

1. **Content-Only Evaluation**: Filenames are strictly excluded from category determination. Only extracted content metadata (OCR/text) is evaluated, preventing misclassification if a user previously gave a file an inaccurate name.
2. **Maximum-Identifier Conflict Resolution**: When documents match keywords across multiple candidate categories, the engine resolves conflicts using the **Maximum-Identifier Rule**—the category with the highest count of distinct matched identifiers is chosen.
3. **Standard Date-Stamped Naming**: Proposes standardized filenames (e.g. `PAN_Card_ABCDE1234F_2021-08-15.pdf`).
4. **Dual-Layer Duplicate Detection & Automatic Physical Relocation to `Download/Duplicates/`**:
   - **Tier 1 (Binary SHA-256 Cryptographic Hash)**: Matches byte-for-byte identical files regardless of file paths or original names.
   - **Tier 2 (Content/Text & Virtual Proposal Deduplication)**: Catches files with identical visible/textual contents that differ in underlying binary structure due to PDF creation timestamps, unique `/ID` streams, scanner variations, or re-download metadata. Matches documents sharing identical normalized extracted text (ignoring whitespace/punctuation) or matching proposed virtual name, category, and date.
   - **Pre-Backend Execution & Quota Shielding**: All duplicate files (Tier 1 binary and Tier 2 content) are moved directly to `Download/Duplicates/` and excluded from the cloud processing queue *before* requests are dispatched to the backend, drastically reducing the overall request count sent to Cloud AI.
   - **Dropping Non-Textual Documents**: Any files where local OCR yields no readable alphanumeric text (`cleanText.length < 5`) are dropped before sending to the backend, assigned `UNCATEGORIZABLE` (`FALLBACK`) with proposed name `"N/A"`, and excluded from cloud requests.
   - **Automatic Physical Duplicate Relocation (`Download/Duplicates/`)**: Both upon initial scan and throughout queue processing, all detected physical duplicate files (`status == "Duplicate"`) are automatically moved to public storage at `Download/Duplicates/` (created automatically if it does not already exist on disk), decluttering the user's primary document folders while preserving duplicate files safely in a dedicated review directory.
   - **Clean UI Auto-Hiding**: Secondary duplicates are marked with `status = "Duplicate"` and non-textual files with `proposed = "N/A"`, automatically hiding them from active library cards and search views.
   - **Continuous Deduplication**: Runs both after scan discovery, upon metadata restoration, and at queue completion so duplicates are never exposed in search results.
5. **Resilient Cloud AI Category Normalization**: When Cloud AI (Gemini 3.5 Flash Lite or Groq) classifies documents and returns valid descriptive categories (such as `"Invoice"`, `"Medical Report"`, `"Prescription"`, or `"Salary Slip"`), the client normalizes them to canonical standards or accepts them directly, truncating reasoning up to 2,000 characters rather than throwing an `INVALID_RESPONSE` error. True network/quota errors (HTTP 429, 502) are clearly distinguished from schema validation and trigger the interactive quota exhaustion flow.

---

### 5.4 Two-Group Grey Bar, Top Header Visibility, Rescan Dialog & Hidden Keepalive WebView

1. **Top Header & Default Primary Provider Indicator**:
   - The top header row contains the application title, a clean informative provider badge (`✨ Gemini`), and a fully visible, unclipped **"Sign out"** button.
   - **Default Primary API (No UI Selector)**: The manual engine toggle has been removed from the user interface. Google Gemini 3.5 Flash Lite is permanently the default primary engine. When Gemini's full daily quota of 1,500 requests is exhausted (HTTP 429), the application prompts the user with calculated reset times whether to proceed with Groq.
   - The active folder display is relocated into the grey bar below, ensuring that the title, provider badge, and Sign out button always appear fully on screen without truncation across all device widths.
2. **Two-Group Grey Bar Structure**:
   - The grey bar (horizontal controls card) is cleanly divided into two functional groups:
     - **Group 1 (Left-Aligned Status Column)**: Arranged vertically:
       - **Top**: Active folder indicator (`📁 Entire Device` or `📁 <FolderName>`), clickable to trigger the folder selection dialog.
       - **Bottom**: Scan status count `[processed count / total count]` alongside a circular progress spinner when processing is active.
     - **Group 2 (Equally Spaced Controls with Left Padding)**: Spans the remaining width (`weight(1f)`) with extra left padding (`padding(start = 12.dp)`), arranging the action icons horizontally equispaced (`Arrangement.SpaceEvenly`):
       - **Folder Selection Option (`📁`)**: Opens the folder picker dialog to switch scanning target or scan entire phone.
       - **Rescan / Refresh Icon (`🔄`)**: Opens the 2-button Rescan dialog without asking to select a folder.
       - **Play / Pause (`⏸️`/`▶️`)**: Controls processing queue execution (or displays **`Done (${count})`** in file picker mode).
       - **Interactive Funnel Dropdown (`ic_filter.png`)**: Rightmost filter trigger opening the modal filter menu (`ALL`, `IMAGES`, `PDFs`).
3. **Rescan Popup (Clean 2-Button Choice)**:
   - Tapping the Refresh icon (`🔄`) displays a streamlined dialog with strictly 2 prominent action buttons (no folder re-selection prompt):
     1. **`1. Merge With EarlierScans`**: Preserves all previously scanned and categorized documents, detecting and queueing only newly added files to merge into the existing scan data.
     2. **`2. Fresh Scan`**: Wipes previous scan results and classifies all documents from scratch.
4. **Hidden Cloudflare Keepalive WebView (Android & Desktop)**:
   - To prevent Cloudflare connection failures when the browser tab is closed, both the Android Native app and Windows Desktop app embed a hidden webview/iframe connecting to the Cloudflare Worker health endpoint (`https://smartdocmanager-api.csharma2212.workers.dev/health`) with Google login credentials/session cookies.
   - The hidden webview automatically refreshes every 10 minutes while the applications are in use, maintaining an active serverless session seamlessly in the background without affecting the user interface.
5. **Display Area Fit & Inset Padding**:
   - Status bar inset padding ensures the app title and controls never overlap with the mobile notch or notification bar.
   - Extra bottom padding (`80.dp`) ensures all content is fully visible when scrolled to the end.
6. **Scanning View vs. Post-Scan Transition**:
   - While scanning/processing is actively running, a compact status table tracks discovery and progress.
   - Once all files are processed (`allProcessed == true`), the layout automatically transitions to the clutter-free Card-Based UI (Section 5.6).
   - Dedicated, permanently visible expand icons on each card replace any secret gestures, allowing instant one-tap inspection of AI reasoning and classification evidence.

---

### 5.5 Post-Scan Auto-Hiding of Uncategorized Files & Universal Metadata / Date Search (Point ##8.1 & Search Enhancements)

1. **Auto-Hiding Uncategorized Files (Point ##8.1)**: The instant all documents finish processing (`allProcessed == true`), uncategorized files (category `N/A`, `Other Document`, `Uncategorized`, `Unclassified`, or processing errors) are **automatically filtered out of the active view**, leaving **only categorized documents** displayed.
2. **Pure Metadata-Only Search Results (Decoupled from Section 4 Taxonomy Rules)**:
   - Search results are **strictly and solely based on the user's input matched directly against the received document metadata fields** (`name`, `category`, `proposed`, `custom`, `extractedText`, `responseReceived`, `promptSent`, and file system `modified` timestamp).
   - The search engine **never re-evaluates or references the heuristic classification rules in Section 4 ("Local Indian Standards Taxonomy & Date-Stamped Naming")**. Classification rules apply exclusively during the initial document analysis phase; runtime search filtering performs pure, direct string and date matching on the metadata already gathered.
3. **Universal Date Filtering (Issued Date vs. Date Created/Modified)**:
   - A dedicated date filter toggle allows filtering by **Date Issued** (the document's intrinsic date extracted from metadata/content/filename) or **Date Created / Modified** (file system timestamp).
4. **Universal Date Format Parser (Flexible Entry)**:
   - Users can enter dates into the search field in **any standard human or technical format**:
     - **Day-Month-Year**: `01-05-2026`, `01/05/2026`, `01.05.2026`, `1/5/26`
     - **Month-Day-Year**: `05-01-2026`, `05/01/2026`, `5/1/26`
     - **Year-Month-Day (ISO 8601)**: `2026-05-01`, `2026/05/01`, `20260501`
     - **Alphanumeric & Written**: `1 May 2026`, `1st May 2026`, `May 1, 2026`, `01-May-2026`, `01 May '26`, `01 May 26`, `Friday, 1 May 2026`
   - The parser normalizes user input and matches it against document dates accurately regardless of regional format differences.
5. **Flexible Substring & Word Matching**:
   - Searches match substrings and words across all metadata fields (`category`, `name`, `proposed`, `custom`, `extractedText`, `responseReceived`, `promptSent`), ensuring partial terms within words or medical test names (LFT, CBC, Lipid Profile, HbA1c) match seamlessly.
6. **Interactive Live Search Auto-Suggestions (100% Sourced from Received Document Metadata)**:
   - As the user types in the search field, a dynamic suggestion popup/dropdown appears.
   - Sourced **exclusively from your scanned library's actual received metadata** (actual document categories, proposed/virtual filenames, original file names, custom titles, and keywords extracted from document text).
   - Contains **zero hardcoded or static taxonomy lists**—if a term is not present in the received metadata of your documents, it will never be suggested. Tapping a suggestion instantly populates the search field; users can also ignore suggestions and continue typing.

---

### 5.6 Post-Scan Card-Based Layout, Inline Editing, Multi-Document Sharing, Atomic Moving & Permanent Deletion (Android UI Redesign)

1. **Card-Based Mobile UI (Clutter-Free Document View)**:
   - To eliminate mobile screen clutter caused by dense tabular layouts, the interface automatically transitions once scanning/processing completes (`allProcessed == true`) from the table into a clean, modern list of cards (`ElevatedCard`).
   - Each card represents a single processed document and displays:
     - **Name**: Original filename rendered as a clickable link (with link color and underline) that immediately opens the actual document in the system default viewer via `openDocument(context, doc)`.
     - **Virtual Name**: Proposed standardized filename presented in an in-line editable text field (`OutlinedTextField`), allowing the user to provide a unique custom name of their choice.
     - **Save on Blur**: Edits to the Virtual Name are automatically validated and persisted to metadata (`custom` field and external storage JSON) on focus loss (blur event), requiring no extra "Save" button taps.
2. **Dedicated Card Controls (Permanently Visible)**:
   - **Left Checkbox**: Dedicated selection checkbox permanently visible on the left side of every card.
   - **Right Expand Icon**: Dedicated expand/collapse icon (`ic_dropdown` / Chevron) permanently visible on the right side of every card (replacing previous secret gesture requirements).
3. **Expandable 3-Tier AI Diagnostic & Reasoning Panel (Full Request/Response Inspector)**:
   - Tapping a card's expand icon smoothly expands a comprehensive 3-tier diagnostic inspector panel that surfaces the complete request and response lifecycle across all 3 tiers:
     - 📱 **Tier 1: Phone Request**:
       - Endpoint URL and HTTP method (`POST /v1/classify`).
       - Google OIDC Authentication Token indicator (Bearer token attached & verified).
       - On-Device OCR Extracted Text length and payload preview snippet.
     - ☁️ **Tier 2: Cloudflare Backend Gateway**:
       - Cloudflare Worker HTTP status code (e.g., `200 OK`, `429 Too Many Requests`, `502 Bad Gateway`, `503 Service Not Configured`).
       - Per-user rate-limiting gate check result (`CLASSIFY_LIMITER`).
       - AI Provider dispatch routing (`gemini-3.5-flash-lite` primary / failover).
     - 🤖 **Tier 3: AI Model Execution & Root Cause Failure Diagnostic**:
       - **When AI Succeeds**: Displays provider model, prompt metadata (`system_instruction`, mode, token usage), raw model candidate JSON, category justification, and proposed name.
       - **When AI Fails (Exact Root Cause Diagnostic)**: Directly captures and displays why Gemini failed without hiding behind generic error codes:
         - **Google HTTP Status**: (e.g., `404 Not Found`, `400 Bad Request`, `403 Forbidden`, `429 Resource Exhausted`).
         - **Google Error Status**: (e.g., `NOT_FOUND`, `INVALID_ARGUMENT`, `PERMISSION_DENIED`, `RESOURCE_EXHAUSTED`).
         - **Google Exact Error Message**: Google's full server error message (e.g., *"models/gemini-3.5-flash-lite is not found for API version v1beta"*, *"API key expired or invalid"*).
         - **On-Device Fallback Execution**: Rationale for fallback (`fell back to on-device OCR`), local normalized category assigned, and matched content keywords/identifiers.
     - Dimensions, raw payload viewer, and document digest.
4. **Universal Multi-Document Sharing Sheet Integration**:
   - When one or more cards are selected via checkbox, a prominent **Share** action button is displayed (`📤 Share (${count})`).
   - Tapping Share opens the Android system share sheet (`ACTION_SEND` for a single file, `ACTION_SEND_MULTIPLE` for multiple files) via `DocExportHelper`.
   - Selected files are copied to app cache with their **proposed/custom virtual names**, allowing target apps (WhatsApp, Gmail, Google Drive, Bluetooth, etc.) to receive and send the files under their new virtual names while original storage files remain completely untouched.
5. **Strict 20-File Export Limit & Clean User Notice**:
   - The Share/Export button is strictly disabled if:
     - No cards are selected (`selectedCount == 0`), OR
     - More than 20 cards are selected (`selectedCount > 20`).
   - A clear, permanent descriptive notice is displayed cleanly below the action buttons: *"At a time only 20 files can be exported."* (no intrusive or redundant "Max 20" warning badge in the button bar).
6. **Atomic Multi-Document Moving with Resilient Folder Creation**:
   - **Beside Share Button**: A dedicated **Move** action button (`📦 Move (${count})`) is positioned beside the Share button in the post-scan action bar.
   - **State Behavior**: By default, the Move button is disabled. It becomes dynamically enabled once the user selects some or all cards via checkbox.
   - **Default Target Directory (`/storage/emulated/0/Download/SmartDocsTransferd/[Date +_+ Time stamp]`)**: When the user taps the Move action button, the destination defaults to `/storage/emulated/0/Download/SmartDocsTransferd/[Date +_+ Time stamp]`, where Date format is `DDMMYY` (e.g. `161126`) and Time format is `hh:mm:ss` (e.g. `00:53:02`), joined by an underscore (`DDMMYY_hh:mm:ss`). If the folder path does not exist, the app automatically creates it (`mkdirs()`), including candidate fallback paths (`Environment.DIRECTORY_DOWNLOADS`) and filesystem colon fallback (`DDMMYY_hh-mm-ss`) for devices enforcing strict FAT32 character restrictions.
   - **One-Tap Move or Custom Destination**: Tapping Move displays the full destination path with a **Move Now** button for immediate transfer without extra navigation, while also offering an **Other Folder...** option for custom directory picking via SAF.
   - **Renaming & Timestamp Date-Stamping**: Actual physical files are moved from their original locations to the target folder under their **updated virtual/custom names**, with their filesystem created/updated timestamp (`lastModified`) updated to match the document's extracted date stamp (supporting both ISO, Indian, and new 40-char `ddMMMyy` formats).
   - **Atomic Transaction Guarantee & Safe In-Place Staging**: The multi-file move operation uses resilient in-place temporary files (`.transfer_...tmp`) in the destination directory, avoiding hidden dot directories (`.moving_staging_...`) that can be restricted by Android or SAF. Files are verified, atomically renamed, and original files are safely deleted only after all transfers succeed. If any failure occurs during transfer, all temporary files are cleaned up and **zero original files are moved or altered**.
   - **Automatic Metadata & State Synchronization**: Upon a successful move, the application automatically updates the recorded URIs, filenames, and modified timestamps in both the in-memory document state and the app's persistent external metadata storage (`PersistentMetadataManager`), ensuring the app's tracking of scanned and pending files remains 100% accurate without re-scanning. Moved files in `Download/SmartDocsTransferd` are excluded from storage re-scans.
7. **Permanent Document Deletion with Safety Confirmation Dialog & Storage Sync**:
   - **Red-Colored Delete Action Button / Icon**: A dedicated red-colored button (`🗑️ (${count})`) is positioned in the action bar alongside Move and Share. It is disabled by default and becomes brightly enabled in alert red (`MaterialTheme.colorScheme.error`) whenever one or more cards are selected.
   - **Safety Confirmation Dialog**: Tapping the red Delete icon opens an explicit confirmation dialog with the prominent message: *"Selected items will be permanently deleted."* and details the count of files to be removed, offering **Delete Permanently** (Confirm) and **Cancel** options.
   - **Physical File Deletion**: Upon user confirmation, the selected physical files are permanently deleted from internal device storage or SD cards (`File.delete()` for direct files or `DocumentFile.delete()` / `ContentResolver.delete()` for content URIs).
   - **Real-Time App Storage Synchronization**: The deleted items are immediately purged from both in-memory state (`items`) and the app's persistent external storage metadata (`PersistentMetadataManager`), updating `smartdoc_metadata.json` so the app's inventory accurately reflects the deletion without requiring a re-scan. Selection is cleared automatically.
8. **Select All / Deselect All One-Tap Control**:
   - A dedicated toggle control at the top of the card list allows users to switch between selecting all visible cards or deselecting all cards with a single tap.
9. **Strict Duplicate & Non-Taxonomy Exclusion**:
   - Documents identified as duplicates (`status == "Duplicate"`) or documents that do not match the Indian Standards Taxonomy criteria (uncategorized, unclassified, `Other Document`, or processing errors) are strictly excluded from the card list—no card is ever rendered for them.

---

### 5.7 Persistent External Metadata Storage Across App Re-Installations (New Storage Persistence Feature)

1. **Preserving Metadata Across App Uninstallation**:
   - To prevent losing processed metadata when the user uninstalls and re-installs the app, metadata is persisted outside the app's private sandbox in public mobile storage.
   - **Default Location**: `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)/SmartDocManager/smartdoc_metadata.json`.
   - **Custom Location Option**: Users can choose a custom storage location or folder (via SAF directory picker `ACTION_OPEN_DOCUMENT_TREE`).
2. **Automatic Post-Scan Sync**:
   - After every scan and classification cycle, the latest metadata (categories, virtual proposed names, extracted text, dates, hashes, and justifications) is automatically written to this external persistent JSON file.
3. **Remembers Chosen Location**:
   - The application remembers the previously selected location and prompts or auto-syncs to it seamlessly on subsequent launches.
4. **Disk Cross-Check & Verification on Launch / Re-Install**:
   - When loading previously saved metadata after a fresh install or re-launch, the application cross-checks whether each recorded file still exists on device storage (`File.exists()` / MediaStore URI check).
   - If a file was deleted or moved, it is removed from the metadata.
   - If files still exist, their classification, proposed names, and metadata are restored immediately, **completely eliminating the need to re-scan or re-process existing documents**.

---

### 5.8 Storage Access Framework (SAF) File Store Integration (Points ##8.2 – ##8.5)

The Android application functions as an intelligent, system-wide document provider and file manager for external apps (Gmail, WhatsApp, Google Chrome, Drive, etc.):

1. **System Picker Registration (Point ##8.2)**:
   - `AndroidManifest.xml` registers intent filters for `android.intent.action.GET_CONTENT`, `android.intent.action.OPEN_DOCUMENT`, and `android.intent.action.PICK` supporting MIME types `*/*`, `image/*`, `application/pdf`, and `application/*`.
   - When any third-party app requests a document upload, **SmartDocManager** appears as an available document source in the system chooser.
2. **Storage Access Framework (SAF) Root Provider**:
   - Implemented in [`SmartDocumentsProvider.kt`](android/app/src/main/java/com/smartdoc/poc/SmartDocumentsProvider.kt) (`android.content.action.DOCUMENTS_PROVIDER`).
   - Appears directly in the Android OS Files app root drawer alongside Google Drive, Downloads, and Gallery.
3. **Dual File Selection Modes in File Manager / Picker Mode (Point ##8.3)**:
   - When launched in picker mode, each document row displays a selection **Checkbox** at the beginning.
   - **Single-File Tap Selection (Point ##8.4)**: Tapping any document row immediately selects that single file and delivers it to the target application (Gmail, WhatsApp, browser) with its **proposed virtual name** (e.g. `PAN_Card_ABCDE1234F.jpg`, `Aadhaar_Card_JohnDoe.pdf`).
   - **Multi-File Checkbox Selection (Point ##8.5)**: Tapping checkboxes allows the user to select multiple files simultaneously.
4. **Prominent "Done (${count})" Action Button (Point ##8.5)**:
   - When one or more checkboxes are checked, a prominent **`Done (${count})`** button appears in the top bar directly over the refresh and play/pause button section.
   - Tapping `Done` packages all selected documents into an Android `ClipData` intent with `FLAG_GRANT_READ_URI_PERMISSION`, delivering all files to the calling app with their proposed virtual names.
5. **Non-Destructive FileProvider Caching**:
   - Managed via [`DocExportHelper.kt`](android/app/src/main/java/com/smartdoc/poc/DocExportHelper.kt). The helper copies files temporarily to cache storage under their proposed virtual names so that calling apps receive the virtual name as `OpenableColumns.DISPLAY_NAME` while original files on device storage remain **completely unaltered and untouched**.

---

### 5.9 Windows Desktop App Mode & Dedicated Window Launcher

- **App Mode Execution**: Launches an isolated native desktop window (`msedge.exe --app=http://127.0.0.1:8501 --window-size=1280,850`) without browser navigation bars or tabs via `windows/desktop_launcher.py`. Closing the window cleanly shuts down the background server.
- **Unified Modern Application Icon**: High-resolution branded application icon featuring layered document folders with transparent background across Windows executable, setup wizard (`icon.ico`), Streamlit web UI (`icon.png`), and Android mipmap launcher icons (`ic_launcher.png`, `ic_launcher_round.png`).
- **Consistent Virtual Naming Across Desktop & Android**: Document virtual naming in the Windows Desktop application strictly mirrors Android's deterministic $\le 40$-character convention: `CAT_DocumentNameAbbrev_ddMMMyy.ext` (e.g. `FIN_IncomeTaxForm16_16Nov26.pdf`, `IDN_AadhaarCard_15Aug22.jpg`). User custom overrides take top display precedence, followed by compliant virtual proposed names.
- **One-Click Desktop Shortcut**: Compiles bytecode and places a one-click launcher icon directly on the Windows Desktop (`SmartDocManager.lnk`).

---

## 6. Authoritative Compilation, Build & Export Guide (Main Point ##9)

This section provides the authoritative, tested command reference to compile, build, test, and export builds for **Android Debug**, **Android Release**, and **Windows Setup Installer**:

### 6.1 Quick Command Reference Table

| Target Deliverable           | Output Artifact Path                                 | Primary Build Command                                        | Alternative CLI / Direct Tool                                                                        |
| ---------------------------- | ---------------------------------------------------- | ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| **Android Debug APK**        | `output/android/SmartDocManager-debug.apk`           | `pwsh -File .\scripts\10-build-android-debug.ps1`            | `pwsh -File .\dev.ps1 -Action BuildAndroidDebug` <br> _or_ `cd android; .\gradlew assembleDebug`     |
| **Android Release APK**      | `output/android/SmartDocManager-release.apk`         | `pwsh -File .\scripts\11-build-android-release.ps1`          | `pwsh -File .\dev.ps1 -Action BuildAndroidRelease` <br> _or_ `cd android; .\gradlew assembleRelease` |
| **Windows Setup Wizard**     | `output/windows/installer/SmartDocManager_Setup.exe` | `pwsh -File .\scripts\build-installer.ps1`                   | `pwsh -File .\dev.ps1 -Action BuildInstaller` <br> _uses Inno Setup 6 (`ISCC.exe`)_                  |
| **Windows Standalone App**   | `output/windows/dist/SmartDocManager/`               | `pwsh -File .\scripts\build-installer.ps1`                   | _PyInstaller uncompressed onedir bundle_                                                             |
| **Windows Desktop Shortcut** | `%USERPROFILE%\Desktop\SmartDocManager.lnk`          | `pwsh -File .\dev.ps1 -Action BuildWindows`                  | `pwsh -File .\scripts\09-build-windows.ps1`                                                          |
| **Deploy Debug to Device**   | Installed onto connected phone via ADB               | `pwsh -File .\scripts\15-deploy-android-debug.ps1 -NoPrompt` | `pwsh -File .\dev.ps1 -Action DeployDebug`                                                           |

---

### 6.2 Compiling & Exporting Android Debug APK

Builds the debug APK (versionCode 2, versionName "0.2") and places the deliverable in `output/android/SmartDocManager-debug.apk`:

```powershell
# Master script execution:
powershell -ExecutionPolicy Bypass -File scripts\10-build-android-debug.ps1

# Dev controller equivalent:
powershell -ExecutionPolicy Bypass -File dev.ps1 -Action BuildAndroidDebug

# Direct Gradle equivalent:
Push-Location android
.\gradlew.bat --no-daemon assembleDebug
Pop-Location

# Deploy and launch immediately onto connected Android device via ADB:
powershell -ExecutionPolicy Bypass -File scripts\15-deploy-android-debug.ps1 -NoPrompt
```

---

### 6.3 Compiling & Exporting Android Release APK (Signed Update)

The release build automatically autodiscovers `keystores/smartdocmanager-release.jks`. Compiled with versionCode 2 / versionName "0.2" so that running `adb install -r` cleanly updates existing app installations without signature or version downgrade errors:

```powershell
# Step 1 (Optional one-time keystore & SHA-1 setup):
powershell -ExecutionPolicy Bypass -File scripts\20-setup-signing-and-sha1.ps1

# Step 2: Compile and sign the Release APK:
powershell -ExecutionPolicy Bypass -File scripts\11-build-android-release.ps1

# Dev controller equivalent:
powershell -ExecutionPolicy Bypass -File dev.ps1 -Action BuildAndroidRelease

# Output deliverable:
# output\android\SmartDocManager-release.apk

# Verify signature scheme v2/v3:
$env:PATH += ";$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0"
apksigner verify --verbose output\android\SmartDocManager-release.apk

# Install release APK onto phone as an update:
adb install -r output\android\SmartDocManager-release.apk
```

---

### 6.4 Compiling & Exporting Windows Setup Installer Wizard

Compiles the standalone PyInstaller onedir distribution and packages it into a production-grade Windows Setup Wizard (`.exe`) with desktop shortcuts, Start Menu entries, and uninstaller using Inno Setup 6:

```powershell
# Master installer builder script:
powershell -ExecutionPolicy Bypass -File scripts\build-installer.ps1

# Dev controller equivalent:
powershell -ExecutionPolicy Bypass -File dev.ps1 -Action BuildInstaller

# Output deliverables:
# 1. Setup Wizard: output\windows\installer\SmartDocManager_Setup.exe
# 2. Portable App: output\windows\dist\SmartDocManager\SmartDocManager.exe
```

---

### 6.5 Running Full Test Verification Suites

Verifies all 73 Windows tests and 23 Cloudflare Worker tests:

```powershell
# Run 73 Windows unit tests:
powershell -ExecutionPolicy Bypass -File scripts\07-test-windows.ps1

# Run 23 Cloudflare Worker tests:
npm test --prefix hosted-backend/cloudflare
```

---

## 7. Cloudflare Worker Deployment & Free API Quotas (smartdocmanager-api)

### 7.1 Deploy Worker

Configure and deploy the Cloudflare Worker serverless proxy (`smartdocmanager-api`):

```powershell
Push-Location hosted-backend/cloudflare
npm install
npm test
npx wrangler secret put GEMINI_API_KEY
npx wrangler secret put GROQ_API_KEY
npx wrangler deploy
Pop-Location
```

### 7.2 Cloudflare Dashboard Configuration

Under **Workers & Pages → smartdocmanager-api → Settings → Variables and Secrets**, configure:

| Variable / Secret              | Type                  | Configured Value in `wrangler.toml`                                                                                                                 |
| ------------------------------ | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GEMINI_API_KEY`               | **Secret**            | Google AI Studio Gemini API Key                                                                                                                     |
| `GROQ_API_KEY`                 | **Secret**            | Groq Cloud API Key                                                                                                                                  |
| `GOOGLE_DESKTOP_CLIENT_SECRET` | **Secret** (Optional) | Client Secret for desktop OAuth broker (`/v1/auth/token`)                                                                                           |
| `GOOGLE_CLIENT_IDS`            | Plaintext             | `492541392963-up746ino0bjdhufq32g1cnq2tqqbqs8o.apps.googleusercontent.com,492541392963-b0mf650fohoas9m6a24fa32enrstjrr6.apps.googleusercontent.com` |
| `GOOGLE_DESKTOP_CLIENT_ID`     | Plaintext             | `492541392963-up746ino0bjdhufq32g1cnq2tqqbqs8o.apps.googleusercontent.com`                                                                          |
| `ALLOWED_GOOGLE_EMAILS`        | Plaintext             | `csharma2212@gmail.com,*`                                                                                                                           |
| `ALLOW_LEGACY_TOKEN`           | Plaintext             | `true`                                                                                                                                              |
| `GEMINI_MODEL`                 | Plaintext             | Default: `gemini-3.5-flash-lite`                                                                                                                    |
| `GROQ_MODEL`                   | Plaintext             | Default: `openai/gpt-oss-20b`                                                                                                                       |
| `USE_GROQ_ONLY`                | Plaintext             | Default: `false` (When true, routes requests directly to Groq, bypassing Gemini)                                                                   |

### 7.3 Cloudflare Abuse Rate Limiter (`CLASSIFY_LIMITER`)

In `wrangler.toml`, rate limiting is enforced per IP/principal:

```toml
[[unsafe.bindings]]
name = "CLASSIFY_LIMITER"
type = "ratelimit"
namespace_id = "1001"
simple = { limit = 10, period = 60 }
```

### 7.4 Free API Tier Limits, Client Pacing & Interactive Quota Dialogs

- **Google Gemini 3.5 Flash Lite**: **15 RPM**, **1,000,000 TPM**, **1,500 RPD** (Free Tier). The application fully utilizes the complete 1,500 requests per day limit.
- **Groq Cloud**: **30 RPM**, **7,000 RPD** (Free Tier). Serves as secondary fallback upon user consent when Gemini daily quota is exhausted.
- **Client-Side Self-Throttling**: The app strictly enforces a maximum dispatch rate of **10 requests per 60 seconds** on the client side, preventing 429 burst limit rejections.
- **Gemini Quota Exhaustion Dialog (Yes / No)**:
  - When Gemini quota is exhausted (HTTP 429 or 1,500 requests reached), the app halts queue processing and displays:
    `"Gemini quota is completely utilized! It will be reset at <HH:MM> hours on [dd-MMM-yyyy] date. Do you want me to proceed with Groq?"` with **Yes** and **No** buttons.
  - Reset time is dynamically computed based on Gemini's daily quota reset at Midnight US Pacific Time (00:00 PT), formatted in local 24-hour clock time (e.g. `13:30 hours on 18-Nov-2026`).
- **Groq Quota Exhaustion Dialog (OK)**:
  - If the user selects **Yes**, processing continues using the Groq API up to its daily limit.
  - When Groq daily quota is exhausted (HTTP 429), the app displays:
    `"Groq quota is completely utilized! It will be reset at <HH:MM> hours on [dd-MMM-yyyy] date."` with an **OK** button.
  - Reset time is dynamically computed based on Groq's daily reset at Midnight UTC (00:00 UTC), formatted in local 24-hour clock time (e.g. `05:30 hours on 18-Nov-2026`).
- **Safe Pending Document Preservation**:
  - If the user selects **No** (or when Groq quota is exhausted), unprocessed documents remain in **pending** status (`status == "pending"` / `processed == "False"`).
  - There is **no local rule-based heuristic classification** fallback. All file renaming, duplicate relocation to `Download/Duplicates/`, and on-device text extraction (OCR) remain active.
  - The application automatically tracks pending documents and reprocesses them once the quota reset timestamp passes.

---

## 8. Git Ignore Specification

All sensitive files, signing keys, build outputs, and local databases are strictly protected in [`.gitignore`](.gitignore):

```gitignore
# --- Python & Desktop Build Artifacts ---
.venv/
windows/.venv/
__pycache__/
*.py[cod]
.pytest_cache/
dist/
build/
*.spec
*.exe
*.lnk

# --- Local Databases & Metadata ---
*.sqlite3
*.sqlite3-journal
*.db
.smartdocmanager/

# --- Android, Gradle & Native Toolchains ---
.gradle/
android/.gradle/
android/build/
android/app/build/
local.properties
android/local.properties
*.iml
.idea/

# --- Secrets, Keystores & Fingerprints (Security Critical) ---
keystores/
*.jks
*.keystore
keystore.properties
signing.properties
signing-fingerprints.txt
*fingerprint*.txt
*sha1*.txt
android-credentials.json
.env
*.key
*.pem

# --- Node.js & Cloudflare ---
node_modules/
hosted-backend/cloudflare/node_modules/
.wrangler/

# --- Build Outputs, Execution Logs & Scaffolding ---
output/
logs/
backups/
diagnostics/*.log
diagnostics/build-session.log
```

---

## 9. Complete Project Recreation Prompt

To recreate this entire project from scratch with the exact same architecture, automated setup, Windows desktop app window, Android self-signing, SHA-1 extraction, and non-destructive virtual management, provide the following prompt to an AI coding assistant:

```markdown
Recreate the complete, production-ready "SmartDocManager" project: a cross-platform intelligent document classification, organization, and virtual management system consisting of an Android Native app (Kotlin/Jetpack Compose), a Windows Desktop app (Python/Streamlit with dedicated App Mode), and a serverless proxy backend (Cloudflare Worker named `smartdocmanager-api`).

### 1. Architectural Foundation, Local OCR & Free AI:

- Document bytes and images NEVER leave the client machine.
- Text Recognition (OCR) is strictly on-device:
  - Android: Native `PdfRenderer` (up to 40 pages) + Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`).
  - Windows: Native `pypdfium2` textpages + local Tesseract OCR fallback.
- Full extracted text (unrestricted, removing the prior 16,000 character limit to leverage Gemini's context window) is transmitted to the secure Cloudflare Worker serverless proxy (`smartdocmanager-api`) for AI classification and naming proposal.
- Client-Side Rate Limiter & Daily Free Quotas:
  - Client-side queue pacing guarantees no more than 10 cloud requests per 60 seconds are dispatched.
  - The application utilizes the full 1,500 requests per day limit for Google Gemini 3.5 Flash Lite (no 1,000 cap).
  - Default API is always Google Gemini. Manual engine selection is removed from the UI.
- Quota Exhaustion & Interactive Switch Workflow:
  - When Gemini quota is exhausted (HTTP 429), client halts and displays:
    `"Gemini quota is completely utilized! It will be reset at <HH:MM> hours on [dd-MMM-yyyy] date. Do you want me to proceed with Groq?"` with Yes and No buttons.
    Reset time is calculated based on Midnight US Pacific Time (00:00 PT) in local 24-hour clock time (e.g. `13:30 hours on 18-Nov-2026`).
  - If user selects Yes, processing proceeds using Groq (`openai/gpt-oss-20b`) utilizing its daily quota.
  - When Groq quota is exhausted (HTTP 429), client displays:
    `"Groq quota is completely utilized! It will be reset at <HH:MM> hours on [dd-MMM-yyyy] date."` with OK button.
    Reset time is calculated based on Midnight UTC (00:00 UTC) in local 24-hour clock time (e.g. `05:30 hours on 18-Nov-2026`).
  - No Local Rule Fallback: Unprocessed documents remain in pending status (`status == "pending"` / `processed == "False"`). Local rule classification fallback is eliminated. The application keeps track of pending documents and auto-resumes once the quota reset timestamp passes.
- Zero AI API keys are exposed to clients; all provider keys live in Cloudflare Worker encrypted secrets.

### 2. Authentication & Controlled Access:

- Google Sign-In Gate: App launch displays ONLY the login gate with a clean, single-click "Sign in with Google" button. No folders, tables, or controls are visible until authenticated.
- Windows Desktop Authentication:
  - Uses Google Desktop OAuth (authorization code + PKCE) on a dynamic loopback callback (`http://127.0.0.1:<port>/`).
  - Opens a dedicated, frameless authentication window (`msedge.exe --app=...` without browser tabs or address bar).
  - On callback, automatically sends native Windows `WM_CLOSE` to close the login window and restores/brings the main SmartDocManager app window to the front.
  - Automatic Polling: Streamlit automatically polls session auth state every 500ms and triggers `st.rerun()` the instant sign-in finishes, without requiring any manual "Refresh" button.
  - Zero Secrets in Git: The Desktop Client Secret is kept strictly in an untracked local `.env` file (`SMARTDOC_GOOGLE_CLIENT_SECRET=GOCSPX-...`) or brokered securely via the Cloudflare Worker (`POST /v1/auth/token`).
- Android Native Authentication:
  - Uses Android Credential Manager with Google ID Token Option.
  - 100% Secretless: Google authenticates the app cryptographically via its Package Name (`com.smartdoc.poc`) and SHA-1 certificate fingerprint registered in Google Cloud Console. No secrets are ever stored in or needed by the Android client.
  - 7-Day Session Persistence: User credentials and tokens are cached in `smartdoc_auth` SharedPreferences with a 7-day expiration. Subsequent app launches immediately bypass the login screen and transition straight to the main screen. Tapping "Sign out" clears tokens and returns to the login screen.
- Cloudflare Worker Authorization:
  - Validates Google ID tokens via Google's `tokeninfo` endpoint.
  - Enforces audience matching against `GOOGLE_CLIENT_IDS` and an email allowlist (`ALLOWED_GOOGLE_EMAILS = "csharma2212@gmail.com,*"`) before routing requests.
  - Enforces per-IP abuse control via `CLASSIFY_LIMITER` (10 requests per 60 seconds).

### 3. 100% Non-Destructive Virtual Management:

- Original documents on disk are NEVER modified, renamed, or deleted.
- Dual-Layer Deduplication: Identifies duplicates via Tier 1 (SHA-256 binary hash) and Tier 2 (normalized content text and virtual proposed name matching). Duplicates are marked and hidden from the active library and search view, while preserving all disk files untouched.

### 4. Local Indian Standards Taxonomy & Date-Stamped Naming:

- Scope Boundary & Separation from Search: This heuristic classification engine is evaluated exclusively during the initial document analysis phase (and as an offline local fallback). Runtime search queries never re-evaluate or execute taxonomy rules; search operates strictly on the resulting document metadata.
- The rule engine (`smartdoc/indian_taxonomy.py` and `core.py` on Windows, `Classifier.kt` on Android, `shared-spec/taxonomy.json`) classifies documents across **519 Granular Indian Categories**:
  Aadhaar Card, Permanent Account Number Card, Voter Identity Card, Passport, Driving License, Ration Card, Birth Certificate, Death Certificate, Marriage Certificate, Domicile Certificate, Income Certificate, Caste Certificate, Other Backward Class Certificate, Economically Weaker Section Certificate, Legal Heir Certificate, Encumbrance Certificate, Transfer Certificate, Migration Certificate, Character Certificate, Bonafide Certificate, Overseas Citizen of India Card, Person of Indian Origin Card, Visa Document, Residence Certificate, Class 10 Marksheet, Class 12 Marksheet, Undergraduate Degree Certificate, Postgraduate Degree Certificate, Provisional Degree Certificate, School Leaving Certificate, Semester Marksheet, Grade Card, Hall Ticket, Convocation Letter, Academic Transcript, Course Completion Certificate, Extracurricular Activity Certificate, Sports Merit Certificate, National Cadet Corps Certificate, National Service Scheme Certificate, Scout and Guide Certificate, Entrance Exam Admit Card, Entrance Exam Scorecard, Counseling Allotment Letter, Seat Confirmation Letter, Tuition Fee Receipt, Thesis Submission Certificate, PhD Degree Certificate, Engineering Degree Certificate, Diploma Certificate, Industrial Training Institute Certificate, National Skill Development Corporation Certificate, Apprenticeship Certificate, Internship Completion Certificate, Fellowship Award Letter, Scholarship Sanction Letter, Research Grant Award Letter, Language Proficiency Certificate, IELTS Scorecard, TOEFL Scorecard, GRE Scorecard, GMAT Scorecard, Study Abroad Offer Letter, Confirmation of Acceptance for Studies Letter, I-20 Form, DS-160 Form, Schengen Visa Form, Disability Certificate, Ayushman Bharat Health Account Card, Ayushman Card, Central Government Health Scheme Card, Ex-Servicemen Contributory Health Scheme Card, Employees State Insurance Corporation Card, Medical Fitness Certificate, Hospital Discharge Summary, Diagnostic Laboratory Report, Medical Prescription, Organ Donor Certificate, Blood Group Certificate, Vaccination Certificate, COVID-19 Vaccination Certificate, MRI Report, X-Ray Report, CT Scan Report, Ultrasound Report, Blood Test Report, Pathology Report, Biopsy Report, Disability Medical Certificate, Severe Disability Certificate, Specified Disease Treatment Receipt, Audiometry Report, Vision Certificate, Color Blindness Certificate, Physical Fitness Certificate, Income Tax Return Verification Form, Form 16, Form 16A, Form 26AS, Annual Information Statement, Taxpayer Information Summary, Form 15G, Form 15H, Lower Deduction Certificate, Advance Tax Challan, Self-Assessment Tax Challan, Regular Assessment Tax Challan, Wealth Tax Return, Form 12BB, Form 49A, Form 49B, Form 10E, Income Tax Assessment Order, Tax Deduction at Source Certificate, Tax Collection at Source Certificate, Bank Passbook, Cheque Book, Fixed Deposit Receipt, Recurring Deposit Receipt, Demat Account Statement, Mutual Fund Account Statement, Consolidated Account Statement, Systematic Investment Plan Statement, Capital Gains Statement, Sovereign Gold Bond Certificate, Employees Provident Fund Statement, Public Provident Fund Passbook, National Pension System Permanent Retirement Account Number Card, Savings Account Statement, Current Account Statement, Non-Resident External Account Statement, Non-Resident Ordinary Account Statement, Loan Account Statement, Credit Card Statement, Credit Card Bill, Universal Account Number Card, Pension Payment Order, PF Transfer Claim Form, PF Withdrawal Form, Form 19, Form 10C, Form 31, Form 10D, Pension Claim Form, Monthly Pension Form, EPS Nomination Form, Gratuity Nomination Form, Shram Card, Mahatma Gandhi National Rural Employment Guarantee Act Job Card, Employee Offer Letter, Employee Appointment Letter, Employee Relieving Letter, Employee Experience Certificate, Employee Salary Slip, Employee Appraisal Letter, Bonus Letter, Increment Letter, Arrears Statement, Non-Disclosure Agreement, Employment Contract, Maternity Benefit Claim Form, Dependent Identity Card, Leave Travel Concession Bill, Medical Reimbursement Claim Form, Travelling Allowance Bill, Dearness Allowance Document, House Rent Allowance Receipt, Leave Encashment Statement, Voluntary Retirement Scheme Offer Document, Notice Pay Recovery Receipt, Full and Final Settlement Statement, Universal Land Parcel Identification Number, Khatauni, Khasra, Record of Rights, Sale Deed, Lease Deed, Rent Agreement, Sub-letting Agreement, Commercial Lease Deed, Mutation Certificate, Property Tax Receipt, Patta Document, Search Report, Property Assessment Order, Building Plan Approval Certificate, Occupancy Certificate, Completion Certificate, Commencement Certificate, Real Estate Regulatory Authority Registration Certificate, Allotment Letter, Builder-Buyer Agreement, Possession Letter, Society No Dues Certificate, Cooperative Housing Society Share Certificate, Non-Agricultural Order, Land Conversion Certificate, Rent Receipt, Life Insurance Policy Document, Health Insurance Policy Document, Motor Vehicle Insurance Policy, Two Wheeler Insurance, Commercial Vehicle Insurance, Marine Insurance Policy, Transit Insurance Policy, Burglary Insurance Policy, Fire Insurance Policy, Fidelity Guarantee Insurance Policy, Directors and Officers Liability Insurance, Professional Indemnity Insurance, Public Liability Insurance, Workmen Compensation Insurance Policy, Group Term Life Insurance Policy, Group Health Insurance Policy, Travel Insurance Policy, Medical Insurance Premium Receipt, Electricity Bill, Water Bill, Piped Gas Bill, Liquefied Petroleum Gas Passbook, Landline Telephone Bill, Broadband Internet Bill, Postpaid Mobile Bill, Municipal Corporation Tax Receipt, Vehicle Registration Certificate, Pollution Under Control Certificate, Vehicle Fitness Certificate, Commercial Vehicle Permit, Learner Driving License, International Driving Permit, FASTag Registration Document, Toll Tax Receipt, Traffic Challan Document, Notice of Transfer of Ownership of Motor Vehicle, Report of Transfer of Ownership of Motor Vehicle, Application for Transfer of Ownership in case of Death, Application for Making Entry of an Agreement of Hire Purchase, Notice of Termination of an Agreement of Hire Purchase, Vehicle Sale Receipt, Surveyor Motor Claim Report, Total Loss Settlement Letter, Salvage Value Document, Udyam Registration Certificate, Goods and Services Tax Registration Certificate, Certificate of Incorporation, Memorandum of Association, Articles of Association, Partnership Deed, Limited Liability Partnership Agreement, Trade License, Food Safety and Standards Authority of India License, Import Export Code Certificate, Fire No Objection Certificate, State Pollution Control Board NOC, Shops and Establishments Act Registration Certificate, Customs Duty Receipt, Bill of Lading, Air Waybill, Letter of Credit, Bank Guarantee Document, Promissory Note, Bill of Exchange, Auditor Report, Balance Sheet Document, Profit and Loss Statement, Cash Flow Statement, Statement of Changes in Equity, Directors Report, Annual Return, Financial Statement, Director Identification Number Allotment Letter, Cryptographic Signature Token Document, Chartered Accountant Certificate, Company Secretary Certificate, Cost Accountant Certificate, Net Worth Certificate, Solvency Certificate, Valuation Report, Actuarial Valuation Report, Startup India Recognition Certificate, DPIIT Registration Document, MSME Samadhaan Filing Document, Kisan Credit Card Passbook, Soil Health Card, Crop Insurance Policy, Agricultural Land Passbook, Education Loan Sanction Letter, Education Loan Disbursement Letter, Home Loan Sanction Letter, Home Loan Agreement, Personal Loan Agreement, Vehicle Loan Agreement, Business Loan Agreement, Overdraft Facility Agreement, Cash Credit Limit Sanction Letter, Loan Pre-closure Statement, Loan Closure No Objection Certificate, Amortization Schedule, CIBIL Credit Report, Experian Credit Report, Equifax Credit Report, CRIF High Mark Credit Report, Home Loan Interest Certificate, Education Loan Interest Certificate, Affidavit, Power of Attorney, Will Document, Gift Deed, Relinquishment Deed, Trust Deed, Indemnity Bond, Surety Bond, Bail Bond, Court Summons, Court Judgment Document, Decree Certificate, Caveat Petition, Habeas Corpus Petition, Notarized Document, Stamp Paper, Judicial Stamp Paper, Franked Document, Non-compete Agreement, Divorce Decree Document, Separation Agreement, Maintenance Order, Adoption Deed, Guardianship Certificate, Custody Order, Name Change Gazette Notification, Gender Change Gazette Notification, Religion Change Document, Signature Verification Certificate, Photograph Verification Certificate, Legal Notice, Reply to Legal Notice, Cease and Desist Notice, Arbitral Award Document, Mediation Settlement Agreement, Lok Adalat Award, FIR Cancellation Report, Charge Sheet Document, Autopsy Report, Police Clearance Certificate, First Information Report, Motor Accident Claims Tribunal Petition, Consumer Court Complaint Document, Joint Venture Agreement, Memorandum of Understanding, Service Level Agreement, Vendor EmpEmpanelment Letter, Tender Document, Earnest Money Deposit Receipt, Performance Bank Guarantee, Letter of Intent, Work Order, Measurement Book Document, Running Account Bill, Completion Report, Defect Liability Certificate, Handing Over Certificate, Wayleave Agreement, Right of Way Document, Easement Right Document, Hypothecation Agreement, Pledge Agreement, Mortgage Deed, Deed of Assignment, Conveyance Deed, Exchange Deed, Partition Deed, Family Settlement Agreement, Surrender Deed, Rectification Deed, Cancellation Deed, Revocation of Power of Attorney, Sub-Lease Agreement, Leave and License Agreement, Rent Control Act Registration, Paying Guest Agreement, Hostel Accommodation Allotment Letter, Arms License, Liquor Permit, Broadcast License, Amateur Radio License, Drone Registration Certificate, Pilot License, Seafarer Identification Document, Merchant Navy Continuous Discharge Certificate, Emigration Check Required Document, Emigration Clearance Document, Pravasi Bharatiya Bima Yojana Policy, Yellow Fever Vaccination Certificate, Polio Vaccination Certificate, International Certificate of Vaccination, Export Promotion Capital Goods Authorization, Advance Authorization Certificate, Duty Free Import Authorization, Certificate of Origin, Phytosanitary Certificate, Health Certificate for Export, Directorate General of Foreign Trade Certificate, Registration Cum Membership Certificate, ISO Certification Document, BIS Hallmark Certificate, Agmark Certificate, ISI Mark Certificate, Trademark Registration Certificate, Patent Certificate, Copyright Registration Certificate, Geographical Indication Certificate, Design Registration Certificate, Khadi Mark Certificate, Handloom Mark Certificate, Silk Mark Certificate, Trademark Search Report, Copyright Assignment Deed, Patent Licensing Agreement, Franchise Agreement, Professional Tax Enrolment Certificate, Professional Tax Registration Certificate, Municipal Trade License, Contract Labour License, Provident Fund Registration Certificate, ESIC Registration Certificate, Factory License, Boiler Registration Certificate, Lift Inspector License, Weighing and Measuring Instruments Stamping Certificate, Entertainment Tax Registration, Luxury Tax Registration, Cinema License, Hotel License, Restaurant License, Liquor Serving License, Private Security Agency Regulation Act License, Telecom Regulatory Authority of India Registration, Other Service Provider License, Internet Service Provider License, Virtual Network Operator License, Authorized Dealer License, Money Changer License, Non-Banking Financial Company Registration Certificate, Microfinance Institution Registration, Chit Fund Registration Certificate, Nidhi Company Registration, Section 8 Company License, NGO Darpan Registration Document, Foreign Contribution Regulation Act Registration, Society Registration Certificate, Trust Registration Certificate, Section 12A Registration Certificate, Section 80G Registration Certificate, Corporate Social Responsibility Project Registration, Securities and Exchange Board of India Registration Certificate, Stock Broker License, Depository Participant License, Portfolio Manager Registration, Investment Adviser Registration, Mutual Fund Distributor Certificate, Insurance Regulatory and Development Authority of India License, Insurance Agent License, Surveyor and Loss Assessor License, Third Party Administrator License, Custom House Agent License, Freight Forwarder Registration, Multimodal Transport Operator Registration, Port Trust Registration Certificate, Dock Entry Permit, Airport Entry Permit, Bureau of Civil Aviation Security Clearance, Transit Way Bill, Lorry Receipt, Goods Receipt Note, Delivery Challan, Purchase Order, Proforma Invoice, Commercial Invoice, Debit Note, Credit Note, Receipt Voucher, Payment Voucher, Journal Voucher, Petty Cash Voucher, Boarding Pass, Flight Ticket, Train Ticket, Bus Ticket, Hotel Booking Confirmation, Forex Purchase Receipt, Customs Declaration Form, Police Verification Report, Background Verification Report, Credit Reference Letter, Income Proof Document, Address Proof Document, Identity Proof Document, Age Proof Document, Relationship Proof Document, Survival Certificate, Life Certificate, Unmarried Certificate, Bachelorhood Certificate, Trade Test Certificate, Proficiency Certificate, TPA ID Card, Cashless Pre-Authorization Form, Cashless Approval Letter, Cashless Denial Letter, Claim Settlement Letter, Claim Denial Letter, TPA Query Letter, Explanation of Benefits, Detailed Hospital Bill, Final Hospital Bill, Breakup of Hospital Bill, Implant Invoice, Surgeon Certificate, Treating Doctor Certificate.
- Strict Content-Metadata-Only Evaluation: Filenames are strictly excluded from category determination.
- Maximum-Identifier Rule: When multiple categories match candidate identifiers, the category with the highest count of distinct matched identifiers is chosen.
- Date Extraction: Extracts dates in standard Indian formats (DD/MM/YYYY, DD-Mon-YYYY, 1st day of Month Year, AY 20XX-YY, FY 20XX-YY, Month Year).
- Standardized Naming: Constructs short, meaningful names with extracted identifiers and dates (e.g. `PAN_Card_ABCDE1234F_2021-08-15.pdf`, `Driving_Licence_DL0420110012345_1988-04-15.pdf`, `Prescription_Dr_Mehta_2024-09-05.pdf`).

### 5. Android Native Application Specifications (Points ##1 – ##8 & Enhancements):

- Persistent Login Gate (Points ##1 – ##3): Authenticates via Credential Manager, caches session tokens in `smartdoc_auth` with 7-day expiration, and automatically redirects straight to the library screen on subsequent launches. "Sign out" button clears session.
- Automatic Whole-Device Scanning & Selective Recursive Folder Scanning (Points ##4 – ##5): Scans entire device storage for PDFs and images upon login (skipping Android OS and internal directories `/Android/data`, `/Android/obb`, `/proc`, `/sys`). Features a native in-app Folder Browser Dialog with quick-access shortcuts (📥 Downloads, 📄 Documents, 📷 DCIM, 🖼️ Pictures, 💬 WhatsApp, 📱 Root) that bypasses Android 11+ SAF system picker blocks on the Downloads directory. If the user cancels folder selection or dismisses the prompt, the app automatically falls back to scanning the complete phone storage as per default behavior. Users can change their mind and switch folders mid-scan via `"📁 Change Folder"` or the header scope chip (`📁 Entire Device` / `📁 <FolderName>`), cancelling active scans and restarting for the new folder. If a folder contains zero documents, the small progress spinner stops immediately at `[0/0]` and displays a clean empty-state card. Orchestrates permissions proactively (`MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `POST_NOTIFICATIONS`). Issues a high-priority system notification if app is in background or screen is off; surfaces a cancellable in-app banner toast if active in foreground.
- Content-Metadata-Only Classification & Maximum-Identifier Rule: Filenames are strictly excluded from category evaluation. Conflict resolution selects the category with the highest number of distinct matched identifiers.
- Dual-Layer Duplicate Detection & Auto-Hiding: Evaluates both Tier 1 (SHA-256 binary hash) and Tier 2 (normalized content text and virtual proposed name matching) to catch duplicate documents even when PDF creation timestamps differ. Duplicates are hidden from both active view and search results.
- Resilient Cloud AI Category Normalization: Accepts and normalizes free-form categories returned by Cloud AI (Gemini 3.5 Flash Lite or Groq) such as `"Invoice"`, `"Medical Report"`, `"Prescription"`, or `"Salary Slip"`, truncating reasoning up to 2,000 characters rather than rejecting with `INVALID_RESPONSE`.
- Top Header & Two-Group Grey Bar:
  - Header Row: Features Title, Version, non-clickable provider info badge (`✨ Gemini`), and unclipped Sign Out button. Manual engine selection toggle is removed from the UI; Gemini is permanently default primary.
  - Quota Exhaustion Flow: Interactive dialogs for Gemini (Yes/No switch to Groq) and Groq (OK) with dynamic 24-hour clock reset timestamps.
  - Two-Group Grey Bar Layout:
    - Group 1 (Left Column): Vertically arranged with active folder (`📁 Entire Device` or `📁 <FolderName>`) on top and `[processed / total]` status count at the bottom.
    - Group 2 (Remaining width, `weight(1f)`, extra left padding): Equispaced icons (`SpaceEvenly`): Folder picker (`📁`), Rescan (`🔄`), Play/Pause (`⏸️`/`▶️`), and Filter funnel (`ic_filter.png`).
  - Rescan Popup: Dedicated dialog featuring exclusively 2 buttons: `1. Merge With EarlierScans` and `2. Fresh Scan` (never prompts for folder selection).
  - Hidden Keepalive WebView: Embedded background WebView / iframe refreshing Cloudflare health endpoint every 10 minutes to maintain serverless connectivity.
- Post-Scan Card-Based Layout, Inline Editing, Multi-Document Sharing, Moving & Permanent Deletion:
  - Clutter-Free Card-Based UI: Once processing completes, transitions from the table to an uncluttered `ElevatedCard` list.
  - Clickable Name Link: Original file name rendered as an underlined, clickable link opening the physical document in the system viewer.
  - In-line Virtual Name Editing: Proposed name shown in an `OutlinedTextField`; user edits save automatically on blur / focus loss.
  - Dedicated Permanent Controls: Left selection checkbox and right expand icon (`ic_dropdown`) permanently visible on each card (no secret gestures).
  - Expandable 3-Tier AI Diagnostic Inspector: Tapping expand displays a complete 3-tier inspection panel (Tier 1: Phone request & OCR text snippet, Tier 2: Cloudflare gateway status & rate limiting, Tier 3: AI execution details or exact Gemini failure diagnostic including Google HTTP error code, status, and server error message, plus on-device fallback reasoning).
  - Universal Sharing: Selecting cards enables Share (`📤 Share (${count})`) opening the Android share sheet (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) with virtual file names.
  - 20-File Export Limit & Notice: Share button disabled when 0 or > 20 cards are selected; displays clean notice *"At a time only 20 files can be exported."* (no intrusive Max 20 badge in button bar).
  - Atomic Multi-Document Moving with Custom Folder Creation: A dedicated Move button (`📦 Move (${count})`) is positioned beside the Share button, enabled when cards are selected. Tapping Move defaults the target destination to `/storage/emulated/0/Download/SmartDocsTransferd/[Date +_+ Time stamp]` (format `DDMMYY_hh:mm:ss`), automatically created if not present. Allows one-tap immediate move with "Move Now" or custom folder selection via SAF. The operation atomically moves selected physical documents to the destination under their virtual names, updates file created/updated date stamps to the extracted date, updates the app's persistent storage metadata (`PersistentMetadataManager`), and rolls back completely if any failure occurs. Moved files in `SmartDocsTransferd` are excluded from subsequent scans.
  - Red-Colored Permanent Deletion & Storage Sync: A dedicated red-colored button/icon (`🗑️ (${count})`) is positioned in the action bar, enabled whenever cards are selected. Tapping it displays an explicit confirmation dialog stating *"Selected items will be permanently deleted."* with Confirm and Cancel choices. Confirming permanently deletes physical files from device storage (`File.delete()` / `DocumentFile.delete()`) and synchronizes the app's persistent metadata storage (`PersistentMetadataManager`) in real-time.
  - Select All / Deselect All: Dedicated header toggle to select or deselect all visible cards at once.
  - Strict Duplicate & Non-Taxonomy Exclusion: Duplicates (`status == "Duplicate"`) and files failing taxonomy criteria (uncategorized, unclassified, `Other Document`, or errors) never generate cards.
- Post-Scan Auto-Hiding & Universal Search:
  - Once scanning and processing complete for all files (`allProcessed`), automatically filters out uncategorized items from view, displaying exclusively categorized files.
  - Direct Metadata Search (Pure String & Date Matching): Searches match user queries directly against received document metadata fields (`name`, `category`, `proposed`, `custom`, `extractedText`, `responseReceived`, `promptSent`, `modified`). Taxonomy classification rules are strictly excluded from search evaluation.
  - Dynamic search bar with universal date filtering (Date Issued vs Date Created).
  - Universal Date Parser: Accepts any date format (Day-Month-Year, Month-Day-Year, ISO 8601, and written alphanumeric formats like `1 May 2026`, `May 1 2026`, `2026-05-01`).
  - Substring & word matching across all metadata fields.
  - Interactive live search auto-suggestions dropdown sourced 100% from actual scanned document metadata (zero static keyword lists).
- Persistent External Metadata Storage Across Uninstalls:
  - Persists scan metadata JSON to public mobile storage (`Documents/SmartDocManager/smartdoc_metadata.json` or custom chosen folder via SAF tree picker).
  - Automatically updates latest metadata after every scan.
  - Remembers chosen location.
  - On launch/re-install, cross-checks files against device storage, purging missing items and restoring valid metadata to skip re-scanning.
- External File Store & Storage Access Framework (SAF) Provider (Points ##8.2 – ##8.5):
  - Registers intent filters for `ACTION_GET_CONTENT`, `ACTION_OPEN_DOCUMENT`, and `ACTION_PICK` in `AndroidManifest.xml`.
  - Implements `SmartDocumentsProvider` (`android.content.action.DOCUMENTS_PROVIDER`) so SmartDocManager appears directly in the system Files / SAF root sidebar alongside Google Drive, Downloads, and Gallery.
  - Tapping any row returns that single file immediately with its proposed virtual name to the calling app (Gmail, WhatsApp, browser).
  - Checkboxes at the beginning of rows enable multi-file selection; checking boxes activates a prominent **`Done (${count})`** button in the top bar.
  - Selected files are prepared via `DocExportHelper` in cache storage under their proposed names so target applications see the virtual name as `_display_name` while storage files remain completely unaltered.

### 6. Windows Desktop User Interface & Workflow:

- Screen 1: App launch displays ONLY the login screen with "Sign in with Google".
- Screen 2: Top header shows user email badge with a "Log out" button on the top-right.
- Selecting a folder triggers immediate automatic scanning (PDFs up to 40 pages and images) and renders the Document Library table immediately.
- Automatic Row-by-Row Processing:
  - Background analysis initiates automatically.
  - While a file is being analyzed, its individual row in the `Processed` column renders an animated progress bar (`[███████░░] 70% Processing…`) directly inside that cell.
  - Upon completion of each document, its cell updates to `✅ True`, the recognized category is displayed, and the proposed name is filled.
- Table Columns: `File Name` (clickable to open in OS default viewer), `Type` (PDF/Image), `Processed`, `Category`, and `Proposed Name` (in-line editable with a "Save Name" button).
- Expandable Row Drawer displaying Provider Used badge, decision basis/evidence, and full JSON payload.
- Filters: Dual filtering by Category and Date preset.
- Rescan Dialog: "Scan Again" offers merging vs clear cache.
- Dedicated desktop launcher (`windows/desktop_launcher.py`) that runs headlessly and launches an isolated native desktop app window (`msedge.exe --app=...`) without browser navigation bars or tabs.

### 7. Automated Setup, Android Signing & SHA-1 Generation:

- Provide an automated PowerShell script (`scripts/20-setup-signing-and-sha1.ps1`) and master controller (`dev.ps1`) that:
  - Automatically provisions Python venv, installs requirements.txt, and auto-detects Android SDK to create local.properties.
  - Generates `keystores/debug.keystore` and a self-signed `keystores/smartdocmanager-release.jks` (2048-bit RSA, valid for 10,000 days).
  - Automatically extracts the DEBUG SHA-1 and RELEASE SHA-1 certificate fingerprints via keytool and saves them into `signing-fingerprints.txt` with Google Cloud Console registration instructions.
  - `build.gradle.kts` and `scripts/11-build-android-release.ps1` autodiscover `keystores/smartdocmanager-release.jks` with default credentials (`SmartDoc@Release2026`) so release builds never silently skip.
  - Compiles both `output/android/SmartDocManager-debug.apk` and signed `output/android/SmartDocManager-release.apk` with incremented `versionCode = 2` / `versionName = "0.2"` so they install seamlessly as updates.
  - Compiles Windows standalone directory and Windows Setup Wizard (`SmartDocManager_Setup.exe`) via `scripts/build-installer.ps1`.

### 8. Git Ignore Standards:

- Include comprehensive `.gitignore` rules protecting `keystores/` (`*.jks`, `*.keystore`), `signing-fingerprints.txt`, `local.properties`, `.env`, `*.local.json`, build outputs (`output/`, `build/`, `dist/`), desktop artifacts (`*.exe`, `*.lnk`, `*.spec`), local databases (`*.sqlite3`, `.smartdocmanager/`), `node_modules/`, and diagnostic logs.

### 9. Test Automation:

- Maintain full test suites: 73 Windows pytest cases (testing core, library, full 519-category Indian taxonomy rules, conflict resolution via maximum-identifier rule, filename exclusion, hosted client, Google login flows, search, and clear buttons) and 23 Cloudflare Worker tests (testing token broker endpoint, token validation, allowlist, failover, 429 strict handling, and rate limiting).
```

---

## 10. Ongoing Maintenance & Synchronization Protocol

Whenever a bug fix is applied, a new feature is added, or an architectural improvement is made, adhere strictly to this three-step synchronization protocol:

1. **Update Code Generation & Readiness Scripts:**
   - Update `scripts/04-create-scaffold.ps1` if new directories, config templates, or specification files are introduced.
   - Update `scripts/05-verify-readiness.ps1` to assert the presence of all required core modules (e.g., `windows/smartdoc/indian_taxonomy.py`).
   - Execute `scripts/19-create-clean-archive.ps1` (or `dev.ps1 -Action Archive`) to generate an updated clean distribution archive (`SmartDocManager-source-<timestamp>.zip`).
2. **Synchronize Documentation & Recreation Prompt:**
   - Update `README.md` architecture diagrams, repository trees, test counts, and workflow guides.
   - Update **Section 5 (Completed Application Features & Workflows)** and **Section 6 (Compilation, Build & Export Guide)** to reflect all current features and commands.
   - Update **Section 9 (Complete Project Recreation Prompt)** so that any AI coding assistant or engineer reading the prompt will generate the exact, current, bug-free codebase from scratch.
3. **Execute Full Pre-Commit Test Verification:**
   - Run Windows pytest suite: `powershell -ExecutionPolicy Bypass -File scripts\07-test-windows.ps1` (73 tests passing).
   - Run Cloudflare Worker test suite: `npm test --prefix hosted-backend/cloudflare` (23 tests passing).
