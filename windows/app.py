import os, sys, time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import pandas as pd
import streamlit as st
from smartdoc.core import classify, SUPPORTED, MAX_BYTES

icon_path = Path(__file__).resolve().parent / 'assets' / 'icon.png'
st.set_page_config(page_title='SmartDocManager', page_icon=str(icon_path) if icon_path.is_file() else None, layout='wide')
st.title('SmartDocManager Document Classifier')
st.caption('Cloud classification: selected document images are transmitted to Google Gemini. Use synthetic, redacted or authorized documents only.')
if not st.session_state.get('cloud_ack',False):
    st.warning('Gemini free-tier data handling may not meet confidentiality requirements. Do not submit sensitive or company-controlled documents without authorization.')
    if st.button('I understand and will use approved test documents'):
        st.session_state.cloud_ack=True;st.rerun()
    st.stop()
raw_key=st.text_input('Gemini API key',type='password',value=os.environ.get('GEMINI_API_KEY',''))
key=raw_key.strip()
if key and not (key.startswith('AQ.') or key.startswith('AIza')):
    st.warning("⚠️ **Notice on API Key Format**: Google Gemini API keys start with **'AQ.Ab...'** (new format) or **'AIzaSy...'** (classic format). Please ensure you copied your key from [Google AI Studio > Get API key](https://aistudio.google.com/apikey).")

def rank_models(models):
    preferred = [
        'gemini-3-flash-preview',
        'gemini-2.0-flash',
        'gemini-2.0-flash-lite',
        'gemini-2.5-flash',
        'gemini-2.0-pro-exp-02-05',
        'gemini-1.5-flash-latest',
        'gemini-1.5-flash-8b',
        'gemini-1.5-pro',
        'gemini-1.5-flash'
    ]
    non_vision = ['antigravity', 'embedding', 'aqa', 'text-', 'chat-', 'code-']
    vision_models = [m for m in models if not any(nv in m.lower() for nv in non_vision)]
    other_models = [m for m in models if any(nv in m.lower() for nv in non_vision)]
    
    ranked = []
    for p in preferred:
        for m in vision_models:
            if m.lower() == p and m not in ranked:
                ranked.append(m)
    for m in sorted(vision_models):
        if m not in ranked:
            ranked.append(m)
    for m in sorted(other_models):
        if m not in ranked:
            ranked.append(m)
    return ranked

if 'available_models' not in st.session_state:
    st.session_state.available_models = ['gemini-3-flash-preview', 'gemini-2.0-flash', 'gemini-2.0-flash-lite', 'gemini-1.5-flash', 'gemini-1.5-pro']

col1, col2 = st.columns([3, 1])
with col2:
    if st.button('🔍 Detect Models for Key', disabled=not bool(key), help='Queries Google AI Studio for models supported by your API key'):
        try:
            import requests
            resp = requests.get(f'https://generativelanguage.googleapis.com/v1beta/models?key={key}', timeout=10)
            if resp.status_code == 200:
                fetched = []
                for m in resp.json().get('models', []):
                    if 'generateContent' in m.get('supportedGenerationMethods', []):
                        name = m.get('name', '').replace('models/', '')
                        if name: fetched.append(name)
                if fetched:
                    st.session_state.available_models = rank_models(fetched)
                    st.success(f"Detected {len(fetched)} active models! Recommended multimodal model selected.")
                else:
                    st.warning("No models found supporting generateContent.")
            else:
                st.error(f"Failed to fetch models (HTTP {resp.status_code}). Check your API key.")
        except Exception as e:
            st.error(f"Detection error: {e}")

model_list = list(st.session_state.available_models)
if 'Custom (type below)' not in model_list:
    model_list.append('Custom (type below)')

saved_model = os.environ.get('GEMINI_MODEL', 'gemini-3-flash-preview')
default_idx = model_list.index(saved_model) if saved_model in model_list else 0

with col1:
    selected_option = st.selectbox('Gemini model ID (active in Google AI Studio)', options=model_list, index=default_idx)

if selected_option == 'Custom (type below)':
    model = st.text_input('Custom model ID', value=saved_model if saved_model not in model_list else 'gemini-2.0-flash')
else:
    model = selected_option

if any(nv in model.lower() for nv in ['antigravity', 'embedding', 'text-', 'chat-']):
    st.warning(f"⚠️ Model '{model}' is a text/code-only model and does not accept image inputs. For documents, please select a multimodal model such as **gemini-2.0-flash** from the dropdown above.")
files=st.file_uploader('Select documents',type=['pdf','png','jpg','jpeg','webp'],accept_multiple_files=True)
folder=st.text_input('Optional local folder (Windows)',value='')
selected=[]
if files: selected.extend((f.name,f.getvalue()) for f in files)
if folder.strip():
    base=Path(folder.strip())
    if base.is_dir():
        selected.extend((f.name,f.read_bytes()) for f in sorted(base.iterdir()) if f.is_file() and f.suffix.lower() in SUPPORTED and f.stat().st_size<=MAX_BYTES)
    else: st.warning('Folder not found.')
if 'results' not in st.session_state: st.session_state.results=[]
if st.button('Classify selected files',disabled=not bool(selected)):
    if not key: st.error('Enter a Gemini API key.')
    else:
        st.session_state.results=[]
        progress=st.progress(0);message=st.empty();elapsed=[]
        for index,(name,data) in enumerate(selected):
            remaining=(sum(elapsed)/len(elapsed))*(len(selected)-index) if elapsed else None
            message.write(f'Processing {index+1}/{len(selected)}: {name} | ETA: {remaining:.0f}s' if remaining is not None else f'Processing {index+1}/{len(selected)}: {name}')
            result=classify(name,data,key,model)
            st.session_state.results.append(result)
            elapsed.append(result['Seconds']);progress.progress((index+1)/len(selected))
        message.success('Batch finished. Review statuses for failures.')
if st.session_state.results:
    frame=pd.DataFrame(st.session_state.results)
    st.dataframe(frame.rename(columns={'Specifics':'What specifics made the POC decide the category','Status':'Processing Status'}),use_container_width=True)
    st.download_button('Export results CSV',frame.to_csv(index=False).encode('utf-8'),'smartdocmanager_results.csv','text/csv')
