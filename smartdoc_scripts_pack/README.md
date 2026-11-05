# SmartDoc — Cross-Platform Document Classification POC

> **Status:** Stage 2 Verified Implementation Candidate  
> **Platform Support:** Windows 11 Desktop (Streamlit) & Android Native APK (Kotlin, Jetpack Compose)  
> **Verification Status:** Windows Unit Tests (5/5 PASSED), Android Unit Tests (22/22 PASSED), Debug APK (BUILT & VERIFIED)

---

## Quickstart & Developer Runbook

### About This Section
This runbook is the complete, single-stop operational guide for developing, testing, building, and deploying the SmartDoc POC applications. All lifecycle tasks are automated and orchestrated through the master controller [`dev.ps1`](./dev.ps1), which coordinates 17 specialized stage scripts across environment provisioning, preflight checks, testing, bytecode compilation, APK packaging, and device deployment.

To prevent build failures caused by missing prerequisites, commands follow a **strict mandatory execution order**. Each step in the ordered list below explains what the step accomplishes and why it is required, followed immediately by the exact PowerShell command to run.

---

### Mandatory Execution Order (Lifecycle Pipeline)

Follow these steps in numerical sequence:

1. **Step 1: Provision Environment & Scaffold Project (Mandatory First Run)**  
   *What it does:* Creates the directory structure (`docs/`, `shared-spec/`, `output/`, `logs/`, `backups/`), creates the Python virtual environment (`windows/.venv/`), installs all required packages from `windows/requirements.txt` via `pip`, auto-detects your Android SDK to generate `android/local.properties`, and verifies the Android Gradle wrapper. Must be executed first on any new or repaired workspace.  
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action Setup
   ```

2. **Step 2: System Health, Tooling & Readiness Audit**  
   *What it does:* Checks storage (>= 10 GB free) and available RAM (>= 1024 MB), audits all required CLI tools (`python.exe`, `java.exe`, `git.exe`, `adb.exe`, and Android SDK path), and verifies that all core source and wrapper files exist before beginning tests or compilation.  
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action Verify
   ```

3. **Step 3: Run Full Pre-Build Test Suite (Mandatory Gate)**  
   *What it does:* Mandatory pre-build gate that verifies the shared taxonomy contract (`shared-spec/taxonomy.json`), runs all 5 Windows unit tests via pytest, and runs all 5 Android unit tests via Gradle. Prints formatted summary blocks detailing which specific tests passed and failed. Both build actions will automatically run this step first.  
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action Test
   ```

4. **Step 4: Build & Launch Windows Desktop Application**  
   *What it does:* Runs pre-checks and unit tests, compiles Python bytecode (`compileall`), validates required package imports (`streamlit`, `PIL`, `pypdfium2`, `pandas`), creates a shortcut on your Windows Desktop (`SmartDoc POC.lnk`), and boots the local Streamlit application in your default browser at `http://127.0.0.1:8501`.  
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action BuildWindows
   ```

5. **Step 5: Build, Package & Cryptographically Verify Android APK**  
   *What it does:* Runs pre-checks and unit tests, compiles Kotlin source code with the Jetpack Compose compiler (`compileDebugKotlin`), packages DEX bytecode and resources into `output/android/SmartDoc-debug.apk`, and cryptographically verifies the APK signature using Android SDK `apksigner.bat` (Scheme v2). If release signing environment variables are configured, also generates and signs `SmartDoc-release.apk`.  
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action BuildAndroid
   ```

6. **Step 6: Deploy Debug APK to Connected Android Device**  
   *What it does:* Uses `adb install -r output/android/SmartDoc-debug.apk` to install the newly built APK onto a physical Android device (e.g., Realme C63 5G) connected via USB with Developer Options and USB Debugging authorized.  
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action DeployDebug
   ```

7. **Step 7: Launch Windows Desktop App Directly (Without Rebuilding)**  
   *What it does:* Starts the Streamlit application directly using the pre-existing virtual environment on `http://127.0.0.1:8501` and opens your default browser without running recompilation or tests.  
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action LaunchWindows
   ```

---

### Command Sets for Specific Desired Outcomes

Use the targeted command sets below to quickly achieve specific development goals:

#### Outcome A: Complete First-Time Setup on a New Machine
Run this two-command sequence to prepare the environment and verify that all dependencies and unit tests pass:
```powershell
# 1. Scaffold directories, create Python venv, install packages, and initialize Gradle
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action Setup

# 2. Run all unit tests to confirm complete operational readiness
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action Test
```

#### Outcome B: Run All Tests (Both Windows & Android)
Executes shared contract checks, 5 Windows pytest cases, and 5 Android JUnit cases, displaying both test summary blocks:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action Test
```

#### Outcome C: Test Individual Platforms Separately (with Detailed Pass/Fail Blocks)

