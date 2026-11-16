import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from app import parse_legacy_specifics

def test_parse_legacy_specifics_normal_ai_note():
    err, basis = parse_legacy_specifics("AI Note: Doctor Lal PathLabs LFT report")
    assert err == ""
    assert "Doctor Lal PathLabs LFT report" in basis

def test_parse_legacy_specifics_http_503():
    err, basis = parse_legacy_specifics("Hosted classifier HTTP 503")
    assert "503" in err
    assert "Indian document taxonomy patterns" in basis

def test_parse_legacy_specifics_service_not_configured():
    err, basis = parse_legacy_specifics("AI Note: Service not configured on hosted Cloudflare Worker")
    assert "Service not configured" in err
    assert "Indian document taxonomy patterns" in basis
