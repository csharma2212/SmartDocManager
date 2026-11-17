"""SmartDocManager Windows Application:
- Clean Google Sign-In Gate
- Automatic recursive scan and instant background processing
- Animated progress bar illusion during document analysis
- Local Indian standard document taxonomy and date-stamped naming
- Non-destructive virtual management (original files on disk are never altered)
- Robust quota tracking across Gemini and Groq
"""
import os, sys, time, json, re
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))

import streamlit as st
import smartdoc.constants as constants

DEFAULT_BACKEND_URL = getattr(constants, 'DEFAULT_BACKEND_URL', 'https://smartdocmanager-api.csharma2212.workers.dev')
GOOGLE_DESKTOP_CLIENT_ID = getattr(constants, 'GOOGLE_DESKTOP_CLIENT_ID', '')
GOOGLE_DESKTOP_CLIENT_SECRET = getattr(constants, 'GOOGLE_DESKTOP_CLIENT_SECRET', '')

from smartdoc.library import connect, scan, rows, update, rename_and_trash, date_match, short_name
from smartdoc.indian_taxonomy import generate_proposed_name, generate_compliant_proposed_name, is_compliant_proposed_name
from smartdoc.google_login import start_login
from smartdoc.async_queue import SequentialGeminiQueue
from smartdoc.metadata import build_document_metadata, matches_query, get_search_suggestions

_icon_path = Path(__file__).resolve().parent / 'assets' / 'icon.png'
_page_icon = str(_icon_path) if _icon_path.exists() else '📄'
st.set_page_config(
    page_title='SmartDocManager',
    layout='wide',
    page_icon=_page_icon,
    initial_sidebar_state='collapsed',
)

# Clean Desktop Application styling & aggressive text selection CSS
st.markdown("""
<style>
/* Clean desktop application appearance */
#MainMenu { visibility: hidden !important; }
header { visibility: hidden !important; }
footer { visibility: hidden !important; }
div[data-testid="stToolbar"] { visibility: hidden !important; }
div[data-testid="stDecoration"] { visibility: hidden !important; }
div[data-testid="stStatusWidget"] { visibility: hidden !important; }

/* Remove left panel / sidebar completely */
[data-testid="stSidebar"],
[data-testid="stSidebarCollapsedControl"],
section[data-testid="stSidebar"] {
    display: none !important;
}

/* Universal text selection across all components, containers, markdown, and code */
*, *::before, *::after {
    -webkit-user-select: text !important;
    -moz-user-select: text !important;
    -ms-user-select: text !important;
    user-select: text !important;
}

/* Ensure interactive controls maintain proper pointer cursor */
button, [role="button"], input[type="button"], input[type="submit"], [data-testid="stButton"] button {
    cursor: pointer !important;
}

/* Ensure text inputs and code blocks maintain proper text cursor and styling */
input[type="text"], textarea, pre, code, .stTextInput input {
    cursor: text !important;
    font-family: Consolas, "Liberation Mono", Menlo, Courier, monospace !important;
}

/* Vibrant, prominent text selection highlight */
::selection {
    background-color: #2563eb !important;
    color: #ffffff !important;
}
::-moz-selection {
    background-color: #2563eb !important;
    color: #ffffff !important;
}
</style>
""", unsafe_allow_html=True)

# Initialize session state variables
if 'root' not in st.session_state: st.session_state.root = ''
if 'folder_path_input' not in st.session_state: st.session_state.folder_path_input = ''
if 'gemini_api_key' not in st.session_state: st.session_state.gemini_api_key = os.environ.get('GEMINI_API_KEY', '')
if 'scan_scope' not in st.session_state: st.session_state.scan_scope = ''
if 'run_queue' not in st.session_state: st.session_state.run_queue = False
if 'processing_file' not in st.session_state: st.session_state.processing_file = ''
if 'quota_exhausted' not in st.session_state: st.session_state.quota_exhausted = False
if 'google_login' not in st.session_state: st.session_state.google_login = {}
if 'show_duplicates' not in st.session_state: st.session_state.show_duplicates = False
if 'search_query' not in st.session_state: st.session_state.search_query = ''

auth = st.session_state.google_login
if auth.get('expires', 0) <= int(time.time()) + 60:
    auth.pop('token', None)

token = auth.get('token', '')
endpoint = os.environ.get('SMARTDOC_BACKEND_URL') or DEFAULT_BACKEND_URL

# Load client credentials directly from constants / .env
client_id = GOOGLE_DESKTOP_CLIENT_ID
client_secret = GOOGLE_DESKTOP_CLIENT_SECRET
is_configured = bool(client_id) and not client_id.startswith("REPLACE_WITH_")

# ==========================================
# SCREEN 1: LOGIN GATE (If not authenticated)
# ==========================================
if not token:
    st.markdown("<br><br>", unsafe_allow_html=True)
    c1, c2, c3 = st.columns([1, 2, 1])
    with c2:
        st.markdown("<h1 style='text-align: center;'>📄 SmartDocManager</h1>", unsafe_allow_html=True)
        st.markdown("<p style='text-align: center; color: gray;'>Intelligent Document Classification & Virtual Management (Indian Standards)</p>", unsafe_allow_html=True)
        st.divider()
        
        st.markdown("### Sign In to Continue")
        st.write("Please sign in with your authorized Google account to access document processing.")
        
        if is_configured:
            if st.button('Sign in with Google', type='primary', use_container_width=True):
                try:
                    start_login(client_id, auth, backend_url=endpoint, client_secret=client_secret)
                    st.rerun()
                except ValueError as exc:
                    st.error(str(exc))
                
        if auth.get('state') == 'waiting':
            st.info('🌐 Browser opened for Google Sign-In. Waiting for authentication...')
            time.sleep(1)
            st.rerun()
                
        if auth.get('error'):
            st.error(auth['error'])
            
        st.caption("🔒 All document processing credentials remain secured. Original disk files are protected.")
    st.stop()  # Stop execution so nothing else is shown until logged in

_html2canvas_cache = None

def get_html2canvas_code() -> str:
    global _html2canvas_cache
    if _html2canvas_cache is None:
        js_path = Path(__file__).resolve().parent / "assets" / "html2canvas.min.js"
        if js_path.exists():
            try:
                _html2canvas_cache = js_path.read_text(encoding="utf-8")
            except Exception:
                _html2canvas_cache = ""
    return _html2canvas_cache or ""

