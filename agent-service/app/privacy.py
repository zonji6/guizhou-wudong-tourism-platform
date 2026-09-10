import re
from collections.abc import Mapping

from pydantic import ValidationError

from app.v3_contracts import PersistentStateV3


_PHONE = re.compile(r"(?<!\d)(?:\+?86[-\s]?)?1[3-9]\d(?:[-\s]?\d){8}(?!\d)")
_CREDENTIAL = re.compile(r"(?i)\b(?:bearer\s+\S+|(?:sk|api)[-_][A-Za-z0-9_-]{8,})")
_SENSITIVE = {
    "authorization",
    "cookie",
    "contactname",
    "contactphone",
    "exception",
    "messages",
    "password",
    "prompt",
    "raw",
    "secret",
    "stack",
    "token",
    "usertext",
    "visitorid",
}


class SensitiveFieldError(ValueError):
    pass


def _key(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def sanitize_public_text(value: str, max_length: int = 1000) -> str:
    value = _PHONE.sub("[已隐藏手机号]", value)
    value = _CREDENTIAL.sub("[已隐藏凭据]", value)
    value = " ".join(value.split())
    return value if len(value) <= max_length else value[: max_length - 1].rstrip() + "…"


def _reject_sensitive(value: object) -> None:
    if isinstance(value, Mapping):
        for key, child in value.items():
            if not isinstance(key, str) or _key(key) in _SENSITIVE:
                raise SensitiveFieldError("待持久化状态包含禁止字段")
            _reject_sensitive(child)
    elif isinstance(value, list):
        for child in value:
            _reject_sensitive(child)


def sanitize_checkpoint_payload(payload: Mapping[str, object]) -> dict[str, object]:
    _reject_sensitive(payload)
    try:
        state = PersistentStateV3.model_validate(payload, strict=True)
    except ValidationError:
        raise SensitiveFieldError("待持久化状态不符合 v3 白名单") from None
    cleaned = state.model_dump(mode="json", by_alias=True)
    _reject_sensitive(cleaned)
    return cleaned