* **To run ONLY Windows unit tests (5 pytest cases):**  
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\07-test-windows.ps1 -Root .
  ```
  *Output Block Format:*
  ```text
  ======================================================================
    WINDOWS TEST RESULTS
  ======================================================================
    [PASS] test_core :: test_taxonomy (0.001s)
    [PASS] test_core :: test_reject_unknown (0.001s)
    [PASS] test_core :: test_image (0.063s)
    [PASS] test_core :: test_mock_provider (0.463s)
    [PASS] test_core :: test_api_failure_distinct (0.007s)
  ----------------------------------------------------------------------
    Final Results: 5 Executed | 5 Passed | 0 Failed | 0 Skipped
  ======================================================================
  ```

* **To run ONLY Android unit tests (5 Kotlin JUnit cases):**  
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\08-test-android.ps1 -Root .
  ```
  *Output Block Format:*
  ```text
  ======================================================================
    ANDROID TEST RESULTS
  ======================================================================
    [PASS] ClassifierTest :: categoriesAreStrict (0.053s)
    [PASS] ClassifierTest :: fallbackExists (0s)
    [PASS] ClassifierTest :: emptyModelReturnsInputError (0.026s)
    [PASS] ClassifierTest :: emptyApiKeyReturnsInputError (0.001s)
    [PASS] ClassifierTest :: allFiveCategoriesPresent (0.079s)
  ----------------------------------------------------------------------
    Final Results: 5 Executed | 5 Passed | 0 Failed | 0 Skipped
  ======================================================================
  ```

* **To verify ONLY the shared taxonomy JSON contract:**  
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\06-test-shared.ps1 -Root .
  ```

#### Outcome D: Build Both Windows & Android Apps in One Step
Executes all unit tests, compiles the Windows desktop application, creates the Desktop shortcut, assembles the Android debug APK, cryptographically validates the signature, and opens the APK output folder:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action BuildAll
```

#### Outcome E: Build & Verify Android APK Only
Runs the Android unit tests, compiles Kotlin code, packages `SmartDoc-debug.apk` in `output/android/`, verifies the APK with `apksigner`, and opens the output folder:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action BuildAndroid
```

#### Outcome F: Build & Launch Windows Desktop App Only
Runs the Windows unit tests, compiles Python bytecode, generates the Desktop shortcut (`SmartDoc POC.lnk`), and opens the application in your browser:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action BuildWindows
```

#### Outcome G: Install Debug APK onto Physical Android Device
Deploys `output/android/SmartDoc-debug.apk` directly over USB to your connected phone:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 -Action DeployDebug
```

#### Outcome H: Interactive Menu Selection
Launch the interactive controller and simply choose an action number (`1` through `9`):
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1
```

---


## Build & Verification Status

| Target / Component | Status | Verification Details |
| :--- | :--- | :--- |
| **Windows Streamlit App** | **VERIFIED & PASSING** | `windows/app.py`, `windows/smartdoc/core.py`. Verified in Python 3.12 venv. |
| **Windows Unit Tests** | **5/5 PASSED** | `windows/tests/test_core.py` executed via `pytest -q windows/tests` (100% pass). |
| **Android Kotlin App** | **VERIFIED & PASSING** | `com.smartdoc.poc.MainActivity`, `Classifier.kt`, Kotlin 2.0.21, Jetpack Compose. |
| **Android Unit Tests** | **5/5 PASSED** | `ClassifierTest.kt` executed via Gradle `testDebugUnitTest` (5/5 pass). |
| **Debug APK Output** | **BUILT & VERIFIED** | Output at `output/android/SmartDoc-debug.apk` (~8.7 MB). Cryptographically verified with Android SDK `apksigner` (v2 scheme valid). |
| **Release APK Output** | **SUPPORTED (Gated)** | Builds signed release APK when environment variables `SMARTDOC_KEYSTORE`, `SMARTDOC_STORE_PASSWORD`, `SMARTDOC_KEY_ALIAS`, `SMARTDOC_KEY_PASSWORD` are provided. |
| **Gradle Wrapper** | **BUNDLED** | Gradle 8.9 wrapper binaries verified and bundled (`gradle/wrapper/gradle-wrapper.jar`, `gradlew.bat`). |
| **Android SDK Discovery** | **AUTOMATED** | Dynamically discovered via `Resolve-AndroidSdk` in `scripts/common/Helpers.ps1` from `ANDROID_HOME`, `android/local.properties`, or `%LOCALAPPDATA%\Android\Sdk`. |

---

## Verified Dependencies & Configurations

### 1. Windows Python Stack (`windows/requirements.txt`)
* **Python Runtime:** Python 3.12 (managed automatically in `windows/.venv`)
* **Core Libraries:**
  * `streamlit` — Interactive desktop web UI running locally on port 8501.
  * `pypdfium2` — Fast native PDF page extraction and rendering without external C dependencies.
  * `pillow` — Image manipulation, orientation correction, and JPEG compression.
  * `pandas` — Structured classification result handling and CSV generation.
  * `pytest` — Unit test suite execution and test assertions.

