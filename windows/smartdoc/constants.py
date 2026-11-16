"""Centralized Application Configuration & Constants
Loads embedded encrypted configuration with optional local overrides and environment variables.
"""
import os
import json
from pathlib import Path
from .embedded_config import get_embedded_config

# Locate shared-spec/app-config.json and local overrides
PROJECT_ROOT = Path(__file__).resolve().parents[2]
SHARED_CONFIG_PATH = PROJECT_ROOT / "shared-spec" / "app-config.json"
LOCAL_CONFIG_PATH = PROJECT_ROOT / "shared-spec" / "app-config.local.json"
ENV_PATH = PROJECT_ROOT / ".env"

# Auto-load untracked .env file into os.environ if present
if ENV_PATH.exists():
    try:
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                k = k.strip()
                v = v.strip().strip("'\"")
                if k:
                    os.environ[k] = v
    except Exception:
        pass

# Initialize with encrypted embedded values
_config = get_embedded_config()

# Merge disk shared config if present (e.g. running from repo checkout)
if SHARED_CONFIG_PATH.exists():
    try:
        disk_cfg = json.loads(SHARED_CONFIG_PATH.read_text(encoding="utf-8"))
        for k, v in disk_cfg.items():
            if v and not str(v).startswith("REPLACE_"):
                _config[k] = v
    except Exception:
        pass

# Merge local untracked overrides if present (never committed to git)
_local_config = {}
if LOCAL_CONFIG_PATH.exists():
    try:
        _local_config = json.loads(LOCAL_CONFIG_PATH.read_text(encoding="utf-8"))
        _config.update(_local_config)
    except Exception:
        pass

DEFAULT_BACKEND_URL = os.environ.get("SMARTDOC_BACKEND_URL") or _config.get(
    "backend_url", "https://smartdocmanager-api.csharma2212.workers.dev"
)

# Load desktop client ID: env var takes precedence, followed by embedded config
GOOGLE_DESKTOP_CLIENT_ID = (
    os.environ.get("SMARTDOC_GOOGLE_CLIENT_ID", "").strip()
    or _config.get("google_desktop_client_id", "").strip()
)

# Desktop client secret: env var takes precedence, followed by local config or embedded config
GOOGLE_DESKTOP_CLIENT_SECRET = (
    os.environ.get("SMARTDOC_GOOGLE_CLIENT_SECRET", "").strip()
    or _local_config.get("google_desktop_client_secret", "").strip()
    or _config.get("google_desktop_client_secret", "").strip()
)

GOOGLE_WEB_CLIENT_ID = (
    os.environ.get("SMARTDOC_GOOGLE_WEB_CLIENT_ID", "").strip()
    or _config.get("google_web_client_id", "").strip()
)

def is_desktop_client_id_configured():
    """Returns True if a real client ID is configured (not empty and not a placeholder)."""
    return bool(GOOGLE_DESKTOP_CLIENT_ID) and not GOOGLE_DESKTOP_CLIENT_ID.startswith("REPLACE_WITH_")
