"""Portable Application Entrypoint for SmartDocManager
Launches the local Streamlit engine and renders it inside a standalone, dedicated
native Windows application window (Edge/Chrome app mode) with no browser tabs,
no URL bar, and no console/CLI window.
"""
import os
import sys
import time
import socket
import threading
import subprocess
import webbrowser
import urllib.request
import multiprocessing
from pathlib import Path

# Multiprocessing freeze support is mandatory for frozen executables on Windows
multiprocessing.freeze_support()

# Set up logging / dummy output streams to prevent crashes in windowless mode (console=False)
log_dir = Path.home() / ".smartdocmanager"
log_dir.mkdir(parents=True, exist_ok=True)
log_file = log_dir / "launcher.log"


class DummyBuffer:
    def __init__(self, path):
        self._path = path

    def write(self, b):
        try:
            with open(self._path, "ab") as f:
                f.write(b)
        except Exception:
            pass

    def flush(self):
        pass


class DummyWriter:
    def __init__(self, file_path):
        self._path = file_path
        self.encoding = "utf-8"
        self.errors = "replace"
        self.buffer = DummyBuffer(file_path)

    def write(self, text):
        try:
            with open(self._path, "a", encoding="utf-8", errors="replace") as f:
                f.write(text)
        except Exception:
            pass

    def flush(self):
        pass

    def isatty(self):
        return False


class DummyStdin:
    def read(self, *args, **kwargs):
        return ""

    def readline(self, *args, **kwargs):
        return ""

    def readlines(self, *args, **kwargs):
        return []

    def __iter__(self):
        return iter([])

    def isatty(self):
        return False


if sys.stdout is None or not hasattr(sys.stdout, "write"):
    sys.stdout = DummyWriter(log_file)
if sys.stderr is None or not hasattr(sys.stderr, "write"):
    sys.stderr = DummyWriter(log_file)
if sys.stdin is None or not hasattr(sys.stdin, "read"):
    sys.stdin = DummyStdin()


def get_base_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).parent))
    return Path(__file__).resolve().parent


def is_port_in_use(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.4)
        return s.connect_ex(("127.0.0.1", port)) == 0


def is_server_healthy(port: int) -> bool:
    try:
        req = urllib.request.Request(f"http://127.0.0.1:{port}/_stcore/health")
        with urllib.request.urlopen(req, timeout=1.0) as resp:
            if resp.status == 200:
                return True
    except Exception:
        pass

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(0.4)
            return s.connect_ex(("127.0.0.1", port)) == 0
    except Exception:
        return False


def find_free_port(start_port: int = 8501) -> int:
    """Finds an available TCP port on IPv4 loopback (127.0.0.1)."""
    for port in range(start_port, start_port + 50):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                s.bind(("127.0.0.1", port))
                return port
            except OSError:
                continue
    return start_port


def find_browser_app_executable() -> str | None:
    """Finds installed Microsoft Edge or Google Chrome to run borderless app mode."""
    candidates = [
        os.path.expandvars(r"%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"),
        os.path.expandvars(r"%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"),
        os.path.expandvars(r"%ProgramFiles%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%LocalAppData%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%ProgramFiles%\BraveSoftware\Brave-Browser\Application\brave.exe"),
        os.path.expandvars(r"%LocalAppData%\BraveSoftware\Brave-Browser\Application\brave.exe"),
    ]
    for p in candidates:
        if os.path.exists(p):
            return p
    return None


def launch_desktop_window(port: int):
    """Waits for Streamlit to be healthy, then launches a dedicated standalone desktop window."""
    app_url = f"http://127.0.0.1:{port}"
    start = time.time()
    server_ready = False
    while time.time() - start < 40:
        if is_server_healthy(port):
            server_ready = True
            break
        time.sleep(0.3)

    browser_exe = find_browser_app_executable()
    if browser_exe:
        window_cmd = [
            browser_exe,
            f"--app={app_url}",
            "--window-size=1360,860",
            "--no-first-run",
            "--no-default-browser-check",
        ]
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        try:
            subprocess.Popen(window_cmd, creationflags=creationflags)
            return
        except Exception:
            pass

    # Fallback to system default browser
    webbrowser.open(app_url)


def monitor_runtime_lifecycle():
    """Monitors Streamlit runtime state and exits cleanly when the user closes the app window."""
    time.sleep(3.0)
    try:
        from streamlit.runtime import Runtime, RuntimeState
    except ImportError:
        return

    had_connected_session = False
    disconnected_since = None

    while True:
        time.sleep(1.0)
        try:
            if not Runtime.exists():
                continue
            rt = Runtime.instance()
            state = rt.state
            if state == RuntimeState.ONE_OR_MORE_SESSIONS_CONNECTED:
                had_connected_session = True
                disconnected_since = None
            elif had_connected_session and state == RuntimeState.NO_SESSIONS_CONNECTED:
                if disconnected_since is None:
                    disconnected_since = time.time()
                elif time.time() - disconnected_since >= 4.0:
                    # User closed the app window; exit cleanly
                    os._exit(0)
        except Exception:
            pass


def main():
    try:
        base_dir = get_base_dir()
        if str(base_dir) not in sys.path:
            sys.path.insert(0, str(base_dir))

        app_script = base_dir / "app.py"
        if not app_script.exists():
            exe_dir = Path(sys.executable).parent
            if (exe_dir / "app.py").exists():
                app_script = exe_dir / "app.py"

        port = find_free_port(8501)

        # Configure Streamlit explicitly for local loopback operation (IPv4)
        from streamlit import config as _st_config
        _st_config.set_option("server.address", "127.0.0.1")
        _st_config.set_option("server.port", port)
        _st_config.set_option("server.headless", True)
        _st_config.set_option("server.fileWatcherType", "none")
        _st_config.set_option("server.enableCORS", False)
        _st_config.set_option("server.enableXsrfProtection", False)
        _st_config.set_option("browser.gatherUsageStats", False)
        _st_config.set_option("global.developmentMode", False)
        _st_config.set_option("logger.hideWelcomeMessage", True)

        # Launch dedicated desktop window and runtime lifecycle monitor in background threads
        window_thread = threading.Thread(target=launch_desktop_window, args=(port,), daemon=True)
        window_thread.start()

        monitor_thread = threading.Thread(target=monitor_runtime_lifecycle, daemon=True)
        monitor_thread.start()

        import streamlit.web.bootstrap as stb
        flag_options = {
            "server.port": port,
            "server.address": "127.0.0.1",
            "server.headless": True,
            "server.fileWatcherType": "none",
            "server.enableCORS": False,
            "server.enableXsrfProtection": False,
            "browser.gatherUsageStats": False,
            "global.developmentMode": False,
            "logger.hideWelcomeMessage": True,
        }
        stb.load_config_options(flag_options)

        stb.run(
            main_script_path=str(app_script),
            is_hello=False,
            args=[],
            flag_options=flag_options,
        )
    except Exception as exc:
        import traceback
        err_msg = f"Fatal Error in SmartDocManager:\n\n{exc}\n\n{traceback.format_exc()}"
        try:
            with open(log_file, "a", encoding="utf-8") as f:
                f.write("\n" + err_msg + "\n")
            import ctypes
            ctypes.windll.user32.MessageBoxW(
                0,
                f"Error starting SmartDocManager:\n\n{exc}\n\nSee {log_file} for details.",
                "SmartDocManager Error",
                0x10,
            )
        except Exception:
            pass


if __name__ == "__main__":
    main()
