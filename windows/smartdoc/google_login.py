"""Google OIDC for local Windows desktop. No client secret embedded or logged.

Uses the installed-app authorization-code flow with PKCE and a loopback callback.
The ID token is held only in Streamlit session memory; login is repeated after restart.
"""
import base64
import hashlib
import json
import os
import secrets
import shlex
import subprocess
import threading
import time
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlencode, urlsplit, parse_qs

import requests

_active_server = None
_active_lock = threading.Lock()
_pre_auth_hwnds = set()


def find_browser_executable() -> str | None:
    """Finds the default web browser or installed Edge/Chrome to open new browser windows."""
    # 1. Attempt detection of default browser from Windows Registry
    try:
        import winreg
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            r"Software\Microsoft\Windows\Shell\Associations\UrlAssociations\http\UserChoice"
        ) as key:
            prog_id, _ = winreg.QueryValueEx(key, "ProgId")
        with winreg.OpenKey(winreg.HKEY_CLASSES_ROOT, rf"{prog_id}\shell\open\command") as key:
            cmd, _ = winreg.QueryValueEx(key, "")
            parts = shlex.split(cmd)
            if parts and os.path.exists(parts[0]):
                return parts[0]
    except Exception:
        pass

    # 2. Check standard installation paths for common modern browsers
    candidates = [
        os.path.expandvars(r"%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"),
        os.path.expandvars(r"%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"),
        os.path.expandvars(r"%ProgramFiles%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%LocalAppData%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%ProgramFiles%\BraveSoftware\Brave-Browser\Application\brave.exe"),
        os.path.expandvars(r"%LocalAppData%\BraveSoftware\Brave-Browser\Application\brave.exe"),
        os.path.expandvars(r"%ProgramFiles%\Mozilla Firefox\firefox.exe"),
    ]
    for p in candidates:
        if os.path.exists(p):
            return p
    return None


def get_window_handles() -> set:
    """Returns a set of all currently existing top-level window handles."""
    hwnds = set()
    try:
        import ctypes
        from ctypes import wintypes
        user32 = ctypes.windll.user32
        WNDENUMPROC = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)

        def enum_cb(hwnd, _):
            hwnds.add(hwnd)
            return True

        user32.EnumWindows(WNDENUMPROC(enum_cb), 0)
    except Exception:
        pass
    return hwnds


def open_auth_window(url: str):
    """Opens Google Sign-in in a brand new, isolated browser window."""
    global _pre_auth_hwnds
    _pre_auth_hwnds = get_window_handles()

    browser_exe = find_browser_executable()
    if browser_exe:
        flag = "-new-window" if "firefox" in browser_exe.lower() else "--new-window"
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        try:
            return subprocess.Popen([browser_exe, flag, url], creationflags=creationflags)
        except Exception:
            pass

    webbrowser.open_new(url)
    return None


def close_auth_window(initial_hwnds: set | None = None):
    """Closes ONLY the dedicated sign-in window created for this login session.
    Strictly avoids touching or closing any pre-existing browser window.
    """
    if initial_hwnds is None:
        initial_hwnds = _pre_auth_hwnds

    try:
        import ctypes
        from ctypes import wintypes
        user32 = ctypes.windll.user32
        WNDENUMPROC = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        auth_hwnds = []

        def enum_cb(hwnd, _):
            # Strictly protect all pre-existing windows that were already open
            if hwnd in initial_hwnds:
                return True
            if user32.IsWindowVisible(hwnd):
                length = user32.GetWindowTextLengthW(hwnd)
                if length > 0:
                    buff = ctypes.create_unicode_buffer(length + 1)
                    user32.GetWindowTextW(hwnd, buff, length + 1)
                    title = buff.value
                    if "Sign-in Complete" in title or "SmartDocManager - Sign-in" in title:
                        auth_hwnds.append(hwnd)
            return True

        user32.EnumWindows(WNDENUMPROC(enum_cb), 0)
        for hwnd in auth_hwnds:
            # 0x0010 = WM_CLOSE, 0x0112 = WM_SYSCOMMAND, 0xF060 = SC_CLOSE
            user32.PostMessageW(hwnd, 0x0010, 0, 0)
            user32.PostMessageW(hwnd, 0x0112, 0xF060, 0)
    except Exception:
        pass


