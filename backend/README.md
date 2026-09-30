# LAIA private brain backend

This folder documents the server-side contract for LAIA v3.

## Privacy rules
- Never put LLM API keys, database credentials, system prompts, or private memories in index.html.
- Keep secrets only in server-side environment variables.
- Keep long-term memory server-side and tied to the authenticated owner.
- Restrict CORS to the LAIA frontend origin.
- Add authentication before enabling personal memory.

## Chat contract
POST /api/chat

Request:
```json
{"message":"Hallo Laia","sessionId":"browser-session-id"}
```

Response:
```json
{"reply":"Hey :)","mood":"happy"}
```

The frontend currently leaves BRAIN_ENDPOINT empty and safely falls back to the local demo replies until a private backend is deployed.
