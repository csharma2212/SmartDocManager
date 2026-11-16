"""Unit tests for Windows desktop Google OAuth login flow."""
import base64
import json
import sys
import threading
import time
from pathlib import Path
from urllib.request import urlopen, Request
from urllib.error import HTTPError, URLError
import pytest
from unittest.mock import patch, MagicMock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from smartdoc.google_login import start_login


CLIENT_ID = "123456789-abcdef.apps.googleusercontent.com"


def _safe_urlopen(url: str, timeout: int = 5):
    try:
        return urlopen(url, timeout=timeout)
    except URLError as exc:
        if "10013" in str(exc) or "forbidden" in str(exc).lower():
            pytest.skip("Loopback socket connection blocked by sandbox security policy")
        raise


def _make_jwt(claims: dict) -> str:
    header = base64.urlsafe_b64encode(b'{"alg":"none"}').decode().rstrip("=")
    payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip("=")
    return f"{header}.{payload}."


def test_invalid_client_id_rejected():
    result = {}
    with pytest.raises(ValueError, match="Google OAuth client ID"):
        start_login("invalid-client-id", result)


def test_previous_errors_cleared_on_retry():
    result = {"error": "Previous failure message", "state": "error"}
    with patch("smartdoc.google_login.open_auth_window"):
        start_login(CLIENT_ID, result)
    assert "error" not in result
    assert result.get("state") == "waiting"


def test_login_with_backend_proxy_exchange():
    result = {}
    fake_token = _make_jwt({"aud": CLIENT_ID, "email": "proxyuser@example.com", "exp": int(time.time()) + 3600})

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {"id_token": fake_token}

    with patch("smartdoc.google_login.open_auth_window") as mock_open, patch("requests.post", return_value=mock_resp) as mock_post:
        start_login(CLIENT_ID, result, backend_url="https://smartdocmanager-api.workers.dev")

        auth_url = mock_open.call_args[0][0]
        from urllib.parse import parse_qs, urlsplit
        params = parse_qs(urlsplit(auth_url).query)
        redirect_uri = params["redirect_uri"][0]
        state = params["state"][0]

        callback_url = f"{redirect_uri}?state={state}&code=test_proxy_code"
        resp = _safe_urlopen(callback_url, timeout=5)
        assert resp.status == 200

        for _ in range(20):
            if result.get("state") == "done":
                break
            time.sleep(0.05)

        assert result.get("state") == "done"
        assert result.get("email") == "proxyuser@example.com"
        assert "/v1/auth/token" in mock_post.call_args[0][0]


def test_login_with_client_secret_included():
    result = {}
    fake_token = _make_jwt({"aud": CLIENT_ID, "email": "test@example.com", "exp": int(time.time()) + 3600})

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {"id_token": fake_token}

    with patch("smartdoc.google_login.open_auth_window") as mock_open, patch("requests.post", return_value=mock_resp) as mock_post:
        start_login(CLIENT_ID, result, client_secret="GOCSPX-mocksecret123")

        auth_url = mock_open.call_args[0][0]
        from urllib.parse import parse_qs, urlsplit
        params = parse_qs(urlsplit(auth_url).query)
        redirect_uri = params["redirect_uri"][0]
        state = params["state"][0]

        callback_url = f"{redirect_uri}?state={state}&code=test_code_secret"
        resp = _safe_urlopen(callback_url, timeout=5)
        assert resp.status == 200

        for _ in range(20):
            if result.get("state") == "done":
                break
            time.sleep(0.05)

        assert result.get("state") == "done"
        assert mock_post.call_args[1]["data"]["client_secret"] == "GOCSPX-mocksecret123"


def test_successful_login_exchange():
    result = {}
    fake_token = _make_jwt({"aud": CLIENT_ID, "email": "test@example.com", "exp": int(time.time()) + 3600})

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {"id_token": fake_token}

    with patch("smartdoc.google_login.open_auth_window") as mock_open, patch("requests.post", return_value=mock_resp) as mock_post:
        start_login(CLIENT_ID, result, client_secret="test_secret")

        # Get redirect URI and state from the URL passed to open_auth_window
        auth_url = mock_open.call_args[0][0]
        from urllib.parse import parse_qs, urlsplit
        params = parse_qs(urlsplit(auth_url).query)
        redirect_uri = params["redirect_uri"][0]
        state = params["state"][0]

        # Simulate browser callback to the loopback server
        callback_url = f"{redirect_uri}?state={state}&code=test_auth_code_123"
        resp = _safe_urlopen(callback_url, timeout=5)
        assert resp.status == 200
        content = resp.read().decode()
        assert "Sign-in Complete" in content

        # Give background thread a moment to finish POST exchange
        for _ in range(20):
            if result.get("state") == "done":
                break
            time.sleep(0.05)

        assert result.get("state") == "done"
        assert result.get("email") == "test@example.com"
        assert result.get("token") == fake_token
        mock_post.assert_called_once()