### 2. Android Stack (`android/app/build.gradle.kts`)
* **Toolchain:** JDK 17, Gradle 8.9, Android Gradle Plugin `8.7.3`, Kotlin `2.0.21`
* **Jetpack Compose Compiler:** Official Kotlin Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose:2.0.21`) with `buildFeatures { compose = true }`
* **Compose BOM:** `androidx.compose:compose-bom:2024.11.00`
* **Core Dependencies:**
  * `androidx.core:core-ktx:1.15.0`
  * `androidx.activity:activity-compose:1.9.3`
  * `androidx.compose.ui:ui`
  * `androidx.compose.material3:material3`
  * `androidx.compose.foundation:foundation`
  * `junit:junit:4.13.2` (Unit tests)
* **SDK Targets:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`, `JavaVersion.VERSION_17`

### 3. Shared Specification (`shared-spec/`)
* **Taxonomy Contract:** [`shared-spec/taxonomy.json`](./shared-spec/taxonomy.json)
* **Result Schema:** [`shared-spec/result-schema.json`](./shared-spec/result-schema.json)
* **Test Fixtures:** [`shared-spec/test-fixtures/manifest.json`](./shared-spec/test-fixtures/manifest.json)

---

## Setup & Platform Configuration Details

### Windows Setup
1. Run `dev.ps1 -Action Setup`. This invokes scaffolding (`04-create-scaffold.ps1`), creates `windows/.venv/`, and installs packages from `windows/requirements.txt`.
2. Run `dev.ps1 -Action BuildWindows`. Verifies imports and byte compilation, creates a desktop shortcut (`SmartDoc POC.lnk`), and starts the Streamlit app at `http://127.0.0.1:8501`.
3. In the Streamlit UI, enter your Gemini API key (never stored plaintext on disk).

### Android CLI Setup (Without Android Studio)
* **Prerequisites:** JDK 17, Android SDK command-line tools, SDK platform 35, build-tools 35, and platform-tools.
* **SDK Location:** The project automatically detects the SDK if located at `%LOCALAPPDATA%\Android\Sdk` or set via `ANDROID_HOME`/`ANDROID_SDK_ROOT`. During setup, `android/local.properties` is automatically generated if missing.
* **USB Debugging:** Enable Developer Options and USB Debugging on your phone. Connect via USB and authorize the computer before running `dev.ps1 -Action DeployDebug`.

### Android Release Signing
To generate signed release APKs, set the following environment variables in your terminal before building:
* `SMARTDOC_KEYSTORE` — Absolute path to your `.jks` or `.keystore` file.
* `SMARTDOC_STORE_PASSWORD` — Keystore password.
* `SMARTDOC_KEY_ALIAS` — Key alias.
* `SMARTDOC_KEY_PASSWORD` — Key password.  
*Never paste keystores or passwords into chat or commit them to source control.*

---

## Changelog & Revision History

### Stage 2 Baseline Implementation
* Added Windows Streamlit document classification app (`windows/app.py`, `windows/smartdoc/core.py`) with mocked unit tests (`windows/tests/test_core.py`).
* Added Android Kotlin app (`MainActivity.kt`, `Classifier.kt`) with PDF/image ingestion, Gemini 2.5 Flash API client, and unit tests (`ClassifierTest.kt`).
* Added master workflow controller (`dev.ps1`) and task-specific automation stages (`scripts/01` to `scripts/17`).
* Added shared classification contract (`shared-spec/taxonomy.json` and `shared-spec/result-schema.json`).

### Hotfix 02 — Setup & Scaffolding Sequence
* Fixed setup sequence to scaffold directories before running dependency installations.
* Added virtual environment verification and package import tests to stage 05.

### Hotfix 04 — Gradle Wrapper Verification
* Added standalone Gradle wrapper generation stage (`scripts/16-init-gradle-wrapper.ps1`).
* Added Gradle wrapper component verification before Android test and build stages.

### Hotfix 05 — Build & Recreation Stabilization
* **Kotlin 2.0+ Compose Compiler:** Integrated `org.jetbrains.kotlin.plugin.compose:2.0.21` and `buildFeatures { compose = true }` in `android/app/build.gradle.kts`, fixing the `CompositionLocal.getCurrent()` IR compiler crash.
* **Centralized SDK Discovery:** Added `Resolve-AndroidSdk` to `scripts/common/Helpers.ps1` with fallback to `android/local.properties` and local AppData.
* **Scaffold Auto-Generation:** Updated `scripts/04-create-scaffold.ps1` to automatically recreate `android/local.properties` if missing.
* **Resilient Artifact Verification:** Updated `scripts/12-verify-artifacts.ps1` and `14-open-android-output.ps1` to dynamically verify available APKs without failing when release keystores are unconfigured.
* **Documentation Unification:** Consolidated all separate markdown files (`START_HERE.md`, `DEVELOPMENT_STATUS.md`, `BUILD_REPORT.md`, `CHANGELOG.md`, `README_FIX_02.md`, `SmartDoc_POC_README_v2_2.md`) into a single verified `README.md`.

---

# SmartDoc — Revised Cross-Platform Document Classification POC

**Version:** 2.2 (finalized development specification; no application code)  
**Date:** 2026-11-05  
**Source:** `README_Original.md` — *Offline Document Classification Engine (POC Specification & Runbook)*

## 1. Executive summary and decision

