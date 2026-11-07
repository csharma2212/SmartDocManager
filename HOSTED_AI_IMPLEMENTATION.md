# Hosted AI integration (implementation candidate)

The Windows and Android clients now support **Hosted AI** in addition to existing Gemini mode. This is **internet-based**, not offline inference. Android uses bundled ML Kit Latin OCR; Windows uses `pytesseract` and requires the separate Tesseract OCR executable on PATH. Both send **OCR text** (not the original document image) to a self-deployed HTTPS backend. The backend calls Groq using its private `GROQ_API_KEY` environment variable. The Groq model is configurable via `GROQ_MODEL`. No provider credentials are shipped in the clients.

## Backend setup

```sh
cd hosted-backend
python -m pip install -r requirements.txt
export GROQ_API_KEY='your-provider-key'
export SMARTDOC_CLIENT_TOKEN='long-random-access-token'
uvicorn app:app --host 127.0.0.1 --port 8080
```

For remote clients, deploy `hosted-backend/Dockerfile` behind HTTPS (e.g. Cloud Run), configure environment secrets in the hosting service, and enable authentication, request quotas, cost monitoring, abuse prevention, and appropriate retention/privacy policies. Cloud Run deployment, Groq quotas, and service availability have **not** been tested here. Do not deploy a public unrestricted endpoint. A shared client token embedded in a distributed app is **not secure authentication**; production use needs individual user authentication (e.g. OIDC), server-side quotas, and revocation. The current implementation is suitable for a private controlled pilot only.

Windows: `pip install -r windows/requirements.txt`, install Tesseract OCR executable, start Streamlit, choose Hosted AI and provide backend HTTPS URL and access token. Android: choose Hosted AI, enter HTTPS URL and token; OCR is bundled in the APK through ML Kit dependency. Model choice and Groq credentials live on the backend.

## Limitations and safety

- OCR is currently **first page only** for PDFs, inherited from the existing rendering pipeline; scanned handwriting and non-Latin scripts may fail.
- Uploaded OCR text can contain sensitive personal information and is sent to the backend and Groq. Explicit informed consent and an approved data-handling policy are required.
- The API accepts at most 16,000 characters; the client truncates beyond this limit. Classification confidence and automatic renaming thresholds are not provided.
- No fallback to a local LLM is implemented. This addition does not implement optional Android model installation.
- Free inference tiers are rate-limited, may change, and cannot guarantee free public usage.
- The backend has a basic shared bearer token but no rate limiting or per-user identity; do not distribute the token in public APKs.
- Tests should cover network failures, OCR accuracy, privacy requirements, and real-device APK build before release.
