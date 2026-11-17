import json, time, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pytest
from smartdoc.library import connect, scan, rows
from smartdoc.async_queue import SequentialGeminiQueue

def test_sequential_gemini_queue_step_completion(tmp_path, monkeypatch):
    monkeypatch.setenv('HOME', str(tmp_path))
    monkeypatch.setenv('USERPROFILE', str(tmp_path))
    
    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    f1 = docs_dir / "PAN_Card_Test.pdf"
    f1.write_bytes(b"%PDF-1.4 test pan card ABCDE1234F")
    
    conn = connect(docs_dir)
    scan(conn, docs_dir)
    
    def fake_classify(name, data, endpoint, token, **kwargs):
        return {
            'Status': 'OK',
            'Category': 'PAN Card',
            'SuggestedName': 'PAN_Card_ABCDE1234F_2024-01-01',
            'Specifics': 'Contains PAN number',
            'Provider': 'Google Gemini 2.5 Flash',
            'Text': 'test pan card ABCDE1234F',
            'PromptSent': {'system_instruction': 'Classify document...', 'user_text': 'test pan card ABCDE1234F'},
            'RawResponse': {'category': 'PAN Card', 'proposed_name': 'PAN_Card_ABCDE1234F_2024-01-01'}
        }
        
    monkeypatch.setattr('smartdoc.async_queue.classify_hosted', fake_classify)
    
    queue = SequentialGeminiQueue()
    callback_called = []
    
    def on_step(path, res, err):
        callback_called.append((path, res, err))
        
    accepted = queue.submit_task(str(f1), "https://example.com", "fake_token", on_complete=on_step)
    assert accepted is True
    
    for _ in range(50):
        if not queue.is_busy():
            break
        time.sleep(0.05)
        
    assert not queue.is_busy()
    assert len(callback_called) == 1
    assert callback_called[0][0] == str(f1)
    assert callback_called[0][1]['Status'] == 'OK'
    
    rec = rows(conn, docs_dir)[0]
    assert rec['status'] == 'done'
    assert rec['category'] == 'PAN Card'
    assert ('PANCard' in rec['proposed'] or 'PAN_Card' in rec['proposed'])
    assert len(rec['proposed']) <= 40
    assert rec['virtual_name'] == rec['proposed']
    spec = json.loads(rec['specifics'])
    assert spec['provider'] == 'Google Gemini 2.5 Flash'
    assert 'prompt_sent' in spec
    assert 'raw_response' in spec
    assert spec['raw_response']['category'] == 'PAN Card'
    conn.close()

def test_sequential_gemini_queue_failure_callback(tmp_path, monkeypatch):
    monkeypatch.setenv('HOME', str(tmp_path))
    monkeypatch.setenv('USERPROFILE', str(tmp_path))
    
    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    f1 = docs_dir / "Prescription_Test.pdf"
    f1.write_bytes(b"%PDF-1.4 prescription Dr Sharma Rx Paracetamol")
    
    conn = connect(docs_dir)
    scan(conn, docs_dir)
    
    def fake_classify_error(name, data, endpoint, token, **kwargs):
        return {
            'Status': 'API_ERROR',
            'Specifics': 'Hosted classifier HTTP 503: Service overloaded',
            'PromptSent': {'system_instruction': 'Classify...'},
            'RawResponse': {'error': 'overloaded'}
        }
        
    monkeypatch.setattr('smartdoc.async_queue.classify_hosted', fake_classify_error)
    
    queue = SequentialGeminiQueue()
    callback_called = []
    
    accepted = queue.submit_task(
        str(f1), "https://example.com", "fake_token",
        on_complete=lambda path, res, err: callback_called.append((path, res, err))
    )
    assert accepted is True
    
    for _ in range(50):
        if not queue.is_busy():
            break
        time.sleep(0.05)
        
    assert not queue.is_busy()
    assert len(callback_called) == 1
    rec = rows(conn, docs_dir)[0]
    assert rec['status'] == 'pending'
    spec = json.loads(rec['specifics'])
    assert '503' in spec['cloud_error']
    assert 'missing GEMINI_API_KEY' not in spec['cloud_error']
    assert 'prompt_sent' in spec
    assert 'raw_response' in spec
    conn.close()