def render_full_screenshot_button():
    """Renders a button that smoothly scrolls from top to bottom and captures a complete full-page screenshot."""
    import streamlit.components.v1 as components
    js_code = get_html2canvas_code()

    html_markup = f"""
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="utf-8">
    <style>
    * {{ margin: 0; padding: 0; box-sizing: border-box; }}
    body {{ background: transparent; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }}
    .screenshot-btn {{
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        font-weight: 500;
        padding: 0.45rem 0.85rem;
        border-radius: 0.5rem;
        font-size: 0.85rem;
        line-height: 1.4;
        color: #f8fafc;
        background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
        border: 1px solid #475569;
        cursor: pointer;
        transition: all 0.2s ease;
        width: 100%;
        box-shadow: 0 1px 3px rgba(0,0,0,0.3);
    }}
    .screenshot-btn:hover {{
        background: linear-gradient(135deg, #334155 0%, #475569 100%);
        border-color: #38bdf8;
        color: #ffffff;
        box-shadow: 0 2px 6px rgba(56, 189, 248, 0.25);
    }}
    .screenshot-btn:active {{
        transform: translateY(1px);
    }}
    .screenshot-btn:disabled {{
        opacity: 0.75;
        cursor: wait;
    }}
    </style>
    </head>
    <body>
    <button id="capture-btn" class="screenshot-btn" onclick="takeFullScreenshot()">
        📸 Capture Full Screenshot
    </button>
    <script>
    {js_code}

    async function takeFullScreenshot() {{
        const btn = document.getElementById('capture-btn');
        btn.disabled = true;

        const parentDoc = window.parent.document;
        const parentWin = window.parent;

        const scrollContainer = parentDoc.querySelector('[data-testid="stAppViewContainer"]') || 
                               parentDoc.querySelector('section.main') || 
                               parentDoc.documentElement;

        const targetElement = parentDoc.querySelector('[data-testid="stAppViewContainer"]') || 
                              parentDoc.querySelector('.stApp') || 
                              parentDoc.body;

        const totalHeight = Math.max(
            scrollContainer.scrollHeight,
            targetElement.scrollHeight,
            parentDoc.body.scrollHeight,
            parentDoc.documentElement.scrollHeight
        );

        // Step 1: Smoothly scroll down from top to bottom
        const steps = 6;
        for (let i = 1; i <= steps; i++) {{
            const scrollToY = Math.min(totalHeight, Math.round((totalHeight / steps) * i));
            scrollContainer.scrollTo({{ top: scrollToY, behavior: 'smooth' }});
            btn.innerHTML = `🔄 Scrolling... ${{Math.round((i / steps) * 100)}}%`;
            await new Promise(r => setTimeout(r, 120));
        }}

        // Brief pause at the bottom to ensure all dynamic elements are rendered
        await new Promise(r => setTimeout(r, 250));

        // Step 2: Scroll back to the top for clean, unshifted capture
        btn.innerHTML = '📸 Capturing interface...';
        scrollContainer.scrollTo({{ top: 0, behavior: 'instant' }});
        await new Promise(r => setTimeout(r, 200));

        try {{
            // Step 3: Capture full-height canvas using html2canvas
            const canvas = await html2canvas(targetElement, {{
                height: totalHeight,
                windowHeight: totalHeight,
                width: targetElement.scrollWidth,
                windowWidth: targetElement.scrollWidth,
                scrollY: 0,
                scrollX: 0,
                useCORS: true,
                allowTaint: true,
                logging: false,
                scale: 1.5,
                backgroundColor: parentWin.getComputedStyle(parentDoc.body).backgroundColor || '#0e1117',
                ignoreElements: (el) => {{
                    return el.tagName === 'IFRAME' && el.srcdoc && el.srcdoc.includes('capture-btn');
                }}
            }});

            // Step 4: Convert canvas to PNG blob
            canvas.toBlob(async (blob) => {{
                if (!blob) {{
                    throw new Error("Canvas to blob conversion failed");
                }}

                const now = new Date();
                const pad = (n) => String(n).padStart(2, '0');
                const timestamp = `${{now.getFullYear()}}-${{pad(now.getMonth()+1)}}-${{pad(now.getDate())}}_${{pad(now.getHours())}}-${{pad(now.getMinutes())}}-${{pad(now.getSeconds())}}`;
                const filename = `SmartDocManager_FullScreenshot_${{timestamp}}.png`;

                // 1. Download file via parent document
                const a = parentDoc.createElement('a');
                a.href = parentWin.URL.createObjectURL(blob);
                a.download = filename;
                parentDoc.body.appendChild(a);
                a.click();
                setTimeout(() => {{
                    parentDoc.body.removeChild(a);
                    parentWin.URL.revokeObjectURL(a.href);
                }}, 1000);

                // 2. Attempt to copy to clipboard for instant Ctrl+V pasting
                try {{
                    if (navigator.clipboard && navigator.clipboard.write) {{
                        await navigator.clipboard.write([
                            new ClipboardItem({{ 'image/png': blob }})
                        ]);
                    }}
                }} catch (clipErr) {{
                    console.warn('Clipboard write warning:', clipErr);
                }}

                btn.innerHTML = '✅ Saved to Downloads & Clipboard!';
                setTimeout(() => {{
                    btn.innerHTML = '📸 Capture Full Screenshot';
                    btn.disabled = false;
                }}, 3500);
            }}, 'image/png');

        }} catch (err) {{
            console.error('Screenshot capture error:', err);
            btn.innerHTML = '❌ Capture Failed';
            setTimeout(() => {{
                btn.innerHTML = '📸 Capture Full Screenshot';
                btn.disabled = false;
            }}, 3000);
        }}
    }}
    </script>
    </body>
    </html>
    """
    components.html(html_markup, height=45)

# ==========================================
# SCREEN 2: MAIN DASHBOARD (When Authenticated)
# ==========================================

# Top Header: App Title on left, Screenshot Button & User Account + Logout on top-right
header_col1, header_col2, header_col3 = st.columns([2.5, 1.4, 0.9], vertical_alignment="center")
with header_col1:
    st.title('📄 SmartDocManager')
    st.caption('Intelligent Document Classification & Virtual Management · Indian Standards')

with header_col2:
    st.markdown("<div style='height: 12px;'></div>", unsafe_allow_html=True)
    render_full_screenshot_button()

with header_col3:
    st.write(f"👤 **{auth.get('email', 'Connected')}**")
    if st.button('Log out', type='secondary', use_container_width=True):
        st.session_state.google_login = {}
        st.session_state.root = ''
        st.rerun()

# Hidden Keepalive WebView / iframe for Cloudflare connection (refreshes every 10 minutes)
keepalive_url = f"{endpoint.rstrip('/')}/health"
import streamlit.components.v1 as components
components.html(
    f"""
    <iframe id="cf-keepalive-frame" src="{keepalive_url}" style="display:none;width:0;height:0;border:none;"></iframe>
    <script>
        (function() {{
            var frame = document.getElementById('cf-keepalive-frame');
            if (window._cfKeepaliveTimer) clearInterval(window._cfKeepaliveTimer);
            window._cfKeepaliveTimer = setInterval(function() {{
                if (frame) {{
                    frame.src = '{keepalive_url}?_t=' + new Date().getTime();
                }}
            }}, 10 * 60 * 1000); // 10 minutes
        }})();
    </script>
    """,
    height=0,
    width=0
)

if 'cf_keepalive_worker_running' not in st.session_state:
    st.session_state.cf_keepalive_worker_running = True
    def _cf_keepalive_worker():
        import urllib.request
        while True:
            time.sleep(600)  # 10 minutes
            try:
                hdrs = {"Authorization": f"Bearer {token}"} if token else {}
                req = urllib.request.Request(keepalive_url, headers=hdrs)
                urllib.request.urlopen(req, timeout=10)
            except Exception:
                pass
    import threading
    _t = threading.Thread(target=_cf_keepalive_worker, daemon=True)
    _t.start()

st.divider()

# Primary AI Engine (Default is always Google Gemini; no UI engine selector)
if 'ai_provider' not in st.session_state:
    st.session_state.ai_provider = 'gemini'

from smartdoc.hosted import (
    get_remaining_daily_quota, get_gemini_day_key, get_groq_day_key,
    reset_provider_count, format_quota_display, get_gemini_reset_epoch, get_groq_reset_epoch,
    is_quota_exhausted, get_gemini_reset_info, get_groq_reset_info
)

# Startup / Active session sync: Check if previous quota exhaustion has passed reset clock
if 'gemini_exhausted' not in st.session_state:
    st.session_state.gemini_exhausted = False
if 'groq_exhausted' not in st.session_state:
    st.session_state.groq_exhausted = False
if 'show_restored_msg' not in st.session_state:
    st.session_state.show_restored_msg = None

cur_gemini_day = get_gemini_day_key()
cur_groq_day = get_groq_day_key()

if st.session_state.get('gemini_exhausted'):
    if st.session_state.get('gemini_exhausted_day') != cur_gemini_day:
        st.session_state.gemini_exhausted = False
        st.session_state.gemini_declined = False
        st.session_state.quota_exhausted = False
        st.session_state.ai_provider = 'gemini'
        reset_provider_count('gemini')
        st.session_state.show_restored_msg = "Google Gemini quota has been restored! Daily limit is reset to 1500 requests."
elif st.session_state.get('gemini_declined') and st.session_state.get('gemini_declined_day') != cur_gemini_day:
    st.session_state.gemini_declined = False
    st.session_state.quota_exhausted = False
    st.session_state.ai_provider = 'gemini'
    reset_provider_count('gemini')
    st.session_state.show_restored_msg = "Google Gemini quota has been restored! Daily limit is reset to 1500 requests."

if st.session_state.get('groq_exhausted'):
    if st.session_state.get('groq_exhausted_day') != cur_groq_day:
        st.session_state.groq_exhausted = False
        st.session_state.groq_acknowledged = False
        reset_provider_count('groq')
        st.session_state.show_restored_msg = "Groq quota has been restored! Limit has been reset."

quota_fmt = format_quota_display(st.session_state.ai_provider)
prov_title = "✨ Google Gemini" if st.session_state.ai_provider == 'gemini' else "⚡ Groq"
st.caption(f"🤖 **Active AI Engine:** **{prov_title}** {quota_fmt}")