def bring_app_to_front():
    """Restores and brings the main SmartDocManager desktop window to the front."""
    try:
        import ctypes
        from ctypes import wintypes
        user32 = ctypes.windll.user32
        WNDENUMPROC = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        targets = []

        def enum_cb(hwnd, _):
            if user32.IsWindowVisible(hwnd):
                length = user32.GetWindowTextLengthW(hwnd)
                if length > 0:
                    buff = ctypes.create_unicode_buffer(length + 1)
                    user32.GetWindowTextW(hwnd, buff, length + 1)
                    title = buff.value
                    if "SmartDocManager" in title and "Sign-in" not in title:
                        targets.append(hwnd)
            return True

        user32.EnumWindows(WNDENUMPROC(enum_cb), 0)
        for hwnd in targets:
            # Send Alt key down/up to bypass Windows focus-stealing restrictions
            user32.keybd_event(0x12, 0, 0, 0)
            user32.ShowWindow(hwnd, 9)  # 9 = SW_RESTORE
            user32.SetForegroundWindow(hwnd)
            user32.keybd_event(0x12, 0, 2, 0)
            break
    except Exception:
        pass


def start_login(client_id: str, result: dict, backend_url: str = "", client_secret: str = "") -> None:
    global _active_server
    if not client_id.endswith('.apps.googleusercontent.com'):
        raise ValueError('Configure SMARTDOC_GOOGLE_CLIENT_ID with a Google OAuth client ID')

    # Close any lingering previous listener
    with _active_lock:
        if _active_server is not None:
            try:
                _active_server.server_close()
            except Exception:
                pass
            _active_server = None

    # Clear previous result state so retries don't fail immediately
    result.pop('error', None)
    result.pop('code', None)
    result.pop('token', None)
    result.pop('email', None)
    result.pop('expires', None)
    result['state'] = 'waiting'

    verifier = base64.urlsafe_b64encode(secrets.token_bytes(48)).decode().rstrip('=')
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    state = secrets.token_urlsafe(24)

    callback_received = threading.Event()

    class Callback(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_GET(self):
            parsed = urlsplit(self.path)
            # Ignore browser favicon or pre-connect probes
            if parsed.path == '/favicon.ico':
                self.send_response(204)
                self.end_headers()
                return

            params = parse_qs(parsed.query)
            if 'state' not in params:
                # Spurious probe or non-OAuth request
                self.send_response(400)
                self.end_headers()
                return

            if params.get('state', [''])[0] != state:
                result['error'] = 'OAuth state mismatch'
            elif 'error' in params:
                err_desc = params.get('error_description', params.get('error', ['Google sign-in was cancelled']))[0]
                result['error'] = f'Google sign-in was cancelled or rejected: {err_desc}'
            else:
                code = params.get('code', [''])[0]
                if code:
                    result['code'] = code
                else:
                    result['error'] = 'Google authorization response did not include an authorization code'

            body = (
                b'<!DOCTYPE html><html><head><meta charset="utf-8">'
                b'<title>SmartDocManager - Sign-in Complete</title>'
                b'<style>'
                b'body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#0f172a;color:#f8fafc}'
                b'.card{background:#1e293b;padding:40px;border-radius:16px;box-shadow:0 10px 25px -5px rgba(0,0,0,0.5);text-align:center;max-width:440px}'
                b'.icon{font-size:48px;color:#22c55e;margin-bottom:12px}'
                b'h2{margin:0 0 10px 0;font-size:22px}'
                b'p{color:#94a3b8;font-size:14px;margin:0}'
                b'</style></head>'
                b'<body><div class="card"><div class="icon">&#10004;</div>'
                b'<h2>Sign-in Complete</h2>'
                b'<p>Returning to SmartDocManager automatically. You can close this window.</p></div>'
                b'<script>'
                b'try { window.open("", "_self", ""); window.close(); } catch(e) {}'
                b'setTimeout(function() { window.close(); }, 300);'
                b'</script>'
                b'</body></html>'
            )
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Connection', 'close')
            self.end_headers()
            try:
                self.wfile.write(body)
                self.wfile.flush()
            except Exception:
                pass
            callback_received.set()

    server = HTTPServer(('127.0.0.1', 0), Callback)
    server.timeout = 1
    with _active_lock:
        _active_server = server

    redirect = f'http://127.0.0.1:{server.server_port}/'
    params = dict(
        client_id=client_id,
        redirect_uri=redirect,
        response_type='code',
        scope='openid email profile',
        state=state,
        code_challenge=challenge,
        code_challenge_method='S256',
        prompt='select_account',
    )

    auth_url = 'https://accounts.google.com/o/oauth2/v2/auth?' + urlencode(params)
    auth_proc = open_auth_window(auth_url)

    def work():
        global _active_server
        nonlocal auth_proc
        try:
            deadline = time.monotonic() + 180
            while not callback_received.is_set() and time.monotonic() < deadline:
                server.handle_request()

            if not callback_received.is_set():
                result['error'] = 'Google sign-in timed out (180s). Please click Sign in with Google to try again.'
                result['state'] = 'error'
                return

            time.sleep(0.3)
            # Close only the newly created auth window; existing windows are 100% protected
            close_auth_window(_pre_auth_hwnds)
            bring_app_to_front()
            if auth_proc and auth_proc.poll() is None:
                try:
                    auth_proc.terminate()
                except Exception:
                    pass

            if 'error' in result:
                result['state'] = 'error'
                return

            code = result.pop('code', None)
            if not code:
                result['error'] = 'No authorization code received from Google.'
                result['state'] = 'error'
                return

            token_data = None
            proxy_err = None

            # Strategy 1: Direct local exchange if client_secret is configured in .env (fastest, 100% Git-safe)
            if client_secret:
                post_data = {
                    'client_id': client_id,
                    'client_secret': client_secret,
                    'code': code,
                    'code_verifier': verifier,
                    'redirect_uri': redirect,
                    'grant_type': 'authorization_code',
                }
                response = requests.post(
                    'https://oauth2.googleapis.com/token',
                    data=post_data,
                    timeout=20,
                )
                if response.status_code == 200:
                    token_data = response.json()
                else:
                    err_detail = response.text
                    try:
                        err_json = response.json()
                        err_desc = err_json.get('error_description') or err_json.get('error')
                        if err_desc:
                            err_detail = err_desc
                    except Exception:
                        pass
                    masked_id = f"...{client_id[-24:]}" if len(client_id) > 24 else client_id
                    result['error'] = f"Google token exchange failed ({response.status_code}): {err_detail}\n(Using Client ID: {masked_id})"
                    result['state'] = 'error'
                    return

            # Strategy 2: Exchange via Cloudflare Worker proxy if secret is stored in Cloudflare
            elif backend_url:
                try:
                    proxy_resp = requests.post(
                        f"{backend_url.rstrip('/')}/v1/auth/token",
                        json={
                            'client_id': client_id,
                            'code': code,
                            'code_verifier': verifier,
                            'redirect_uri': redirect,
                        },
                        timeout=20,
                    )
                    if proxy_resp.status_code == 200:
                        token_data = proxy_resp.json()
                    elif proxy_resp.status_code == 404:
                        proxy_err = (
                            f"Cloudflare Worker ({backend_url}) returned 404 on /v1/auth/token.\n\n"
                            "The updated 'hosted-backend/cloudflare/src/worker.mjs' code has not been deployed to this worker yet."
                        )
                    else:
                        proxy_err = f"Cloudflare token broker error ({proxy_resp.status_code}): {proxy_resp.text}"
                except Exception as exc:
                    proxy_err = f"Cloudflare connection error: {exc}"

            if not token_data:
                result['error'] = proxy_err or (
                    "Google OAuth Error: 'client_secret is missing.'\n\n"
                    "Google requires the Client Secret to complete authentication.\n"
                    "To keep your GitHub repository 100% clean and safe:\n"
                    "• Add SMARTDOC_GOOGLE_CLIENT_SECRET=GOCSPX-... to a local .env file in the project root (Git-ignored),\n"
                    "• Or store it securely in Cloudflare: npx wrangler secret put GOOGLE_DESKTOP_CLIENT_SECRET."
                )
                result['state'] = 'error'
                return

            token = token_data.get('id_token', '')
            if not token:
                result['error'] = 'Google did not return an ID token.'
                result['state'] = 'error'
                return

            # Server verifies signature, audience, issuer and expiration; local parse is display only.
            segment = token.split('.')[1]
            claims = json.loads(base64.urlsafe_b64decode(segment + '=' * (-len(segment) % 4)))
            if claims.get('aud') != client_id:
                result['error'] = f"Unexpected Google token audience: {claims.get('aud')} != {client_id}"
                result['state'] = 'error'
                return

            result.update(
                token=token,
                email=claims.get('email', ''),
                expires=int(claims.get('exp', 0)),
                state='done',
            )
            # Bring app window to top automatically
            bring_app_to_front()
        except Exception as exc:
            result['error'] = f"Google sign-in error: {exc}"
            result['state'] = 'error'
        finally:
            if auth_proc and auth_proc.poll() is None:
                try:
                    auth_proc.terminate()
                except Exception:
                    pass
            with _active_lock:
                try:
                    server.server_close()
                except Exception:
                    pass
                if _active_server is server:
                    _active_server = None

    threading.Thread(target=work, daemon=True, name='smartdoc-google-login').start()
