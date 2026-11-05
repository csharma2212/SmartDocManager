import os, sys, time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import pandas as pd
import streamlit as st
from smartdoc.core import classify, SUPPORTED, MAX_BYTES

st.set_page_config(page_title='SmartDoc POC',layout='wide')
st.title('SmartDoc document classifier')
st.caption('Cloud classification: selected document images are transmitted to Google Gemini. Use synthetic, redacted or authorized documents only.')
if not st.session_state.get('cloud_ack',False):
    st.warning('Gemini free-tier data handling may not meet confidentiality requirements. Do not submit sensitive or company-controlled documents without authorization.')
    if st.button('I understand and will use approved test documents'):
        st.session_state.cloud_ack=True;st.rerun()
    st.stop()
key=st.text_input('Gemini API key',type='password',value=os.environ.get('GEMINI_API_KEY',''))
model=st.text_input('Gemini model ID (verify free-tier eligibility in AI Studio)',value=os.environ.get('GEMINI_MODEL','gemini-2.5-flash'))
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
    st.download_button('Export results CSV',frame.to_csv(index=False).encode('utf-8'),'smartdoc_results.csv','text/csv')