@st.dialog("Quota Restored")
def show_quota_restored_dialog(msg: str):
    st.success(f"🎉 {msg}")
    if st.button("OK", type="primary", use_container_width=True, key="dlg_quota_restored_ok"):
        st.session_state.show_restored_msg = None
        st.rerun()

if st.session_state.get('show_restored_msg'):
    show_quota_restored_dialog(st.session_state.show_restored_msg)

@st.dialog("Gemini Quota Notice")
def show_gemini_quota_dialog(hour: str, date_str: str):
    st.warning(f"Gemini quota is completely utilized! It will be reset at {hour} hours on {date_str} date. Do you want me to proceed with Groq?")
    c1, c2 = st.columns(2)
    if c1.button("Yes", type="primary", use_container_width=True, key="dlg_gemini_yes"):
        gemini_queue.clear_quota_event()
        st.session_state.ai_provider = 'groq'
        st.session_state.gemini_declined = False
        st.session_state.quota_exhausted = False
        if is_quota_exhausted('groq') or st.session_state.get('groq_exhausted'):
            gh, gd = get_groq_reset_info()
            st.session_state.show_groq_quota_popup = (gh, gd)
            st.session_state.run_queue = False
        else:
            st.session_state.run_queue = True
        st.rerun()
    if c2.button("No", type="secondary", use_container_width=True, key="dlg_gemini_no"):
        gemini_queue.clear_quota_event()
        st.session_state.gemini_declined = True
        st.session_state.gemini_declined_day = get_gemini_day_key()
        st.rerun()

@st.dialog("Groq Quota Notice")
def show_groq_quota_dialog(hour: str, date_str: str):
    st.warning(f"Groq quota is completely utilized! It will be reset at {hour} hours on {date_str} date.")
    if st.button("OK", type="primary", use_container_width=True, key="dlg_groq_ok"):
        gemini_queue.clear_quota_event()
        st.session_state.ai_provider = 'gemini'
        st.session_state.groq_acknowledged = True
        st.session_state.run_queue = False
        st.rerun()

if st.session_state.get('show_groq_quota_popup'):
    gh, gd = st.session_state.pop('show_groq_quota_popup')
    show_groq_quota_dialog(gh, gd)

conn = connect(None)

def sanitize_error_text(err: str) -> str:
    if not err:
        return ""
    cleaned = str(err)
    for bad in ["Cloudflare Worker missing GEMINI_API_KEY or GROQ_API_KEY secret", "missing GEMINI_API_KEY or GROQ_API_KEY secret", "missing GEMINI_API_KEY"]:
        cleaned = cleaned.replace(bad, "Service not configured on hosted Cloudflare Worker")
    return cleaned.strip()

def parse_legacy_specifics(spec_raw: str):
    clean_err = ""
    clean_basis = spec_raw.strip()
    if "AI Note:" in clean_basis:
        parts = clean_basis.split("AI Note:", 1)
        after_note = parts[1].strip()
        if any(err_kw in after_note.lower() for err_kw in ["503", "error", "failed", "service not configured", "quota", "exception"]):
            clean_err = after_note
            clean_basis = parts[0].strip()
        else:
            clean_basis = after_note
    elif "Hosted classifier HTTP" in clean_basis:
        clean_err = "Hosted classifier HTTP 503"
        clean_basis = clean_basis.replace("Hosted classifier HTTP 503", "").strip()
    elif "503" in clean_basis:
        clean_err = "Hosted classifier HTTP 503"

    clean_basis = re.sub(r"^Inference:\s*[^\|]+\|\s*", "", clean_basis).strip()
    if clean_basis.startswith("• "):
        clean_basis = clean_basis[2:].strip()
    if not clean_basis or clean_basis in ("Hosted classifier HTTP 503", "503"):
        clean_basis = "Classified based on Indian document taxonomy patterns"

    clean_err = sanitize_error_text(clean_err)
    return clean_err, clean_basis

# Automatic one-time migration for legacy error records and plain-text specifics in database
try:
    legacy_err_rows = conn.execute(
        "SELECT path FROM files WHERE specifics LIKE '%smartdoc-classifier%' OR specifics LIKE '%Data format error%' OR specifics LIKE '%Service not configured%'"
    ).fetchall()
    for e_row in legacy_err_rows:
        conn.execute("UPDATE files SET status='pending', specifics='' WHERE path=?", (e_row['path'],))
    if legacy_err_rows:
        conn.commit()
except Exception:
    pass

try:
    bad_rows = conn.execute("SELECT path, specifics FROM files WHERE specifics LIKE '%missing GEMINI_API_KEY%'").fetchall()
    for b_row in bad_rows:
        cleaned_spec = b_row['specifics'].replace(
            "Cloudflare Worker missing GEMINI_API_KEY or GROQ_API_KEY secret",
            "Cloud AI inference service unavailable (HTTP 503)"
        )
        conn.execute("UPDATE files SET specifics=? WHERE path=?", (cleaned_spec, b_row['path']))
    if bad_rows:
        conn.commit()
except Exception:
    pass

try:
    legacy_rows = conn.execute("SELECT path, name, category, proposed, specifics, provider FROM files WHERE specifics != '' AND specifics NOT LIKE '{%'").fetchall()
    for row in legacy_rows:
        s_raw = row['specifics']
        c_err, c_basis = parse_legacy_specifics(s_raw)
        c_cat = row['category'] or 'Other Document'
        c_prop = row['proposed'] or row['name']
        migrated_obj = {
            'api_name': 'Local Indian Taxonomy Engine (Offline Fallback)' if c_err else (row['provider'] or 'Local Indian Taxonomy Engine'),
            'endpoint': endpoint if c_err else 'Local Rule Engine',
            'cloud_error': c_err,
            'provider': 'local_engine',
            'data_received': {
                'provider': 'Local Indian Taxonomy Engine (Offline Fallback)' if c_err else 'local_engine',
                'category': c_cat,
                'proposed_name': c_prop,
                'specifics': c_basis,
                'cloud_status': c_err or 'OK'
            },
            'raw_json': {
                'category': c_cat,
                'proposed_name': c_prop,
                'evidence': c_basis,
                'cloud_error': c_err or None
            },
            'basis': c_basis
        }
        conn.execute("UPDATE files SET specifics=?, provider=? WHERE path=?", (json.dumps(migrated_obj), migrated_obj['api_name'], row['path']))
    if legacy_rows:
        conn.commit()
except Exception:
    pass

def choose_folder() -> str:
    """Opens a native Windows folder selection dialog.
    Tries PowerShell FolderBrowserDialog first (modern, native, topmost),
    with fallbacks to Win32 SHBrowseForFolderW via ctypes and Tkinter.
    """
    # Strategy 1: PowerShell FolderBrowserDialog (modern, native, modal topmost)
    try:
        import subprocess
        ps_code = (
            "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; "
            "$f = New-Object System.Windows.Forms.FolderBrowserDialog; "
            "$f.Description = 'Select Document Folder to Manage'; "
            "$f.ShowNewFolderButton = $true; "
            "$topmost = New-Object System.Windows.Forms.Form; "
            "$topmost.TopMost = $true; "
            "if ($f.ShowDialog($topmost) -eq [System.Windows.Forms.DialogResult]::OK) { "
            "Write-Host -NoNewline $f.SelectedPath }"
        )
        cmd = ["powershell", "-NoProfile", "-NonInteractive", "-Command", ps_code]
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=60, creationflags=creationflags)
        folder = res.stdout.strip()
        if folder and Path(folder).is_dir():
            return str(Path(folder).resolve())
    except Exception:
        pass

    # Strategy 2: Native Win32 SHBrowseForFolderW via ctypes (zero external dependencies)
    try:
        import ctypes
        from ctypes import wintypes
        BIF_RETURNONLYFSDIRS = 0x0001
        BIF_NEWDIALOGSTYLE = 0x0040

        class BROWSEINFO(ctypes.Structure):
            _fields_ = [
                ('hwndOwner', wintypes.HWND),
                ('pidlRoot', ctypes.c_void_p),
                ('pszDisplayName', wintypes.LPWSTR),
                ('lpszTitle', wintypes.LPCWSTR),
                ('ulFlags', wintypes.UINT),
                ('lpfn', ctypes.c_void_p),
                ('lParam', wintypes.LPARAM),
                ('iImage', ctypes.c_int)
            ]

        shell32 = ctypes.windll.shell32
        ole32 = ctypes.windll.ole32
        ole32.CoInitialize(None)

        display_name = ctypes.create_unicode_buffer(260)
        bi = BROWSEINFO()
        bi.hwndOwner = 0
        bi.pidlRoot = None
        bi.pszDisplayName = ctypes.cast(display_name, wintypes.LPWSTR)
        bi.lpszTitle = "Select Document Folder to Manage"
        bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE

        pidl = shell32.SHBrowseForFolderW(ctypes.byref(bi))
        if pidl:
            path_buf = ctypes.create_unicode_buffer(260)
            shell32.SHGetPathFromIDListW(pidl, path_buf)
            ole32.CoTaskMemFree(pidl)
            ole32.CoUninitialize()
            if path_buf.value and Path(path_buf.value).is_dir():
                return str(Path(path_buf.value).resolve())
        ole32.CoUninitialize()
    except Exception:
        pass

    # Strategy 3: Tkinter fallback
    try:
        import tkinter as tk
        from tkinter import filedialog
        window = tk.Tk()
        window.withdraw()
        window.attributes('-topmost', True)
        folder = filedialog.askdirectory(title='Select Document Folder')
        window.destroy()
        if folder and Path(folder).is_dir():
            return str(Path(folder).resolve())
    except Exception:
        pass

    return ''

