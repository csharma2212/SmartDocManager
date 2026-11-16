import sys
from pathlib import Path
import tempfile
from streamlit.testing.v1 import AppTest

app_path = str(Path(__file__).resolve().parents[1] / "app.py")

def test_search_and_clear_button():
    with tempfile.TemporaryDirectory() as td:
        sample_doc = Path(td) / "pathology_report.pdf"
        sample_doc.write_bytes(b"%PDF-1.4\n1 0 obj\n<< /Title (Dr Lal PathLabs Report LFT) >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF")

        at = AppTest.from_file(app_path)
        # Mock authenticated session
        at.session_state['google_login'] = {'token': 'mock_token', 'expires': 9999999999}
        at.session_state['root'] = td
        at.session_state['folder_path_input'] = td
        at.session_state['search_query'] = 'lalpath'
        at.run(timeout=10)

        assert len(at.exception) == 0

        # Find the clear button by key
        clear_btn = next((b for b in at.button if b.key == 'clear_search_btn'), None)
        assert clear_btn is not None
        assert not clear_btn.disabled

        # Click the clear button and run
        clear_btn.click().run(timeout=10)

        # Verify no StreamlitWidgetAlreadyInstantiatedError was raised
        assert len(at.exception) == 0
        assert at.session_state['search_query'] == ''

def test_search_suggestion_pill():
    with tempfile.TemporaryDirectory() as td:
        sample_doc = Path(td) / "pathology_report.pdf"
        sample_doc.write_bytes(b"%PDF-1.4\n1 0 obj\n<< /Title (Dr Lal PathLabs Report LFT) >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF")

        at = AppTest.from_file(app_path)
        at.session_state['google_login'] = {'token': 'mock_token', 'expires': 9999999999}
        at.session_state['root'] = td
        at.session_state['folder_path_input'] = td
        at.session_state['search_query'] = ''
        at.run(timeout=10)

        assert len(at.exception) == 0

        # Check if suggestion buttons exist
        sugg_buttons = [b for b in at.button if b.key and b.key.startswith('sugg_pill_')]
        if sugg_buttons:
            sugg_btn = sugg_buttons[0]
            sugg_btn.click().run(timeout=10)
            assert len(at.exception) == 0
            assert at.session_state['search_query'] != ''
