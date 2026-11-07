# SmartDocManager hosted classification: deployment

The Android and Windows clients use the **same** API: `POST /v1/classify` with `Authorization: Bearer <SMARTDOC_CLIENT_TOKEN>` and JSON `{ "text": "OCR text" }`. The response is `{ "category": "Tax Document", "specifics": "Form 16" }`. `GET /health` is public.

## Option A — Cloudflare Workers (JavaScript, recommended for a small private pilot)

1. In `hosted-backend/cloudflare`, run `npm install` and `npm test` (Node.js 20+).
2. Sign into your Cloudflare account using `npx wrangler login`.
3. Set the Groq key with `npx wrangler secret put GROQ_API_KEY`.
4. Set a long random private access token with `npx wrangler secret put SMARTDOC_CLIENT_TOKEN`.
5. Run `npm run deploy`. Copy the HTTPS Workers URL into both apps' Hosted AI settings.
6. Set the same private token in the apps. Never put the Groq key in an app or GitHub repository.

The Worker uses Cloudflare's rate-limit binding (10 requests/minute per client IP). This is **not** user authentication, a per-user quota, or a guaranteed cost ceiling. Free-tier quotas, binding availability, and model availability must be checked in your Cloudflare/Groq accounts. The service needs internet access; it is not an offline model.

## Option B — Render (Python FastAPI)

1. Push this project to a private GitHub repository; create a Render Blueprint using `hosted-backend/render.yaml` (or create a Docker web service with the backend directory as context).
2. Set `GROQ_API_KEY` and `SMARTDOC_CLIENT_TOKEN` as private environment variables in Render. Do not commit `.env`.
3. Deploy, check `/health`, then configure the service HTTPS URL and the token in both applications.
4. The existing Python FastAPI service has bearer-token authentication but **does not yet implement robust per-user quotas**. Put an API gateway with rate limits in front of it before public distribution.

For Koyeb, deploy the existing `hosted-backend/Dockerfile` and set the same environment variables. Koyeb free-tier eligibility, capacity and availability may differ from Render.

## Privacy, security and operational limits

- OCR runs locally in hosted mode, but **extracted document text is sent to your backend and then to Groq**. This may contain highly sensitive identifiers, bank details, or health information. Obtain explicit user consent and review provider data handling policies before use.
- The shared bearer token is suitable only for a trusted private pilot. It can be extracted from a distributed client. Public release requires individual identity, abuse controls, request quotas, and preferably per-user revocable credentials.
- No user needs a personal Gemini account when hosted mode is selected. The **operator** still needs a Groq API key, and Groq usage limits apply.
- Do not log document contents, request bodies, or provider credentials. Rotate compromised tokens.
- The free hosting tiers are not guaranteed free forever or always-on. Groq model identifiers can change.
- Classification accuracy is not guaranteed. Validate results before any file rename, especially for low-confidence or ambiguous scans.

## Local tests

- Windows: `cd windows && python -m pytest -q tests`
- Cloudflare Worker: `cd hosted-backend/cloudflare && npm test`
- Android: `cd android && ./gradlew testDebugUnitTest` (requires a configured Android SDK and Gradle distribution).
- Python backend: start with `uvicorn app:app --host 127.0.0.1 --port 8000` from `hosted-backend` after installing `requirements.txt`; do not expose this local dev server publicly.

## Status and limitations

The Worker is implemented and tested with mocked Groq responses. No live Groq call, Cloudflare deployment, Render deployment, Android APK compilation, or Windows executable packaging is claimed. Existing Windows and Android source integrations are preserved. The complete document-management feature set and true on-device LLM inference are **not** delivered by this backend integration alone.