def choose_file() -> str:
    """Opens a native Windows file selection dialog for PDF and image documents.
    Tries PowerShell OpenFileDialog first, with fallbacks to Tkinter.
    """
    # Strategy 1: PowerShell OpenFileDialog (modern, native, modal topmost)
    try:
        import subprocess
        ps_code = (
            "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; "
            "$f = New-Object System.Windows.Forms.OpenFileDialog; "
            "$f.Title = 'Select Document File to Manage'; "
            "$f.Filter = 'All Supported Documents (*.pdf;*.png;*.jpg;*.jpeg;*.webp;*.docx;*.xlsx;*.doc;*.xls;*.pptx;*.ppt;*.txt)|*.pdf;*.png;*.jpg;*.jpeg;*.webp;*.docx;*.xlsx;*.doc;*.xls;*.pptx;*.ppt;*.txt|PDF Files (*.pdf)|*.pdf|Office Documents (*.docx;*.xlsx;*.doc;*.xls;*.pptx;*.ppt)|*.docx;*.xlsx;*.doc;*.xls;*.pptx;*.ppt|Text Files (*.txt)|*.txt|Image Files (*.png;*.jpg;*.jpeg;*.webp)|*.png;*.jpg;*.jpeg;*.webp|All Files (*.*)|*.*'; "
            "$topmost = New-Object System.Windows.Forms.Form; "
            "$topmost.TopMost = $true; "
            "if ($f.ShowDialog($topmost) -eq [System.Windows.Forms.DialogResult]::OK) { "
            "Write-Host -NoNewline $f.FileName }"
        )
        cmd = ["powershell", "-NoProfile", "-NonInteractive", "-Command", ps_code]
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=60, creationflags=creationflags)
        file_path = res.stdout.strip()
        if file_path and Path(file_path).is_file():
            return str(Path(file_path).resolve())
    except Exception:
        pass

    # Strategy 2: Tkinter fallback
    try:
        import tkinter as tk
        from tkinter import filedialog
        window = tk.Tk()
        window.withdraw()
        window.attributes('-topmost', True)
        file_path = filedialog.askopenfilename(
            title='Select Document File to Manage',
            filetypes=[
                ("All Supported Documents", "*.pdf;*.png;*.jpg;*.jpeg;*.webp;*.docx;*.xlsx;*.doc;*.xls;*.pptx;*.ppt;*.txt"),
                ("PDF Files", "*.pdf"),
                ("Office Documents", "*.docx;*.xlsx;*.doc;*.xls;*.pptx;*.ppt"),
                ("Text Files", "*.txt"),
                ("Image Files", "*.png;*.jpg;*.jpeg;*.webp"),
                ("All Files", "*.*")
            ]
        )
        window.destroy()
        if file_path and Path(file_path).is_file():
            return str(Path(file_path).resolve())
    except Exception:
        pass

    return ''

# Folder or File Selection Section
gemini_queue = SequentialGeminiQueue.get_instance()
currently_active = gemini_queue.get_current_processing()
if not currently_active and st.session_state.get('processing_file'):
    st.session_state.processing_file = ''
active_processing_path = currently_active or st.session_state.get('processing_file', '')
is_any_processing = bool(active_processing_path)

sel_col1, sel_col2, sel_col3 = st.columns([1.1, 1.1, 3.8])
if sel_col1.button('📂 Browse Folder', type='primary', use_container_width=True, disabled=is_any_processing, help="Operation locked: a document is currently processing" if is_any_processing else None):
    chosen = choose_folder()
    if chosen and Path(chosen).is_dir():
        resolved_path = str(Path(chosen).resolve())
        is_diff = (st.session_state.root != resolved_path)
        st.session_state.root = resolved_path
        st.session_state.folder_path_input = resolved_path
        st.session_state.quota_exhausted = False
        st.session_state.run_queue = True
        if is_diff:
            scan(conn, resolved_path, retain=False)
            st.session_state.scan_scope = resolved_path
        st.rerun()

if sel_col2.button('📄 Select File', type='secondary', use_container_width=True, disabled=is_any_processing, help="Operation locked: a document is currently processing" if is_any_processing else None):
    chosen_file = choose_file()
    if chosen_file and Path(chosen_file).is_file():
        resolved_path = str(Path(chosen_file).resolve())
        is_diff = (st.session_state.root != resolved_path)
        st.session_state.root = resolved_path
        st.session_state.folder_path_input = resolved_path
        st.session_state.quota_exhausted = False
        st.session_state.run_queue = True
        if is_diff:
            scan(conn, resolved_path, retain=False)
            st.session_state.scan_scope = resolved_path
        st.rerun()

def _on_target_input_change():
    new_path = st.session_state.get('folder_path_input', '').strip()
    if new_path != st.session_state.root:
        st.session_state.root = new_path
        st.session_state.quota_exhausted = False
        st.session_state.run_queue = True

root_input = sel_col3.text_input(
    'Document folder or file path',
    key='folder_path_input',
    placeholder='Select or paste folder or file path (PDF/Office/Text/Image)',
    on_change=_on_target_input_change,
    label_visibility='collapsed',
    disabled=is_any_processing
)

if root_input and root_input.strip() != st.session_state.root:
    st.session_state.root = root_input.strip()
    st.session_state.quota_exhausted = False
    st.session_state.run_queue = True

root = st.session_state.root
target_path = Path(root) if root else None

if not root or not target_path or not (target_path.is_dir() or target_path.is_file()):
    st.info('👈 Please select a document folder or single file (PDF/Office/Text/Image) to discover and analyze.')
    st.stop()

if target_path and target_path.is_file():
    st.caption(f"🎯 **Single File Mode:** `{target_path.name}` · Location: `{target_path.parent}`")

# Initial scan triggered immediately upon folder or file selection
if st.session_state.scan_scope != str(Path(root).resolve()):
    scan(conn, root, retain=True)
    st.session_state.scan_scope = str(Path(root).resolve())
    st.session_state.run_queue = True
    st.session_state.quota_exhausted = False

all_records = rows(conn, root)

# Rescan Controls (Preserve vs Overwrite Dialog)
rescan_col1, rescan_col2 = st.columns([1, 4])
is_cur_exhausted = (
    is_quota_exhausted(st.session_state.ai_provider) or
    (st.session_state.ai_provider == 'gemini' and (st.session_state.get('gemini_declined') or st.session_state.get('gemini_exhausted'))) or
    (st.session_state.ai_provider == 'groq' and (st.session_state.get('groq_acknowledged') or st.session_state.get('groq_exhausted'))) or
    get_remaining_daily_quota(st.session_state.ai_provider) <= 0
)

