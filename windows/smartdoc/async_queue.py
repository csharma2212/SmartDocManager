"""Asynchronous sequential worker for Gemini 2.5 Flash document classification."""
import os, sys, time, json, threading, re
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from .hosted import classify_hosted
from .library import connect, update
from .indian_taxonomy import classify_and_name_locally, generate_proposed_name, generate_compliant_proposed_name
from .metadata import build_document_metadata

class SequentialGeminiQueue:
    """Thread-safe sequential queue that executes Gemini 2.5 Flash inference one document at a time."""
    _instance = None
    _lock = threading.Lock()

    @classmethod
    def get_instance(cls):
        with cls._lock:
            if cls._instance is None:
                cls._instance = cls()
            return cls._instance

    def __init__(self):
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="GeminiWorker")
        self.current_path = None
        self.current_future = None
        self.quota_event = None
        self.failed_paths = set()
        self.lock = threading.Lock()

    def get_quota_event(self):
        with self.lock:
            return self.quota_event

    def clear_quota_event(self):
        with self.lock:
            self.quota_event = None
            self.failed_paths.clear()

    def get_failed_paths(self):
        with self.lock:
            return set(self.failed_paths)

    def clear_failed_paths(self):
        with self.lock:
            self.failed_paths.clear()

    def is_busy(self):
        with self.lock:
            if self.current_future is None:
                return False
            return not self.current_future.done()

    def get_current_processing(self):
        with self.lock:
            if self.current_future and not self.current_future.done():
                return self.current_path
            return None

    def submit_task(self, path_str, endpoint, token, gemini_api_key=None, on_complete=None, preferred_provider='gemini'):
        """Submit a document for sequential Gemini / Groq processing. Returns True if accepted, False if busy or quota paused."""
        with self.lock:
            if self.quota_event is not None:
                return False
            if self.current_future and not self.current_future.done():
                return False
            self.current_path = path_str
            self.current_future = self.executor.submit(
                self._run_job, path_str, endpoint, token, gemini_api_key, on_complete, preferred_provider
            )
            return True

    def _run_job(self, path_str, endpoint, token, gemini_api_key, on_complete, preferred_provider='gemini'):
        target_path = Path(path_str)
        result = None
        err = None
        data = None
        start_time = time.time()
        try:
            if not target_path.exists():
                raise FileNotFoundError(f"File not found: {path_str}")
            data = target_path.read_bytes()
            if not data or len(data) == 0:
                time.sleep(0.3)
                data = target_path.read_bytes()
            if not data or len(data) == 0:
                raise ValueError(f"Document file '{target_path.name}' is empty (0 bytes on disk)")
            result = classify_hosted(
                target_path.name,
                data,
                endpoint,
                token,
                gemini_api_key=gemini_api_key,
                preferred_provider=preferred_provider
            )
        except Exception as exc:
            err = exc
        finally:
            elapsed = time.time() - start_time
            if (preferred_provider or '').lower() == 'groq':
                if 0.0 <= elapsed < 2.0:
                    time.sleep(2.0 - elapsed)
            self._on_job_completed(path_str, result, err, endpoint, data=data, preferred_provider=preferred_provider)
            if on_complete:
                try:
                    on_complete(path_str, result, err)
                except Exception:
                    pass
            with self.lock:
                self.current_path = None

    def _on_job_completed(self, path_str, result, err, endpoint, data=None, preferred_provider='gemini'):
        target_path = Path(path_str)
        ext = target_path.suffix.lower() or '.pdf'
        if data is None and target_path.exists():
            try:
                data = target_path.read_bytes()
            except Exception:
                data = b''

        # Thread-safe database connection for writing completed status
        conn = connect(None)
        try:
            prov_name = (result.get('Provider') if result else None) or ('Groq' if (preferred_provider or '').lower() == 'groq' else 'Google Gemini 2.0 Flash')
            if result and result.get('Status') == 'OK':
                with self.lock:
                    self.failed_paths.discard(path_str)
                api_cat = result.get('Category') or 'Other Document'
                api_suggested = (result.get('ProposedName') or result.get('SuggestedName') or '').strip()
                api_note = result.get('Specifics', '').strip()
                doc_kind = result.get('DocumentKind') or api_cat
                prompt_sent = result.get('PromptSent', {})
                raw_response = result.get('RawResponse', {})

                cloud_map = {
                    'Property or Rental Document': 'Rental / Lease Agreement',
                    'Legal Document': 'Rental / Lease Agreement' if any(w in (result.get('Text') or '').lower() for w in ['lease', 'rent', 'lessor', 'lessee']) else 'Legal / Contract Document',
                    'Tax Document': 'Tax Form (Form 13 / 15G / Lower TDS)' if '13' in (result.get('Text') or '') or '13' in target_path.name else 'Form 16 / TDS Certificate',
                    'Employment Document': 'EPF / Provident Fund (Form 13 / UAN)' if any(w in (result.get('Text') or '').lower() for w in ['epf', 'provident', 'uan', 'form 13']) else 'Salary Slip / Payslip',
                }
                cat = cloud_map.get(api_cat, api_cat)

                from .indian_taxonomy import is_compliant_proposed_name
                if api_suggested and is_compliant_proposed_name(api_suggested):
                    proposed = api_suggested
                else:
                    doc_type = result.get('DocumentType') or result.get('DocumentKind') or cat
                    meta = result.get('Metadata', {}) if isinstance(result.get('Metadata'), dict) else {}
                    issued_date = result.get('IssuedDate') or meta.get('issued_date') or ''
                    proposed = generate_compliant_proposed_name(cat, doc_type, issued_date, target_path.name, result.get('Text', ''))

                specifics_obj = {
                    'api_name': prov_name,
                    'endpoint': endpoint,
                    'provider': prov_name,
                    'document_kind': doc_kind,
                    'category': cat,
                    'proposed_name': proposed,
                    'basis': api_note or f'Classified by {prov_name}',
                    'prompt_sent': prompt_sent,
                    'raw_response': raw_response,
                    'data_received': {
                        'provider': prov_name,
                        'document_kind': doc_kind,
                        'category': cat,
                        'proposed_name': proposed,
                        'specifics': api_note
                    },
                    'raw_json': raw_response or result.get('ApiPayload', {}),
                    'metadata': result.get('Metadata', {}) if result else {}
                }
                extra_meta = result.get('Metadata') if result else None
                if not extra_meta and isinstance(raw_response, dict):
                    extra_meta = raw_response.get('metadata')
                meta_obj = build_document_metadata(
                    data,
                    filename=target_path.name,
                    category=cat,
                    proposed_name=proposed,
                    specifics_raw=f"{api_note} {result.get('Text', '')}",
                    extra_metadata=extra_meta
                )
                update(
                    conn, target_path,
                    status='done',
                    category=cat,
                    proposed=proposed,
                    virtual_name=proposed,
                    specifics=json.dumps(specifics_obj),
                    metadata=json.dumps(meta_obj),
                    provider=prov_name
                )
            else:
                with self.lock:
                    self.failed_paths.add(path_str)
                # No local processing: Leave document in 'pending' status so it can be reprocessed
                # when quota resets, unless a fresh scan is run or original file is deleted.
                status_desc = ""
                if result:
                    status_desc = result.get('Specifics') or result.get('Status') or 'Cloud API error'
                elif err:
                    status_desc = str(err)
                else:
                    status_desc = 'Service unavailable'

                prompt_sent = result.get('PromptSent', {}) if result else {'error': 'Execution failed before prompt dispatch'}
                raw_response = result.get('RawResponse', {}) if result else {'error': str(err or 'No response')}
                is_quota = (result and result.get('Status') == 'QUOTA_EXHAUSTED') or ('429' in str(status_desc)) or ('quota' in str(status_desc).lower())

                reset_hour = (result.get('ResetHour') if result else '') or ''
                reset_date = (result.get('ResetDate') if result else '') or ''
                if is_quota:
                    from .hosted import get_gemini_reset_info, get_groq_reset_info, mark_quota_exhausted
                    prov_key = (preferred_provider or 'gemini').lower()
                    mark_quota_exhausted(prov_key)
                    if not reset_hour or not reset_date:
                        reset_hour, reset_date = get_groq_reset_info() if prov_key == 'groq' else get_gemini_reset_info()
                    with self.lock:
                        self.quota_event = {
                            'provider': prov_key,
                            'reset_hour': reset_hour,
                            'reset_date': reset_date,
                            'path': path_str
                        }

                specifics_obj = {
                    'api_name': prov_name,
                    'endpoint': endpoint or 'Cloud AI',
                    'cloud_error': status_desc,
                    'provider': prov_name,
                    'quota_exhausted': is_quota,
                    'reset_hour': reset_hour,
                    'reset_date': reset_date,
                    'prompt_sent': prompt_sent,
                    'raw_response': raw_response,
                    'data_received': {
                        'provider': prov_name,
                        'cloud_status': status_desc
                    }
                }
                update(
                    conn, target_path,
                    status='pending',
                    specifics=json.dumps(specifics_obj),
                    provider=prov_name
                )
        finally:
            try:
                conn.close()
            except Exception:
                pass