Build **two applications** from one Windows 11 Home development PC: (1) a Windows desktop-accessible Streamlit application and (2) a native Android APK for Realme C63 5G (RMX3950), Realme UI 7.0. Both target devices report 8 GB RAM and have direct internet access without a proxy. Usage is approximately once per week; zero-cost or very-low-cost operation is preferred.

**Recommended POC architecture:** local document ingestion and preprocessing + **Google Gemini Developer API free-tier cloud classification** for explicitly approved, non-sensitive or redacted test documents, without mandatory offline inference in the baseline POC; offline inference is an optional future extension. The cloud-first choice **changes** the original README's mandatory local-only privacy boundary; it must never be silently treated as equivalent. For unredacted sensitive enterprise/identity/medical/legal material, cloud processing is **disabled by default** and requires an independently approved provider, account, data-handling arrangement and organizational authorization. Free-tier availability does not constitute such approval.

No offline classification is required in v2.2. If internet is unavailable, explain that classification requires a connection. Local PDF/image preprocessing remains supported. OCR/rules and on-device VLM inference are deferred optional experiments, not prerequisites for build or acceptance.

## 2. Hardware and environment

| Device | Confirmed inputs | Open questions |
|---|---|---|
| Development/target PC | Windows 11 Home, 8 GB RAM, internet without proxy | CPU, GPU/VRAM, free SSD space, Python version |
| Android target | Realme C63 5G RMX3950, Realme UI 7.0, reported 8 GB RAM, internet without proxy | Physical RAM vs Dynamic RAM, Android API level, available storage, sustained inference performance |

Do not require CUDA, WSL2, vLLM, a dedicated GPU, or downloading an 11B model for the baseline POC. The PC builds both applications. Android compilation uses the JDK, Gradle wrapper, Android SDK command-line tools, build-tools, platform SDK and platform-tools **without installing Android Studio or an emulator**. Use the real phone via USB debugging for device tests. Build stages run sequentially on the 8 GB PC.

## 3. Original taxonomy — unchanged

Classify a single-page document, or the selected page(s) of a PDF, into exactly one of:

1. **Medical Record** — prescriptions, diagnostic/lab reports, discharge summaries, admission forms, clinical invoices.
2. **Government-issued Document** — Aadhaar, PAN, passports, voter IDs, driving licences, national IDs.
3. **Organization-issued ID Card** — employee badges, student/university IDs, corporate insurance cards, visitor passes.
4. **Legal Document** — NDAs, rental/lease contracts, sale deeds, court affidavits, powers of attorney.
5. **uncategorizable input** — unreadable, blank, corrupted, out-of-scope, or insufficiently supported classifications.

Use explicit precedence for overlapping government/employer credentials. Do not guess unreadable text or invent visual evidence. Separate *processing errors* from genuine uncategorizable results in the machine-readable output.

## 4. Functional requirements — both platforms

- Upload/select one or multiple PDFs, PNG, JPG/JPEG and WebP files.
- Inspect a single document and batch-process multiple files; Windows additionally supports user-selected local directory intake, and Android supports user-selected files/folders through the Storage Access Framework (subject to Android permissions).
- Default to first-page classification for PDFs, matching the original POC. Optionally sample additional pages in a separately configured experiment; never claim comprehensive multi-page coverage by default.
- Render PDF pages locally, correct image orientation, normalize color, and resize images within provider limits. Avoid unnecessarily large uploads.
- Show Filename, Category, **What specifics made the POC decide the category**, Processing Status, and Provider/Mode; include elapsed time and optional confidence/evidence-quality indicator without representing generated confidence as calibrated probability.
- Enforce the exact category enumeration and a structured, validated response; reject fabricated evidence and handle invalid JSON, timeouts, 429 rate limits, quota exhaustion, provider outages and cancellation.
- Show per-file progress, retry controls, error messages, and an exportable CSV of results. Never log full document images, extracted personal identifiers, or API credentials.
- No automatic renaming, deletion, or modification of source documents in this POC.
- Perform device readiness checks at setup/installation where platform mechanisms permit, at first launch, and before resource-intensive processing, following Section 8.

## 5. Architecture

### 5.1 Shared specification, not shared runtime

Maintain shared taxonomy, precedence rules, JSON schema, validation fixtures, evaluation dataset and acceptance tests. The Windows and Android implementations use separate native I/O and UI layers.

**Windows:** Streamlit UI → Python file/PDF preprocessing (`pypdfium2`, Pillow) → classification orchestration → provider adapter → response validation → pandas results/CSV.

**Android:** Kotlin + Jetpack Compose UI → Storage Access Framework + Android PDF/image rendering → classification orchestration → provider adapter → schema validation → Room/local results + CSV sharing.

**Provider adapter interface (conceptual):** `classify(document_image_or_pages, metadata, policy) -> structured_result`. Baseline backend: `GeminiCloudProvider`. `OfflineHeuristicProvider` and `LocalVLMProvider` are deferred optional extensions. These are architectural contracts, not code.

### 5.2 Cloud mode — baseline