if rescan_col1.button('🔄 Scan Again', key='rescan_again_btn', disabled=is_any_processing, help="Operation locked: a document is currently processing" if is_any_processing else None):
    if is_cur_exhausted:
        from smartdoc.hosted import get_gemini_reset_info, get_groq_reset_info
        if st.session_state.ai_provider == 'gemini':
            gh, gd = get_gemini_reset_info()
            show_gemini_quota_dialog(gh, gd)
        else:
            gh, gd = get_groq_reset_info()
            show_groq_quota_dialog(gh, gd)
    else:
        st.session_state.ask_rescan = True

if st.session_state.get('ask_rescan'):
    target_type = "File" if (target_path and target_path.is_file()) else "Folder"
    st.warning(f'### Rescan {target_type}: Preserve or Overwrite?')
    choice = st.radio(
        f'Do you want to preserve earlier scan results or overwrite them for this {target_type.lower()}?',
        ('Preserve earlier results (merge & update existing records)', f'Overwrite scan results (clear cache for this {target_type.lower()})'),
        index=0
    )
    b1, b2 = st.columns([1, 4])
    if b1.button('Confirm Rescan', type='primary'):
        has_pending = any(r.get('status') != 'done' and r.get('status') != 'skipped' for r in all_records)
        is_retain = True if has_pending else choice.startswith('Preserve')
        scan(conn, root, retain=is_retain)
        st.session_state.ask_rescan = False
        st.session_state.run_queue = True
        st.session_state.quota_exhausted = False
        st.rerun()
    if b2.button('Cancel'):
        st.session_state.ask_rescan = False
        st.rerun()

# Ensure processed records have standard virtual name consistent with Android app and metadata indexed
for r in all_records:
    if r.get('status') == 'done' and not r.get('override'):
        v_name = r.get('virtual_name') or ''
        p_name = r.get('proposed') or ''
        if not is_compliant_proposed_name(v_name) or not is_compliant_proposed_name(p_name):
            cat = r.get('category') or 'Other Document'
            doc_type = ''
            issued_date = ''
            meta_str = r.get('metadata') or ''
            if meta_str:
                try:
                    m_json = json.loads(meta_str)
                    issued_date = m_json.get('issued_date') or ''
                    doc_type = m_json.get('document_type') or m_json.get('document_kind') or ''
                except Exception:
                    pass
            if not doc_type:
                spec_str = r.get('specifics') or ''
                if spec_str.startswith('{'):
                    try:
                        s_json = json.loads(spec_str)
                        doc_type = s_json.get('document_kind') or s_json.get('document_type') or ''
                        if not issued_date:
                            issued_date = s_json.get('metadata', {}).get('issued_date', '')
                    except Exception:
                        pass
            compliant_name = generate_compliant_proposed_name(
                cat,
                document_type=doc_type or cat,
                issued_date=issued_date,
                original_name=r['name'],
                text=r.get('specifics', '')
            )
            r['proposed'] = compliant_name
            r['virtual_name'] = compliant_name
            try:
                update(conn, r['path'], proposed=compliant_name, virtual_name=compliant_name)
            except Exception:
                pass
    elif r.get('status') == 'done' and not r.get('virtual_name') and r.get('proposed'):
        r['virtual_name'] = r['proposed']
        try:
            update(conn, r['path'], virtual_name=r['proposed'])
        except Exception:
            pass
    if r.get('status') == 'done' and not r.get('metadata'):
        p = Path(r['path'])
        if p.exists():
            try:
                m_obj = build_document_metadata(
                    p, filename=r['name'],
                    category=r.get('category', ''),
                    proposed_name=r.get('proposed', ''),
                    specifics_raw=r.get('specifics', '')
                )
                r['metadata'] = json.dumps(m_obj)
                update(conn, r['path'], metadata=r['metadata'])
            except Exception:
                pass

# Filter hidden duplicates unless toggled
records = [r for r in all_records if not r.get('is_hidden', 0)] if not st.session_state.show_duplicates else all_records
pending = [r for r in records if r['status'] != 'done']

# Metric Summary
stat1, stat2, stat3 = st.columns(3)
stat1.metric('Discovered Files', len(records))
stat2.metric('Processed Documents', len([r for r in records if r['status'] == 'done']))
stat3.metric('Pending Analysis', len(pending))

# Prominent Quota Exhaustion Alert
if st.session_state.get('gemini_declined'):
    st.warning("⚠️ **Gemini daily quota exhausted.** Processing paused by user (Groq fallback declined). Pending documents will be reprocessed once quota resets.")
elif st.session_state.get('groq_acknowledged'):
    st.warning("⚠️ **Groq daily quota exhausted.** Processing paused. Pending documents will be reprocessed once quota resets.")
elif st.session_state.quota_exhausted:
    st.error("⚠️ **All services have surpassed their free quota. Please try after some time.**")
    st.info("The application automatically halted processing to avoid quota violations. Both Gemini and Groq free tiers will reset periodically.")

st.divider()

# ==========================================
# SEARCH & FILTER CONTROLS (Metadata-Aware with Intelligent Suggestions)
# ==========================================
st.markdown("#### 🔍 Search & Filter Document Library")

# Row 1: Search Bar & Clear Button
search_col, clear_col = st.columns([5.2, 0.8], vertical_alignment="bottom")

def _clear_search_query():
    st.session_state.search_query = ""

def _set_search_query(term):
    st.session_state.search_query = term

search_val = search_col.text_input(
    "Search Documents & Complete Metadata",
    key="search_query",
    placeholder="Search by keywords, lab (e.g. lalpath), medical test (e.g. LFT, CBC), vendor, date, or any metadata...",
    label_visibility="collapsed",
    help="Instant search across file names, full extracted text, medical tests, laboratory names, vendors, and AI evidence."
)

clear_col.button(
    "✕ Clear",
    key="clear_search_btn",
    on_click=_clear_search_query,
    use_container_width=True,
    disabled=not st.session_state.search_query,
    help="Clear search filter"
)

# Dynamic intelligent suggestions based on library metadata ("guessing what user is trying to search")
suggestions = get_search_suggestions(records, st.session_state.search_query, max_suggestions=10)

if suggestions:
    # Clickable suggestion pills row underneath search input
    st.markdown("<div style='margin-top: -6px; margin-bottom: 6px;'><span style='font-size: 0.78rem; color: #94a3b8; font-weight: 500;'>💡 Suggestions (click to search):</span></div>", unsafe_allow_html=True)
    pill_cols = st.columns(min(len(suggestions[:6]), 6))
    for idx, term in enumerate(suggestions[:6]):
        pill_cols[idx].button(
            f"🔍 {term}",
            key=f"sugg_pill_{idx}_{term}",
            on_click=_set_search_query,
            args=(term,),
            help=f"Search for '{term}'",
            use_container_width=True
        )

# Row 2: Category, Type, Date, Duplicates, and Empty Text Filters
all_cats = ['All categories'] + sorted({r['category'] for r in records if r.get('category')})
all_types = ['All types'] + sorted({r['kind'] for r in records if r.get('kind')})
date_presets = ['All dates', 'Since Today', 'Since Yesterday', 'Since A Week', 'Since A Month', 'Since Last 3 Months', 'Since Last 6 Months', 'Since Last 1 Year', 'Older than 1 Year']

if 'show_no_text' not in st.session_state:
    st.session_state.show_no_text = False

f_col1, f_col2, f_col3, f_col4, f_col5 = st.columns([1.8, 1.3, 1.5, 0.9, 0.9], vertical_alignment="center")
sel_category = f_col1.selectbox('Filter by Category', all_cats)
sel_type = f_col2.selectbox('Filter by Type', all_types)
sel_date = f_col3.selectbox('Filter by Date', date_presets)
st.session_state.show_duplicates = f_col4.checkbox('Duplicates', value=st.session_state.show_duplicates)
st.session_state.show_no_text = f_col5.checkbox('Empty Text', value=st.session_state.show_no_text, help='Show files with no textual content (hidden by default)')

def is_no_text_record(r):
    spec = str(r.get('specifics', '')).lower()
    if 'no readable text' in spec or 'no text content' in spec or 'dropped from cloud requests' in spec:
        return True
    return False

filtered_records = [
    r for r in records 
    if (sel_category == 'All categories' or r['category'] == sel_category)
    and (sel_type == 'All types' or r.get('kind') == sel_type)
    and date_match(r['mtime_ns'], sel_date)
    and matches_query(r, st.session_state.search_query)
    and (st.session_state.show_no_text or not is_no_text_record(r))
]

