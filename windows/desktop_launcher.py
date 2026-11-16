"""SmartDocManager Desktop Application Launcher
Runs the application as a standalone, dedicated desktop window (borderless app mode)
without browser tabs or navigation bars.
"""
import os
import sys
import time
import subprocess
import socket
from pathlib import Path

def is_port_in_use(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(1.0)
        return s.connect_ex(('127.0.0.1', port)) == 0

def is_server_healthy(port):
    try:
        import urllib.request
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/_stcore/health", timeout=1.5) as resp:
            return resp.status == 200
    except Exception:
        try:
            import urllib.request
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/", timeout=1.5) as resp:
                return resp.status == 200
        except Exception:
            return False

def terminate_port_owner(port):
    """Terminates any stale or unresponsive process listening on the port."""
    try:
        out = subprocess.check_output(f'netstat -ano | findstr :{port}', shell=True, text=True)
        for line in out.strip().splitlines():
            parts = line.split()
            if len(parts) >= 5 and "LISTENING" in parts:
                pid = parts[-1]
                if pid.isdigit() and int(pid) != os.getpid():
                    subprocess.run(f"taskkill /F /PID {pid}", shell=True, capture_output=True)
        time.sleep(0.5)
    except Exception:
        pass

def find_browser_app_executable():
    # Check for Edge (preinstalled on all Windows 10/11)
    edge_paths = [
        os.path.expandvars(r"%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"),
        os.path.expandvars(r"%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"),
    ]
    for p in edge_paths:
        if os.path.exists(p):
            return p
            
    # Check for Chrome as alternative
    chrome_paths = [
        os.path.expandvars(r"%ProgramFiles%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%LocalAppData%\Google\Chrome\Application\chrome.exe"),
    ]
    for p in chrome_paths:
        if os.path.exists(p):
            return p
            
    return None

def launch_desktop(port=8501):
    root = Path(__file__).resolve().parent
    app_script = root / "app.py"
    
    server_process = None
    if is_port_in_use(port):
        if not is_server_healthy(port):
            print(f"[SmartDocManager] Port {port} occupied by an unresponsive process. Terminating stale process...")
            terminate_port_owner(port)

    if not is_port_in_use(port):
        print(f"[SmartDocManager] Starting background application engine on port {port}...")
        python_exe = sys.executable
        server_cmd = [
            python_exe, "-m", "streamlit", "run",
            str(app_script),
            f"--server.port={port}",
            "--server.address=127.0.0.1",
            "--server.headless=true",
            "--server.enableCORS=false",
            "--browser.gatherUsageStats=false",
            "--global.developmentMode=false"
        ]
        server_process = subprocess.Popen(
            server_cmd,
            cwd=str(root),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        
        # Wait up to 15 seconds for the server to become healthy
        start_time = time.time()
        while time.time() - start_time < 15:
            if is_server_healthy(port):
                break
            time.sleep(0.3)
            
    app_url = f"http://127.0.0.1:{port}"
    browser_exe = find_browser_app_executable()
    
    if browser_exe:
        print(f"[SmartDocManager] Launching dedicated Desktop App Window...")
        profile_dir = Path.home() / ".smartdocmanager" / "edge_profile"
        profile_dir.mkdir(parents=True, exist_ok=True)
        window_cmd = [
            browser_exe,
            f"--app={app_url}",
            "--window-size=1280,850",
            f"--user-data-dir={profile_dir}",
            "--no-first-run",
            "--no-default-browser-check"
        ]
        start_launch = time.time()
        try:
            window_proc = subprocess.run(window_cmd)
            # If the process exited in under 2 seconds (e.g. edge singleton delegation), keep server alive until Ctrl+C
            if time.time() - start_launch < 2.0 and server_process:
                print(f"[SmartDocManager] App running at {app_url}. Press Ctrl+C in this console to terminate.")
                while server_process.poll() is None:
                    time.sleep(1)
        except KeyboardInterrupt:
            pass
    else:
        print(f"[SmartDocManager] Opening in default browser at {app_url}...")
        import webbrowser
        webbrowser.open(app_url)
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            pass

    if server_process and server_process.poll() is None:
        print("[SmartDocManager] Shutting down application engine...")
        server_process.terminate()

if __name__ == "__main__":
    port = 8501
    if len(sys.argv) > 1 and sys.argv[1].isdigit():
        port = int(sys.argv[1])
    launch_desktop(port)