- Use **Gemini Developer API**, with an eligible free-tier multimodal model selected from the account's *current* Google AI Studio model/rate-limit dashboard. Do not hardcode an assumed permanent free model or request quota.
- Google AI Studio is for obtaining/testing API access; the shipped Windows and Android apps call the Gemini API, not the AI Studio web interface.
- The Windows application can hold a personal API key in local environment configuration excluded from source control.
- **Do not embed a reusable Gemini API key in the Android APK.** For a personal-only POC, use a user-supplied key entered into device-protected storage with explicit warnings and key rotation; this still exposes a client-side credential and is not suitable for distribution. For a distributed or production app, use a minimal authenticated backend/proxy with server-side key storage, abuse controls, quotas, and documented hosting cost. A PC-hosted proxy would require the PC to be running and reachable; it is not assumed.
- Explicitly disclose that the document is transmitted to a third-party service. Show a clear one-time POC cloud-data notice during first setup and a persistent Cloud mode indicator. Do **not** request confirmation for each selected document or batch. Do not transmit documents until the initial notice has been acknowledged; retain this choice across sessions. This acknowledgement does not substitute for organizational authorization for sensitive/company-controlled data.
- Sensitive or company-controlled documents are excluded from the free-tier cloud POC unless organizational policy and contractual/data-processing terms explicitly permit it. Use synthetic/redacted samples for initial evaluation.

### 5.3 Offline classification — deferred, not required for v2.2

- When disconnected, show **Internet connection required for classification**. Allow file selection and local preprocessing only when safe; do not misrepresent this as completed classification.
- No offline OCR, heuristic classifier, Ollama, or on-device VLM is a baseline dependency, build gate, or acceptance criterion.
- Future offline processing may be evaluated as a separately approved extension and must be benchmarked independently.

## 6. Free and low-cost alternatives

| Alternative | Cost profile | Device impact | Privacy | POC recommendation |
|---|---|---|---|---|
| Google Gemini Developer API free tier | Free for eligible models within live quotas; may change | Low local inference RAM/storage | Documents leave device; free-tier data-use terms require review | **Preferred for synthetic/redacted weekly tests** |
| Other providers' free API tiers | Availability and limits change frequently; must verify | Low | Third-party transmission | Secondary adapter only after terms/quotas verified |
| Local lightweight OCR + rules | Free/open or device-bundled components | Low to moderate | Fully local | Optional future extension |
| Local quantized VLM | No per-call charge; installation/storage/performance cost | Potentially substantial | Fully local | Optional benchmark, not baseline |
| Self-hosted AI on PC accessed by phone | No per-call API fee, but PC must remain powered/reachable | PC bears inference cost | Local network only if properly configured | Not baseline on 8 GB PC |
| Free hosted notebooks/community inference | Unreliable availability and privacy; runtime expiration | Low local | Often unsuitable for sensitive documents | Not recommended |

**Gemini free-tier caution (as checked 2026-11-04):** Google publishes model-specific free/paid pricing, per-project quotas, and free-tier data-use distinctions. Verify current model eligibility and live RPM/TPM/RPD in AI Studio before use. No fixed weekly quota or perpetual zero-cost guarantee is assumed. Keep the project on the free tier; do not attach billing or automatically upgrade without an explicit decision. A Google AI consumer subscription is not a substitute for API quota.

Official references:
- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/rate-limits
- https://aistudio.google.com/docs/billing

## 7. Security, privacy and data governance

The original README's objective was **documents never leave the local security perimeter**. The recommended cloud-first POC **cannot meet that original requirement**. Therefore the revised POC offers an explicit policy split:

- **Local-only handling:** files may be selected and preprocessed locally, but no classification is available without the internet in v2.2; no upload occurs while offline.
- **Approved Cloud Test Policy:** synthetic/redacted or otherwise explicitly authorized documents may be sent to Gemini; show the first-use cloud disclosure and provider; minimize retention, avoid document-content logs, and display clear privacy warnings.

Do not send unredacted Aadhaar, PAN, medical records, company documents or contracts to a free API merely because the service is free. Protect credentials, use HTTPS, apply input-size limits, remove temporary files, prevent path traversal, and never display full sensitive identifiers in logs/results. Consider redaction before any cloud transmission, recognizing that automatic redaction can fail. Do not claim regulatory compliance without a separate review.

## 8. Device readiness, storage and memory checks (mandatory on both platforms)

### 8.1 When to check

- **Windows setup / first run:** a preflight script checks available disk space, installed physical RAM, current available memory, operating-system compatibility and required software before downloading dependencies or initializing the app. Recheck when the application starts and before a batch or optional local-model operation.
- **Android installation:** Android controls package installation and may reject installation for insufficient storage; a normal APK cannot reliably run custom pre-install checks. Instead, perform checks on **first launch**, after app updates where relevant, and before resource-intensive tasks. Show installation-time requirements in the Android setup instructions and, if using a custom PC-side ADB installer, optionally run a non-destructive host-side preflight before installation.
- Recheck when the user selects **Recheck**, changes processing mode, starts a large batch, or returns after an out-of-memory/storage failure. Avoid constant polling and intrusive alerts.

### 8.2 What to measure