if st.session_state.search_query:
    st.caption(f"Showing **{len(filtered_records)}** of **{len(records)}** documents matching: `{st.session_state.search_query}`")

# ==========================================
# 5-COLUMN INTERACTIVE DOCUMENT TABLE WITH EXPANDABLE ROWS
# ==========================================
st.markdown("### Document Library")
st.caption("Click any document name to open it in your default desktop viewer. Expand any row to inspect why a category & proposed name were chosen.")

col_headers = st.columns([2.6, 0.7, 1.4, 2.3, 3.0])
col_headers[0].markdown("**File Name**")
col_headers[1].markdown("**Type**")
col_headers[2].markdown("**Processed**")
col_headers[3].markdown("**Category**")
col_headers[4].markdown("**Proposed Name**")
st.markdown("<hr style='margin-top: 0; margin-bottom: 8px;'>", unsafe_allow_html=True)

# Active file being analyzed in the background
active_processing_path = currently_active or st.session_state.get('processing_file', '')

for r in filtered_records:
    c1, c2, c3, c4, c5 = st.columns([2.6, 0.7, 1.4, 2.3, 3.0], vertical_alignment='center')
    is_this_processing = (r['path'] == active_processing_path)
    
    # Column 1: Selectable File Name with Desktop Open Button
    # Standard name for visual appearance: override, virtual_name or proposed if done, fallback to original name
    display_title = r.get('override') or r.get('virtual_name') or (r['proposed'] if r.get('status') == 'done' and r.get('proposed') else r['name'])
    orig_sub = f"<br><span style='font-size:0.75em; color:#94a3b8;'>Original: {r['name']}</span>" if display_title != r['name'] else ""
    kind_icon = {
        'Word': '📝',
        'Excel': '📊',
        'PowerPoint': '📽️',
        'Text': '📃',
        'Image': '🖼️',
        'PDF': '📄'
    }.get(r.get('kind', ''), '📄')
    c1_text, c1_open = c1.columns([5, 1], vertical_alignment='center')
    if is_this_processing:
        c1_text.markdown(f"<span style='word-break: break-all;'>{kind_icon} **{display_title}** <span style='font-size:0.75em; color:#38bdf8; background:#1e293b; padding:2px 6px; border-radius:4px;'>🔒 Processing</span>{orig_sub}</span>", unsafe_allow_html=True, help=f"Full path: {r['path']} - Operations locked during active processing")
    else:
        c1_text.markdown(f"<span style='word-break: break-all;'>{kind_icon} **{display_title}**{orig_sub}</span>", unsafe_allow_html=True, help=f"Full path: {r['path']}")
    
    if c1_open.button("↗️", key=f"open_{r['path']}", disabled=is_this_processing, help="Operation locked: document is currently processing..." if is_this_processing else f"Open {r['name']} in default desktop app"):
        try:
            os.startfile(r['path'])
        except Exception as exc:
            st.error(f"Cannot open document: {exc}")
            
    # Column 2: Document Type Badge
    kind = r.get('kind', 'Doc')
    badge_colors = {
        'PDF': '#ef4444',
        'Word': '#2563eb',
        'Excel': '#16a34a',
        'PowerPoint': '#ea580c',
        'Text': '#6366f1',
        'Image': '#0284c7'
    }
    b_color = badge_colors.get(kind, '#64748b')
    c2.markdown(f"<span style='background:{b_color}18; color:{b_color}; border:1px solid {b_color}44; padding:2px 7px; border-radius:5px; font-weight:700; font-size:0.8em; letter-spacing:0.3px;'>{kind}</span>", unsafe_allow_html=True)
    
    # Column 3: Processed Status with In-Cell Animated Progress Bar Illusion
    if r['status'] == 'done':
        c3.markdown("✅ `True`")
    elif r['path'] == active_processing_path:
        c3.progress(0.70, text='Processing…')
    else:
        c3.markdown("⏳ `False`")
        
    # Column 4: Category
    if r['status'] == 'done':
        doc_cat = r.get('category') or 'Other Document'
        c4.markdown(f"🏷️ **{doc_cat}**")
    elif r['path'] == active_processing_path:
        c4.markdown("<span style='color: #38bdf8;'>⚡ <i>Analyzing...</i></span>", unsafe_allow_html=True)
    else:
        c4.markdown("<span style='color: #64748b;'>⏳ <i>Queued</i></span>", unsafe_allow_html=True)

    # Column 5: Proposed Name with In-line Edit & Save
    current_proposal = r.get('override') or r.get('proposed') or r.get('virtual_name') or ''
    
    # Auto-generate proposed name if status is done but proposed is missing in database
    if r['status'] == 'done' and not current_proposal:
        try:
            cat = r.get('category') or 'Other Document'
            current_proposal = generate_proposed_name(cat, r.get('specifics', ''), r['name'])
        except Exception:
            current_proposal = r['name']
        if not current_proposal:
            current_proposal = r['name']
        update(conn, r['path'], proposed=current_proposal)
        r['proposed'] = current_proposal
        
    input_key = f"input_{r['path']}"
    sync_key = f"sync_{r['path']}"
    
    if r['status'] == 'done' and not is_this_processing:
        # Ensure input widget is pre-populated with proposed or saved override name
        saved_val = r.get('override') or current_proposal or r['name']
        if st.session_state.get(sync_key) != saved_val:
            st.session_state[input_key] = saved_val
            st.session_state[sync_key] = saved_val
        elif input_key not in st.session_state or not st.session_state[input_key]:
            st.session_state[input_key] = saved_val
            
        new_name = c5.text_input(
            'Proposed Name',
            value=st.session_state[input_key],
            key=input_key,
            label_visibility='collapsed'
        )
        
        def _save_name_cb(record_path, proposed_override):
            clean_override = short_name(Path(proposed_override).stem, Path(record_path).suffix)
            update(conn, record_path, override=clean_override, virtual_name=clean_override)
            st.session_state[f"input_{record_path}"] = clean_override
            st.session_state[f"sync_{record_path}"] = clean_override

        # If user modified the name, show Save Name button
        if new_name and new_name.strip() != (r.get('override') or r.get('proposed')):
            c5.button(
                'Save Name',
                key=f"save_{r['path']}",
                on_click=_save_name_cb,
                args=(r['path'], new_name)
            )
    else:
        # File is still pending or processing: show clear status instead of empty text box
        if is_this_processing:
            c5.markdown("<span style='color: #38bdf8;'>⚡ <i>Analyzing document... (locked)</i></span>", unsafe_allow_html=True)
        else:
            c5.markdown("<span style='color: #64748b;'>⏳ <i>Queued</i></span>", unsafe_allow_html=True)

    # Expandable row to see API Name, prompt sent, raw results, and evidence
    with st.expander(f"🔍 Classification basis & evidence: {display_title}"):
        if r['status'] == 'done':
            # Parse specifics JSON if structured data was stored, or adapt legacy strings
            spec_raw = r.get('specifics', '').strip()
            details = None
            if spec_raw.startswith('{') and spec_raw.endswith('}'):
                try:
                    details = json.loads(spec_raw)
                except Exception:
                    details = None
            
            # Normalize legacy strings, error notes, or plain-text database records
            if not details:
                c_err, c_basis = parse_legacy_specifics(spec_raw)
                details = {
                    'api_name': 'Local Indian Taxonomy Engine (Offline Fallback)' if c_err else (r.get('provider') or 'Local Indian Taxonomy Engine'),
                    'endpoint': endpoint if c_err else 'Local Rule Engine',
                    'cloud_error': c_err,
                    'basis': c_basis,
                    'data_received': {
                        'provider': 'Local Indian Taxonomy Engine (Offline Fallback)' if c_err else (r.get('provider') or 'Local Engine'),
                        'category': r.get('category', 'Other Document'),
                        'proposed_name': current_proposal,
                        'specifics': c_basis,
                        'cloud_status': c_err or 'OK'
                    },
                    'raw_json': {'note': c_basis, 'cloud_error': c_err} if c_err else ({'raw_note': spec_raw} if spec_raw else {})
                }

            api_name = details.get('api_name') or r.get('provider') or 'Local Indian Taxonomy Engine'
            cloud_error = (details.get('cloud_error') or '').strip()
            if not cloud_error and ('503' in spec_raw or 'hosted classifier' in spec_raw.lower() or 'service not configured' in spec_raw.lower()):
                c_err, _ = parse_legacy_specifics(spec_raw)
                cloud_error = c_err
            raw_prov = (details.get('provider') or details.get('data_received', {}).get('provider') or r.get('provider', '')).lower()
            
            # Determine provider requested and provider used based on the request that generated the response
            prompt_obj = details.get('prompt_sent') or {}
            raw_resp_obj = details.get('raw_response') or details.get('raw_json') or {}
            prompt_str = str(prompt_obj).lower()
            resp_str = str(raw_resp_obj).lower()

            # What provider was requested for this document?
            if 'groq' in prompt_str or 'openai/gpt-oss-20b' in prompt_str or 'groq' in cloud_error.lower():
                requested_prov = 'Groq'
                requested_model = 'openai/gpt-oss-20b'
            elif 'gemini' in prompt_str or 'gemini' in cloud_error.lower():
                requested_prov = 'Google Gemini'
                requested_model = 'gemini-2.0-flash'
            else:
                requested_prov = 'Groq' if st.session_state.get('ai_provider') == 'groq' else 'Google Gemini'
                requested_model = 'openai/gpt-oss-20b' if requested_prov == 'Groq' else 'gemini-2.0-flash'

            # What provider actually generated the response?
            rec = details.get('data_received', {})
            rec_prov = (rec.get('provider') or details.get('provider') or r.get('provider', '')).lower()
            rec_model = rec.get('model') or details.get('model') or ''

            if not cloud_error and ('groq' in rec_prov or 'groq' in resp_str):
                actual_prov_title = "Groq (openai/gpt-oss-20b)"
                actual_prov_badge = "⚡ **Provider Used:** **Groq (openai/gpt-oss-20b)**"
            elif not cloud_error and ('gemini' in rec_prov or 'gemini' in resp_str or 'gemini' in api_name.lower()):
                actual_prov_title = "Google Gemini 3.5 Flash Lite"
                actual_prov_badge = "🤖 **Provider Used:** **Google Gemini 3.5 Flash Lite**"
            elif cloud_error:
                actual_prov_title = "Local Indian Taxonomy Engine (Offline Fallback)"
                actual_prov_badge = "🏠 **Active Provider Used:** **Local Indian Taxonomy Engine** (Offline Indian Rules & Heuristics)"
            else:
                actual_prov_title = api_name
                actual_prov_badge = f"🏠 **Provider Used:** **{api_name}**"

            # 1. Cloud AI Status / Provider Used Alert / Badge
            if cloud_error:
                st.warning(
                    f"⚠️ **Cloud AI Service Status:** `{cloud_error}`\n\n"
                    "• **Action Taken:** Automatically classified using **Local Indian Taxonomy Engine** so your documents are processed without disruption.\n"
                    f"• **Attempted Cloud Model:** {requested_prov} ({requested_model})."
                )
                st.info("🏠 **Active Provider Used:** **Local Indian Taxonomy Engine** (Offline Indian Rules & Heuristics)")
            else:
                st.success(actual_prov_badge)
            
            # 2. Key-Value summary columns
            det_c1, det_c2 = st.columns([1.2, 1])
            with det_c1:
                st.markdown(f"**Assigned Category:** `{r.get('category', 'Other Document')}`")
                st.markdown(f"**Proposed Filename:** `{current_proposal}`")
                st.markdown(f"**Inference Engine:** `{actual_prov_title}`")
            with det_c2:
                st.markdown(f"**Document Type:** `{r.get('kind', 'Document')}`")
                st.markdown(f"**Disk Size:** `{r.get('size', 0) / 1024:.1f} KB`")
                st.markdown(f"**Original Filename:** `{r['name']}`")
            
            # 3. Dedicated API & Classification Evidence Details
            st.markdown("---")
            
            # Discovered Metadata & Key Entities
            meta_raw = r.get('metadata')
            if meta_raw:
                try:
                    meta_dict = json.loads(meta_raw) if isinstance(meta_raw, str) else meta_raw
                    kws = meta_dict.get('keywords', [])
                    labs = meta_dict.get('labs', [])
                    tests = meta_dict.get('tests', [])
                    dates_found = meta_dict.get('dates', [])
                    entities = meta_dict.get('entities') or {}
                    if not entities and isinstance(details.get('metadata'), dict):
                        entities = details.get('metadata')

                    if labs or tests or kws or dates_found or entities:
                        st.markdown("#### 🧪 Discovered Metadata & Entities")
                        meta_cols = st.columns(2)
                        with meta_cols[0]:
                            if entities.get('doctor_name'):
                                st.markdown(f"• **Doctor Name:** `{entities['doctor_name']}`")
                            if entities.get('patient_name'):
                                st.markdown(f"• **Patient Name:** `{entities['patient_name']}`")
                            if entities.get('hospital_name'):
                                st.markdown(f"• **Hospital / Clinic:** `{entities['hospital_name']}`")
                            if labs:
                                st.markdown(f"• **Lab / Diagnostic Center:** `{', '.join(labs)}`")
                            elif entities.get('lab_name'):
                                st.markdown(f"• **Lab / Diagnostic Center:** `{entities['lab_name']}`")
                            if tests:
                                st.markdown(f"• **Medical Tests / Procedures:** `{', '.join(tests)}`")
                            elif entities.get('test_name'):
                                st.markdown(f"• **Medical Tests / Procedures:** `{entities['test_name']}`")
                            if entities.get('lessor_name'):
                                st.markdown(f"• **Lessor Name:** `{entities['lessor_name']}`")
                            if entities.get('lessee_name'):
                                st.markdown(f"• **Lessee Name:** `{entities['lessee_name']}`")
                        with meta_cols[1]:
                            if entities.get('title'):
                                st.markdown(f"• **Document Title:** `{entities['title']}`")
                            if entities.get('issuer_name'):
                                st.markdown(f"• **Issuer / Organization:** `{entities['issuer_name']}`")
                            if entities.get('issued_date'):
                                st.markdown(f"• **Issued Date:** `{entities['issued_date']}`")
                            elif dates_found:
                                st.markdown(f"• **Document Dates:** `{', '.join(dates_found)}`")
                            if entities.get('identifier'):
                                st.markdown(f"• **Identifier / ID:** `{entities['identifier']}`")
                            if entities.get('amount'):
                                st.markdown(f"• **Amount:** `{entities['amount']}`")
                            if kws:
                                st.markdown(f"• **Discovered Keywords:** `{', '.join(kws[:8])}`")
                        st.markdown("<div style='height: 4px;'></div>", unsafe_allow_html=True)
                except Exception:
                    pass

            st.markdown("#### 📡 AI & Classification Details")
            d_col1, d_col2 = st.columns([1, 1])
            with d_col1:
                doc_kind_val = details.get('document_kind') or rec.get('document_kind') or r.get('kind', 'Document')
                st.markdown(f"• **Provider Used:** `{actual_prov_title}`")
                st.markdown(f"• **Document Kind:** `{doc_kind_val}`")
                st.markdown(f"• **Logical Category:** `{rec.get('category') or r.get('category')}`")
                st.markdown(f"• **Proposed Name:** `{rec.get('suggested_name') or rec.get('proposed_name') or current_proposal}`")
                if cloud_error:
                    st.markdown(f"• **Cloud API Status:** `⚠️ {cloud_error}`")
            with d_col2:
                basis_text = details.get('basis') or rec.get('specifics') or 'Document classification evidence'
                st.markdown("• **Reasoning / Why it Belongs to Category:**")
                st.text_area(
                    "Evidence (Selectable)",
                    value=basis_text,
                    height=75,
                    key=f"ta_basis_{r['path']}",
                    label_visibility="collapsed",
                    help="Click and drag with mouse cursor to select text"
                )
                
            # 4. Prompt Sent & Raw Response Inspection (Selectable and Copyable)
            st.markdown("---")
            inspection_target = requested_prov if cloud_error else (
                "Groq (openai/gpt-oss-20b)" if "groq" in actual_prov_title.lower() else (
                    "Google Gemini 3.5 Flash Lite" if "gemini" in actual_prov_title.lower() else actual_prov_title
                )
            )
            target_short_name = "Groq" if "groq" in inspection_target.lower() else ("Gemini" if "gemini" in inspection_target.lower() else "API")
            st.markdown(f"#### 🔬 {inspection_target} API Inspection (Selectable & Copyable)")
            st.caption("Click into the boxes to select with mouse cursor, press Ctrl+A / Ctrl+C, or click the copy icon on the code box:")
            
            p_col1, p_col2 = st.columns(2)
            with p_col1:
                st.markdown(f"**📤 Exact Prompt Sent to {target_short_name}:**")
                prompt_obj = details.get('prompt_sent')
                if prompt_obj:
                    if isinstance(prompt_obj, dict):
                        parts = []
                        q_text = prompt_obj.get('prompt_question') or prompt_obj.get('user_question')
                        if q_text:
                            parts.append(f"=== PROMPT QUESTION ===\n{q_text}")
                        if prompt_obj.get('system_instruction'):
                            parts.append(f"=== PROMPT INSTRUCTION ===\n{prompt_obj.get('system_instruction')}")
                        if prompt_obj.get('mode'):
                            parts.append(f"=== ATTACHMENT MODE ===\n{prompt_obj.get('mode')} ({prompt_obj.get('mime_type', 'file')})")
                        if prompt_obj.get('user_text'):
                            parts.append(f"=== DOCUMENT TEXT (OPTIONAL) ===\n{prompt_obj.get('user_text')}")
                        prompt_formatted = "\n\n".join(parts) if parts else str(prompt_obj)
                    else:
                        prompt_formatted = str(prompt_obj)
                else:
                    prompt_formatted = "No external prompt dispatched (document processed offline via local taxonomy rules)."

                st.text_area(
                    "Prompt Text (Selectable)",
                    value=prompt_formatted,
                    height=140,
                    key=f"ta_prompt_{r['path']}",
                    label_visibility="collapsed",
                    help="Click and drag with mouse cursor to select text"
                )
                st.code(prompt_formatted, language="markdown")

            with p_col2:
                st.markdown(f"**📥 Exact Raw Response from {target_short_name}:**")
                raw_resp_obj = details.get('raw_response') or details.get('raw_json') or {}
                if raw_resp_obj:
                    if isinstance(raw_resp_obj, (dict, list)):
                        raw_formatted = json.dumps(raw_resp_obj, indent=2)
                    else:
                        raw_formatted = str(raw_resp_obj)
                else:
                    raw_formatted = "No raw API response recorded."

                st.text_area(
                    "Response Text (Selectable)",
                    value=raw_formatted,
                    height=140,
                    key=f"ta_resp_{r['path']}",
                    label_visibility="collapsed",
                    help="Click and drag with mouse cursor to select text"
                )
                st.code(raw_formatted, language="json")
                
            # 5. Action button to re-analyze this document
            re_col1, _ = st.columns([1.5, 3])
            if re_col1.button("🔄 Re-analyze Document", key=f"re_{r['path']}", disabled=is_this_processing, help="Operation locked: document is currently processing..." if is_this_processing else "Clear cached result and re-run through AI/local engine"):
                update(conn, r['path'], status='pending')
                st.session_state.processing_file = ''
                st.session_state.run_queue = True
                st.rerun()
        elif r['path'] == active_processing_path:
            active_prov_name = "Groq (openai/gpt-oss-20b)" if st.session_state.get('ai_provider') == 'groq' else "Google Gemini 3.5 Flash Lite"
            st.info(f"⚡ *Currently extracting text and evaluating Indian taxonomy patterns with {active_prov_name}... (operations locked)*")
        else:
            st.caption("⏳ *Document queued for automatic analysis.*")

