"""Unofficial Zepp/Huami cloud access.

UNOFFICIAL: the Zepp/Huami app-API is not a public product; endpoints change
without notice. Personal use only. Credentials stay in process memory and are
never logged.

This provider is a scaffold: plug in a community Zepp client library (several
exist as `zepp-os` / huami-style packages on PyPI) and map its calls onto the
DayMetrics dict below. The HTTP surface (`main.py`) is already final, so adding
the library only touches this file.
"""
import logging
import secrets

from fastapi import HTTPException

log = logging.getLogger("pulse.zepp")
log.setLevel(logging.WARNING)


def _empty_day(day: str) -> dict:
    return {
        "date": day,
        "resting_hr": None,
        "spo2_avg": None,
        "resp_rate_avg": None,
        "sleep_start": None,
        "sleep_end": None,
        "stages": [],
        "steps": None,
        "body_battery": None,
        "hrv_rmssd": None,
    }


class ZeppProvider:
    """In-memory wrapper. One instance per authenticated session."""

    sessions = {}

    def __init__(self, client=None):
        self.client = client
        self.token = None

    @classmethod
    def for_token(cls, token: str) -> "ZeppProvider":
        p = cls.sessions.get(token)
        if p is None:
            raise HTTPException(401, "no such gateway session; re-login")
        return p

    def login(self, email: str, password: str) -> bool:
        # Replace with the community library's login, e.g.:
        #   self.client = ZeppUser(email=email, password=password, country="us")
        #   self.client.login()
        raise HTTPException(501, "Zepp provider not wired to a client library yet")

    def session_token(self) -> str:
        return self.token

    def day(self, day: str) -> dict:
        try:
            s = self.client.get_device_data(day)
        except Exception:
            log.warning("zepp day fetch failed; returning empty payload")
            return _empty_day(day)
        out = _empty_day(day)
        out.setdefault("resting_hr", s.get("_hr_resting", {}).get("value"))
        out.setdefault("steps", s.get("_steps", {}).get("value"))
        return out

    def sleep(self, day: str) -> dict:
        try:
            s = self.client.get_device_data(day).get("_sleep", {})
        except Exception:
            s = {}
        stages = []
        for kind in ("deep", "light", "rem", "awake"):
            if kind in s.get("stage", {}):
                stages.append({"type": kind, "seconds": s["stage"][kind].get("seconds", 0)})
        return {
            "date": day,
            "sleep_start": s.get("start"),
            "sleep_end": s.get("stop"),
            "stages": stages,
        }