| Resource | Windows | Android | Notes |
|---|---|---|---|
| Installed physical RAM | OS system information | Android system APIs, subject to device reporting | Do not count Dynamic RAM / swap as physical RAM. |
| Available memory | OS memory counters | `ActivityManager.MemoryInfo` / low-memory indication | Available RAM fluctuates; it is not a guaranteed per-app allocation. |
| Free storage | Free space on installation, cache, and temp volumes | Free bytes in the app's relevant storage volume | Include download/cache and temporary PDF/image processing needs. |
| Workload requirements | Selected provider, PDF page/image sizes, batch settings | Selected provider, image sizes, batch settings | Cloud mode generally requires much less memory/storage than a local VLM. |
| Other prerequisites | Python/runtime availability, internet for cloud mode | Android API compatibility, internet for cloud mode | Distinguish network/provider errors from device resource failures. |

### 8.3 Threshold policy

- Do **not** invent a universal RAM or storage threshold merely because the devices have 8 GB RAM. Define and document **minimum free storage**, **recommended free storage**, and **minimum available-memory headroom** for Windows cloud mode and Android cloud mode. Define separate thresholds only if offline OCR/rules or a local VLM is added in a future version.
- Derive initial values from actual installed application/dependency sizes, model download size (if any), peak temporary-file size and measured peak working-set usage on the two target devices. Record these values in a versioned configuration and in the README after benchmarking.
- Until measurements are available, display **Requirements not yet verified** instead of claiming an exact minimum. Check available space against the predicted operation's footprint with a safety margin. Use Android's system low-memory signal as an additional warning rather than promising that a specific free-RAM number guarantees success.
- Use a **warning** when resources are below the recommended margin but a safe small/cloud workload may still succeed; **block only** the operation when measured/estimated requirements cannot be met safely (for example, insufficient storage to install a model or render the selected document). Allow a smaller batch, lower image resolution, or cloud mode where privacy policy permits. Never block the whole application unnecessarily.

### 8.4 Required user-facing warning and actions

Show the following exact headline when resources are insufficient or below the recommended threshold:

> **This app requires minimum space and memory for limited use**

The message beneath it must be specific and actionable, for example: *Available storage: [X] GB; required for this operation: [Y] GB. Available RAM: [A] MB; recommended headroom: [B] MB. Free at least [difference] GB of storage or reduce the workload, then tap Recheck.* Substitute measured/configured values; never show placeholders as actual measurements.

Provide **Recheck**, **View requirements**, **Reduce workload**, and **Cancel** actions as applicable. Recommend users free storage by removing unused apps or files **through OS settings**, clearing safe caches, or moving personal files after reviewing them. For memory pressure, suggest closing unneeded apps, reducing batch size, or restarting the device if appropriate. On Android, guide users to OS settings rather than claiming the app can reliably force-stop other applications. On Windows, do not kill unrelated processes automatically. Never silently uninstall apps, clear unrelated data, delete documents, or require privileged access for routine checks.

### 8.5 Acceptance tests

- Verify Windows preflight and Android first-launch/pre-operation checks on the actual target devices.
- Simulate low storage and low-memory signals; verify the exact warning headline, correct units, measured values, actionable shortage, and working **Recheck** flow.
- Verify that a cloud-mode single-file operation is not blocked by requirements intended only for an optional local VLM.
- Verify safe behavior when resource readings are unavailable or change between preflight and processing; handle allocation failures gracefully.
- Verify that no unrelated application is terminated and no user file is removed by readiness checks.

## 9. Project structure — create directly inside user-selected root

The user chooses an **existing project root** (including `./`, meaning the current PowerShell working directory). Do not create a nested `SmartDoc/` folder. A bootstrap script placed in the chosen root creates the following structure. Existing files are backed up with a timestamp before intentional replacement; never overwrite silently or destroy user data.

```text
<selected-project-root>/
  dev.ps1                         # master PowerShell entry point
  README.md
  README_Original.md
  README_Revised_SmartDoc_POC.md
  .gitignore
  scripts/
    01-check-resources.ps1
    02-check-dependencies.ps1
    03-install-dependencies.ps1
    04-create-scaffold.ps1
    05-verify-readiness.ps1
    06-test-shared.ps1
    07-test-windows.ps1
    08-test-android.ps1
    09-build-windows.ps1
    10-build-android-debug.ps1
    11-build-android-release.ps1
    12-verify-artifacts.ps1
    13-launch-windows.ps1
    14-open-android-output.ps1
    15-deploy-android-debug.ps1   # optional, explicit selection
    common/                       # shared logging, paths, exit handling
  docs/
    architecture.md
    privacy-policy-modes.md
    evaluation-plan.md
    development-runbook.md
  shared-spec/
    taxonomy.json
    result-schema.json
    prompt-policy.md
    test-fixtures/                # synthetic/redacted only
  windows/
    app.py
    smartdoc/
    requirements.txt
    tests/
  android/
    gradlew.bat                   # Gradle wrapper; no global Gradle required
    gradle/
    settings.gradle.kts
    build.gradle.kts
    app/
    core/
    README.md
  evaluation/
    ground-truth.csv
    results/
  output/
    android/                      # both final APKs copied here
    windows/
  logs/                           # timestamped stage logs and reports
  backups/                        # timestamped pre-overwrite copies
```