st.divider()

# ==========================================
# VIRTUAL RENAME & REMOVE DUPLICATES (Non-destructive)
# ==========================================
st.subheader("Virtual Organization & Deduplication")
st.write("Apply proposed naming conventions and hide exact duplicate copies inside SmartDocManager. **Original files on disk remain completely unmodified.**")

# Identify exact duplicates by SHA-256
seen_digests = {}
duplicates = []
for r in records:
    if r['digest'] in seen_digests:
        duplicates.append((r['name'], seen_digests[r['digest']]))
    else:
        seen_digests[r['digest']] = r['name']

if duplicates:
    st.info(f"🔍 **{len(duplicates)} exact duplicate copies** detected via SHA-256 fingerprinting.")
    with st.expander("View Identified Duplicates"):
        st.table([{'Duplicate Document': d[0], 'Retained Copy': d[1]} for d in duplicates])
else:
    st.success("No duplicate files found in this folder.")

action_c1, action_c2 = st.columns([1.5, 1])
if action_c1.button('✨ Rename and Remove Duplicates', type='primary', use_container_width=True, disabled=is_any_processing, help="Operation locked: a document is currently being processed. Please wait for it to finish." if is_any_processing else None):
    try:
        # Non-destructive virtual execution
        changed, hidden = rename_and_trash(conn, root, confirm_duplicates=True, virtual_only=True)
        st.success(f"Successfully applied virtual names to {len(changed)} documents and hidden {len(hidden)} duplicate records inside the app.")
        st.caption("🔒 Original files on disk remain untouched.")
        st.rerun()
    except Exception as exc:
        st.error(f"Error updating virtual records: {exc}")