def test_oauth_error_client_secret_missing_clarified():
    result = {}
    mock_resp = MagicMock()
    mock_resp.status_code = 400
    mock_resp.text = '{"error": "invalid_request", "error_description": "client_secret is missing."}'
    mock_resp.json.return_value = {"error": "invalid_request", "error_description": "client_secret is missing."}

    with patch("smartdoc.google_login.open_auth_window") as mock_open, patch("requests.post", return_value=mock_resp):
        start_login(CLIENT_ID, result)

        from urllib.parse import parse_qs, urlsplit
        auth_url = mock_open.call_args[0][0]
        params = parse_qs(urlsplit(auth_url).query)
        redirect_uri = params["redirect_uri"][0]
        state = params["state"][0]

        callback_url = f"{redirect_uri}?state={state}&code=test_code"
        _safe_urlopen(callback_url, timeout=5)

        for _ in range(20):
            if result.get("state") == "error":
                break
            time.sleep(0.05)

        assert result.get("state") == "error"
        assert "client_secret is missing" in result.get("error", "").lower()
        assert "GitHub" in result.get("error", "")


def test_favicon_ignored_without_breaking_flow():
    result = {}
    fake_token = _make_jwt({"aud": CLIENT_ID, "email": "user@example.com", "exp": int(time.time()) + 3600})

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {"id_token": fake_token}

    with patch("smartdoc.google_login.open_auth_window") as mock_open, patch("requests.post", return_value=mock_resp):
        start_login(CLIENT_ID, result, client_secret="test_secret")

        from urllib.parse import parse_qs, urlsplit
        auth_url = mock_open.call_args[0][0]
        params = parse_qs(urlsplit(auth_url).query)
        redirect_uri = params["redirect_uri"][0]
        state = params["state"][0]

        # Browser requests /favicon.ico first
        favicon_url = redirect_uri.rstrip("/") + "/favicon.ico"
        fav_resp = _safe_urlopen(favicon_url, timeout=5)
        assert fav_resp.status == 204
        assert "error" not in result

        # Then browser callback arrives
        callback_url = f"{redirect_uri}?state={state}&code=valid_code"
        cb_resp = _safe_urlopen(callback_url, timeout=5)
        assert cb_resp.status == 200

        for _ in range(20):
            if result.get("state") == "done":
                break
            time.sleep(0.05)

        assert result.get("state") == "done"
        assert result.get("email") == "user@example.com"


def test_close_auth_window_strictly_protects_existing_windows():
    from smartdoc.google_login import close_auth_window
    import ctypes

    closed_hwnds = []

    def fake_post_message(hwnd, msg, wparam, lparam):
        closed_hwnds.append((hwnd, msg))
        return 1

    # Simulate 3 windows: 101 (existing user window), 102 (another existing window), 103 (new auth window)
    mock_titles = {
        101: "SmartDocManager - Sign-in Complete - Edge",  # An existing window with matching title
        102: "Work Project - Edge",
        103: "SmartDocManager - Sign-in Complete - Edge",  # The newly created window
    }

    def fake_enum_windows(callback, lparam):
        for hwnd in [101, 102, 103]:
            callback(hwnd, lparam)
        return True

    def fake_get_window_text(hwnd, buf, max_len):
        title = mock_titles.get(hwnd, "")
        buf.value = title
        return len(title)

    mock_user32 = MagicMock()
    mock_user32.IsWindowVisible.return_value = True
    mock_user32.GetWindowTextLengthW.side_effect = lambda h: len(mock_titles.get(h, ""))
    mock_user32.GetWindowTextW.side_effect = fake_get_window_text
    mock_user32.EnumWindows.side_effect = fake_enum_windows
    mock_user32.PostMessageW.side_effect = fake_post_message

    with patch("ctypes.windll.user32", mock_user32):
        # Pass 101 and 102 as initial_hwnds (pre-existing windows)
        close_auth_window(initial_hwnds={101, 102})

    # Window 101 and 102 must NEVER receive close messages even if title matched!
    closed_ids = [h for h, _ in closed_hwnds]
    assert 101 not in closed_ids, "Existing window 101 was closed!"
    assert 102 not in closed_ids, "Existing window 102 was closed!"
    assert 103 in closed_ids, "New auth window 103 was not closed!"


def test_open_auth_window_launches_new_window():
    from smartdoc.google_login import open_auth_window

    with patch("smartdoc.google_login.find_browser_executable", return_value=r"C:\Program Files\Edge\msedge.exe"), \
         patch("subprocess.Popen") as mock_popen, \
         patch("smartdoc.google_login.get_window_handles", return_value={1, 2, 3}):
        open_auth_window("https://accounts.google.com/test")

        mock_popen.assert_called_once()
        args = mock_popen.call_args[0][0]
        assert "--new-window" in args
        assert "https://accounts.google.com/test" in args

