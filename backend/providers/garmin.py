"""Unofficial Garmin access via the community 'garminconnect' library.

UNOFFICIAL: endpoints can break or be blocked at any time. Personal use only.
Credentials are never written to logs or to disk; the session object stays in
process memory only.
"""
import json
import logging
from datetime import date
from typing import Optional

from fastapi import HTTPException

log = logging.getLogger("pulse.garmin")
log.setLevel(logging.WARNING)  # never log request/response payloads

try:
    from garminconnect import Garmin
except ImportError:  # pragma: no cover
    Garmin = None


class GarminProvider:
    """In-memory wrapper. One instance per authenticated session."""

    sessions = {}

    def __init__(self, client=None):
        self.client = client
        self.token = None

    @classmethod
    def for_token(cls, token: str) -> "GarminProvider":
        p = cls.sessions.get(token)
        if p is None:
            raise HTTPException(401, "no such gateway session; re-login")
        return p

    def login(self, email: str, password: str):
        if Garmin is None:
            raise HTTPException(500, "garminconnect not installed on the gateway host")
        self.client = Garmin(email=email, password=password)
        try:
            self.client.login()
        except Exception:
            return False
        import secrets

        self.token = secrets.token_urlsafe(24)
        GarminProvider.sessions[self.token] = self
        return self.token

    def session_token(self) -> str:
        return self.token

    def _stats(self, day: str) -> dict:
        try:
            return self.client.get_stats(day) or {}
        except Exception:
            return {}

    def _sleep(self, day: str) -> dict:
        try:
            return (self.client.get_sleep_data(day) or {}).get("dailySleepDTO", {})
        except Exception:
            return {}

    def _hrv(self, day: str) -> dict:
        try:
            return self.client.get_hrv_data(day) or {}
        except Exception:
            return {}

    def day(self, day: str) -> dict:
        s, sl = self._stats(day), self._sleep(day)
        return {
            "date": day,
            "resting_hr": s.get("restingHeartRate"),
            "spo2_avg": (s.get("spo2Average") or {}).get("lastNightAvg") if isinstance(s.get("spo2Average"), dict) else s.get("spo2Average"),
            "resp_rate_avg": s.get("avgRespirationValue"),
            "sleep_start": sl.get("sleepStartTimestampGMT") and _iso(sl["sleepStartTimestampGMT"]),
            "sleep_end": sl.get("sleepEndTimestampGMT") and _iso(sl["sleepEndTimestampGMT"]),
            "stages": _stages(sl),
            "steps": s.get("totalSteps"),
            "body_battery": ((s.get("bodyBatteryMostRecentValue") or 0) if s.get("bodyBatteryMostRecentValue") is not None else None),
            "hrv_rmssd": None,
        }

    def hrv(self, day: str) -> dict:
        h = self._hrv(day)
        base = (h.get("hrvSummary") or {}).get("lastNightAvg")
        return {"date": day, "hrv_rmssd": base}

    def sleep(self, day: str) -> dict:
        return self.day(day)


def _iso(epoch_ms: int) -> str:
    from datetime import datetime, timezone

    return datetime.fromtimestamp(epoch_ms / 1000, tz=timezone.utc).isoformat()


def _stages(sleep_dto: dict) -> list:
    out = []
    for k, kind in (
        ("deepSleepSeconds", "deep"),
        ("lightSleepSeconds", "light"),
        ("remSleepSeconds", "rem"),
        ("awakeSleepSeconds", "awake"),
    ):
        secs = sleep_dto.get(k)
        if secs:
            out.append({"type": kind, "seconds": secs})
    return out