if action_c2.button('🔄 Re-analyze All Documents', type='secondary', use_container_width=True, disabled=is_any_processing, help="Operation locked: a document is currently being processed. Please wait for it to finish." if is_any_processing else "Reset all documents to pending to re-run AI / local analysis"):
    conn.execute("UPDATE files SET status='pending'")
    conn.commit()
    st.session_state.processing_file = ''
    st.session_state.quota_exhausted = False
    st.session_state.gemini_declined = False
    st.session_state.groq_acknowledged = False
    st.session_state.ai_provider = 'gemini'
    gemini_queue.clear_quota_event()
    st.rerun()

# ==========================================================
# AUTOMATIC SEQUENTIAL ASYNCHRONOUS GEMINI 2.5 FLASH PROCESSING
# ==========================================================
gemini_queue = SequentialGeminiQueue.get_instance()
currently_processing = gemini_queue.get_current_processing()
quota_ev = gemini_queue.get_quota_event()

if quota_ev:
    q_prov = quota_ev.get('provider', 'gemini')
    q_hour = quota_ev.get('reset_hour', '')
    q_date = quota_ev.get('reset_date', '')
    if q_prov == 'gemini':
        st.session_state.gemini_exhausted = True
        st.session_state.gemini_exhausted_day = get_gemini_day_key()
        if not st.session_state.get('gemini_declined'):
            show_gemini_quota_dialog(q_hour, q_date)
    elif q_prov == 'groq':
        st.session_state.groq_exhausted = True
        st.session_state.groq_exhausted_day = get_groq_day_key()
        if not st.session_state.get('groq_acknowledged'):
            show_groq_quota_dialog(q_hour, q_date)

if currently_processing:
    st.session_state.processing_file = currently_processing
    time.sleep(0.3)
    st.rerun()
elif st.session_state.get('processing_file'):
    # Queue completed the active job. Reset state and refresh once so the UI immediately shows '✅ True'
    st.session_state.processing_file = ''
    st.rerun()
elif pending and not quota_ev and not st.session_state.get('gemini_declined') and not st.session_state.get('groq_acknowledged') and not st.session_state.quota_exhausted:
    failed_paths = gemini_queue.get_failed_paths()
    eligible_pending = [r for r in pending if r['path'] not in failed_paths]
    if not eligible_pending:
        st.session_state.processing_file = ''
    else:
        target_record = eligible_pending[0]
        target_path = Path(target_record['path'])
        st.session_state.processing_file = str(target_path)
        
        # Callback invoked in worker thread upon step completion or failure
        def step_completed_callback(path_done, res, err):
            pass
            
        submitted = gemini_queue.submit_task(
            str(target_path),
            endpoint=endpoint,
            token=token,
            gemini_api_key=st.session_state.get('gemini_api_key') or os.environ.get('GEMINI_API_KEY'),
            on_complete=step_completed_callback,
            preferred_provider=st.session_state.get('ai_provider', 'gemini')
        )
        if submitted:
            time.sleep(0.2)
            st.rerun()
        else:
            st.session_state.processing_file = ''
else:
    st.session_state.processing_file = ''

conn.close()
