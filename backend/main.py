"""Personal-use FastAPI gateway exposing unofficial Garmin / Zepp data to Pulse.

DO NOT run this on shared infrastructure. These wrappers hit *unofficial*
endpoints and exist only so a hobbyist can read their own Garmin/Zepp data
through Pulse.

Auth model:
- `PULSE_GATEWAY_TOKEN` (env): static bearer token the Android app must present.
- Login endpoints return a session id; app calls carry it in `X-Session`.
"""
import os
from datetime import date, timedelta
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel

from providers.garmin import GarminProvider
from providers.zepp import ZeppProvider

app = FastAPI(title="Pulse personal gateway", version="0.1.0")

GATEWAY_TOKEN = os.environ.get("PULSE_GATEWAY_TOKEN", "")


class LoginRequest(BaseModel):
    email: str
    password: str


class DayMetrics(BaseModel):
    date: str
    resting_hr: Optional[float] = None
    hrv_rmssd: Optional[float] = None
    spo2_avg: Optional[float] = None
    resp_rate_avg: Optional[float] = None
    sleep_start: Optional[str] = None
    sleep_end: Optional[str] = None
    stages: list = []
    steps: Optional[int] = None
    body_battery: Optional[int] = None


def require_token(authorization: str = Header(default="")) -> None:
    if not GATEWAY_TOKEN:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "gateway has no PULSE_GATEWAY_TOKEN set")
    if authorization != f"Bearer {GATEWAY_TOKEN}":
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "bad gateway token")


def session_token(x_session: str = Header(default="")) -> str:
    if not x_session:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "missing X-Session header")
    return x_session


@app.get("/health")
def health():
    return {"ok": True}


# ---------------------------------------------------------------- Garmin ----

@app.post("/garmin/login")
def garmin_login(req: LoginRequest, _: None = Depends(require_token)):
    provider = GarminProvider()
    dl_token = provider.login(req.email, req.password)
    if not dl_token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Garmin rejected the credentials")
    return {"session": dl_token}


@app.get("/garmin/day/{day}", response_model=DayMetrics)
def garmin_day(day: str, _: None = Depends(require_token), s: str = Depends(session_token)):
    return GarminProvider.for_token(s).day(day)


@app.get("/garmin/hrv/{day}", response_model=DayMetrics)
def garmin_hrv(day: str, _: None = Depends(require_token), s: str = Depends(session_token)):
    return GarminProvider.for_token(s).hrv(day)


@app.get("/garmin/sleep/{day}", response_model=DayMetrics)
def garmin_sleep(day: str, _: None = Depends(require_token), s: str = Depends(session_token)):
    return GarminProvider.for_token(s).sleep(day)


@app.get("/garmin/backfill", response_model=list[DayMetrics])
def garmin_backfill(days: int = 30, _: None = Depends(require_token), s: str = Depends(session_token)):
    provider = GarminProvider.for_token(s)
    start = date.today() - timedelta(days=min(days, 90))
    out, d = [], start
    while d <= date.today():
        out.append(provider.day(d.isoformat()))
        d += timedelta(days=1)
    return out


# ----------------------------------------------------------------- Zepp -----

@app.post("/zepp/login")
def zepp_login(req: LoginRequest, _: None = Depends(require_token)):
    provider = ZeppProvider()
    zp_token = provider.login(req.email, req.password)
    if not zp_token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Zepp rejected the credentials")
    return {"session": zp_token}


@app.get("/zepp/day/{day}", response_model=DayMetrics)
def zepp_day(day: str, _: None = Depends(require_token), s: str = Depends(session_token)):
    return ZeppProvider.for_token(s).day(day)


@app.get("/zepp/sleep/{day}", response_model=DayMetrics)
def zepp_sleep(day: str, _: None = Depends(require_token), s: str = Depends(session_token)):
    return ZeppProvider.for_token(s).sleep(day)


@app.get("/zepp/backfill", response_model=list[DayMetrics])
def zepp_backfill(days: int = 30, _: None = Depends(require_token), s: str = Depends(session_token)):
    provider = ZeppProvider.for_token(s)
    start = date.today() - timedelta(days=min(days, 90))
    out, d = [], start
    while d <= date.today():
        out.append(provider.day(d.isoformat()))
        d += timedelta(days=1)
    return out
