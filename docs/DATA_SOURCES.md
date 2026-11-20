# Experimental Garmin / Zepp backends

These gateways are **unofficial, personal-use prototypes**. Neither Garmin nor Zepp
publishes a free open consumer API with intraday data; the unofficial endpoints these
wrappers hit can change or be rate-limited/blocked at any time. Do not ship this in
anything user-facing.

- Official Garmin alternative: the Garmin Health API is a certified partner program
  (OAuth + push/ping webhooks). It requires Garmin developer approval — see
  https://www.garmin.com/en-US/business/health/.
- Credentials/tokens received by the gateway are held in memory only for the session
  and must never be logged. The Android side stores its gateway token encrypted.
- Run the gateway only on infrastructure you control (localhost tunnel or your own
  VPS). The Android app is the only intended client.

## Layout

```
backend/
├── main.py          # FastAPI app
├── providers/
│   ├── garmin.py    # wraps the unofficial 'garminconnect' library
│   └── zepp.py      # wraps the unofficial Zepp/Huami cloud API
└── requirements.txt
```

## Running

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8000
```

Then in the Android app: Settings → backend URLs → `http://<your-host>:8000`.

The Android app will then show both providers on the Connectivity screen with a
clear "experimental / unofficial" warning badge.