The exact script filenames are normative task names; shared helpers may be added. Scripts must work with paths containing spaces, should be idempotent where feasible, and must resolve the project root rather than relying on the caller's current directory.

## 10. Automated development and validation workflow — mandatory

### 10.1 Master controller and approval

- The user runs `dev.ps1` in PowerShell. The master offers Setup/Repair, Verify, Test, Build Windows, Build Android, Build All, Launch Windows, Open APK Folder, and optional Deploy Debug APK.
- Setup accepts the existing project directory or `./`. Bootstrap creates `dev.ps1` and the `scripts/` folder **inside** that selected root. The bootstrap mechanism must be documented clearly for the first run.
- Check tool versions before installing. Request confirmation for system-level changes and respect Windows UAC and SDK license acceptance; the user's general preference to approve missing tools does not override OS prompts or license terms.
- Prefer `winget` or official installers where available; create Python `.venv` locally; use the Gradle wrapper and Android SDK command-line tools only. Do not install Android Studio, an emulator, CUDA, WSL2, or local 11B models for the baseline.
- Never store API keys or signing passwords in scripts, logs, source control, or APK source. Preserve and back up the release keystore securely outside source control.

### 10.2 Strict stage orchestration

1. **Preflight:** verify OS, physical/available RAM, storage, CPU, network reachability (when needed), toolchain versions, and expected workload footprint. Show the Section 8 warning and Recheck. Block only unsafe operations.
2. **Dependencies:** detect and install/repair missing tools with approval; recheck their executable functionality and compatible versions.
3. **Scaffolding:** create directories and files directly under the chosen root; make timestamped backups before overwriting modified files.
4. **Readiness:** verify mandatory files, imports, Java, Android SDK licenses/platform/build-tools, Gradle wrapper, provider configuration (without revealing secrets), and signing configuration. Distinguish optional from blocking requirements.
5. **Pre-build tests:** run shared schema/contract tests, Windows module tests, and Android module/unit tests as applicable. Mock Gemini responses in every normal build. Any mandatory failure **stops the workflow before compilation**.
6. **Windows build/verification:** validate Python dependencies, imports, packaging/launcher and Streamlit startup. Streamlit is a local web application, not an EXE; provide a desktop shortcut or launcher that starts the local server and opens the default browser.
7. **Android builds:** run Gradle command-line builds sequentially for debug and release; use a persistent user-owned release keystore created in a one-time guided setup. Produce `SmartDoc-debug.apk` and `SmartDoc-release.apk` in `output/android/`.
8. **Post-build verification:** check both APKs exist, are nonempty, have the expected application ID/version, are signed with the correct debug/release certificates, and pass available artifact checks. Run any integration/smoke checks that require compilation. Report any device-only checks not run.
9. **Delivery:** after success, launch Windows Streamlit in the default browser and open Windows Explorer at `output/android/`. Optionally deploy debug APK over ADB to the Realme only when explicitly requested, connected, and all required tests pass.

Each script emits a clear stage name, duration, success/failure state, actionable error, and nonzero exit code on failure. The master records a timestamped report (tool versions, test counts, artifacts, checksums, skipped tests and reasons, overall result). On mandatory failure, stop downstream stages, print **BUILD FAILED**, and identify the failing stage and log. Fix the underlying script/source and update README/runbook if the documented process changes; rerun the workflow from preflight. Do not automatically conceal errors or skip failed tests. On full success print **BUILD SUCCEEDED**.

### 10.3 Testing and operational details

- Required pre-build tests: taxonomy, schema, invalid JSON, ambiguous IDs, uncategorizable fallback, processing status, PDF/image handling, batch order, ETA updating after each file, cancellation, resource warnings/Recheck, provider failure handling, first-use cloud notice, credential redaction, Windows modules, and Android modules.
- API/network errors (timeout, authentication, 429, outage) immediately yield category `uncategorizable input` **plus a distinct error status**; no automatic retry in the baseline. A genuine unclassifiable document uses the same category with status `UNCATEGORIZABLE`. Explanations must not falsely claim visual evidence on API failure. Manual retry may be offered.
- Batch processing accepts multiple documents but classifies one at a time. Show completed/total, current filename, elapsed time, and an **approximate** remaining time recalculated after each file.
- Live Gemini integration tests are a separate manually invoked action, using synthetic/redacted fixtures and a currently eligible free-tier project; never make billable or live calls a mandatory part of every build.
- On an 8 GB PC, avoid parallel Windows/Android builds and Android emulators. USB real-device testing is the primary Android device validation path. The APK build itself does not require a connected phone.
- Preserve Windows settings/results and Android app data across compatible updates; maintain Android application ID and signing identity. Document backup/migration procedures for schema changes.

## 11. Evaluation and acceptance criteria

**Functional gates:**
- Both platforms perform resource readiness checks at supported lifecycle points and show the exact minimum-space-and-memory warning with actionable remediation and Recheck, as defined in Section 8.
- Both apps accept supported formats, process a single document and batches, show the exact category and evidence column, and export results.
- Invalid/blank/out-of-scope files use the specified fallback; transport/model failures use a separate error status.
- Cloud requests require first-use disclosure acknowledgement, without per-file prompts; no document upload occurs when offline.
- Invalid model output never creates an out-of-taxonomy category; explanations do not assert unobserved evidence.
- Pre-build tests gate compilation; post-build verification gates delivery. Both signed Android APK variants are generated without Android Studio; Windows Streamlit launch and shortcut are verified.

**Proposed POC targets, to validate rather than guarantee:**
- Build an evaluation corpus of at least 20 samples per business class plus at least 20 uncategorizable samples (minimum 100 total), using synthetic/redacted documents.
- Report per-class precision/recall, confusion matrix, false-positive rate on out-of-scope samples, JSON validity, processing errors, and end-to-end latency on both devices.
- Aim for >=85% macro-F1 on this limited corpus for cloud mode, with no guarantee until measured; do not claim offline-classification results for the v2.2 baseline.
- Test batches of at least 10 files without UI crashes; enforce bounded concurrency and memory use.
- Measure typical and worst-case latency rather than promising an unmeasured response time.
- Verify zero billable usage for tests run under a confirmed eligible free-tier project, without assuming this remains true in the future.

## 12. Failure modes and remediation

- **No internet:** explain classification requires internet; do not silently upload or attempt offline classification.
- **401/403 credentials/access:** show actionable key/project/region error without exposing secrets.
- **429 quota:** mark `uncategorizable input` with distinct `RATE_LIMITED` processing status, show quota guidance and optional manual retry; never bypass rate limits.
- **API pricing/availability changes:** provider configuration and live preflight check; fail closed on non-free or unapproved mode.
- **Large PDF or image:** page limits, size limits, resize; show unsupported/oversize error.
- **Malformed model JSON:** structured-output constraints where supported, strict parsing and validation; mark fallback category plus `INVALID_RESPONSE` status without automatic retry.
- **Ambiguous ID:** explicit precedence, evidence requirement, otherwise fallback.
- **Low resolution:** do not invent text; fallback when evidence is inadequate.
- **Android memory pressure:** bounded image buffers, one-at-a-time inference requests, lifecycle-aware cancellation; show the Section 8 warning and recovery options.
- **App shutdown during batch:** preserve completed result metadata locally, permit explicit restart/resume.
- **API key exposure:** revoke/rotate compromised keys; never commit or embed a distribution key.

## 13. Out of scope for baseline POC

- Guaranteed fully offline VLM-level accuracy on the Realme C63 5G.
- Production cloud processing of unredacted sensitive or enterprise data without approval.
- Auto-renaming, deleting, moving or syncing source documents.
- Always-on PC server or mandatory LAN connection between phone and PC.
- Guaranteed permanent zero-cost Gemini access.
- Production user authentication, payments, cloud storage or public app distribution.

## 14. Remaining implementation prerequisites (not unresolved architecture decisions)

The following facts must be collected by the preflight/setup scripts or supplied by the user. They **do not block finalizing this specification**, but some block an actual build or live test until resolved:

1. **PC storage and CPU:** inspect free SSD space, CPU architecture, installed JDK/Python and existing Android SDK; install only required components after approval. No fixed storage minimum is asserted until measured.
2. **Phone configuration:** verify physical vs Dynamic RAM, Android API level, free storage, USB debugging and ADB authorization; do not assume reported RAM is all physical.
3. **Gemini API project:** user obtains an API key and confirms currently free eligible model, account quotas and terms. Never silently enable billing or claim perpetual free usage.
4. **Test data:** use synthetic/redacted or explicitly authorized documents. The POC's no-per-file-confirmation preference does not authorize uploading confidential or enterprise-controlled content.
5. **Release signing:** securely create/retain a persistent keystore and passwords; determine application ID/package name and versioning at implementation time.
6. **Actual performance thresholds:** benchmark both devices, then record memory/storage limits, warnings and time estimates. Until then, display unverified requirements rather than fabricated numbers.
7. **Android real-device validation:** phone and USB debugging must be available for physical-device tests. APK compilation can still complete without a connected phone, but the final report must distinguish unrun device tests from passed tests.
8. **Provider-key architecture:** personal sideload-only Android POC uses a user-entered key stored with platform protections and a clear extraction-risk warning. Distribution to other users would require a separately designed authenticated backend.

## 15. Summary and approval baseline

**Approved v2.2 baseline:** two independent internet-connected apps; Windows Streamlit and native Android; Gemini free-tier candidate; cloud disclosure once at setup without repeated prompts; no offline classification requirement; strict five-category taxonomy with separate processing status; sequential batches with updated ETA; device resource checks; one PowerShell master and task-specific scripts; no Android Studio; mandatory tests before builds; signed debug/release APK outputs; Windows auto-launch and shortcut; stop-on-failure reports; and preserved update data. No application code is part of this document.

The cloud baseline intentionally changes the original local-only privacy promise. Use only synthetic/redacted or otherwise approved documents for free-tier testing, and revisit provider terms, quotas and security architecture before production use.
