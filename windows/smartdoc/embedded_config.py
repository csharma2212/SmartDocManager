"""Embedded Encrypted Configuration Store
Stores necessary configuration values (backend URL, desktop client ID, desktop client secret, web client ID)
in an encrypted payload. Decrypted in-memory at runtime with zero external dependencies.
"""
import base64
import hashlib
import hmac
import json

_SEED = "SmartDocManager_Secure_Embedded_Store_v1_2026"
_PAYLOAD = (
    "3LlwsFqRvlNZm3eJTcTUpuqhKlust+tdqm3+0zvKMWez5N2kK3HeVkEuW+gZQrgB6JROPuBZCI4HDD4EzjjPpP"
    "FCVOC2dNB8iUCnaRAoBWYHBHisTtqek7K7LAzDIxUxxGUeGVRp8hytel8PEC7nmOM24FArV/Z7yEW9jVT5Oe9sr"
    "2c18yqmpcGOSyD03c4UmLwOfZ3zu8+JFr79DGPjTaSTG7xmumVDqUBm7NzDut9X9tEmivgQjWx1y2vl2D10sKQb"
    "DomoXEPbg3tccRpbU1vzsPwMLVtPJgt5uSESCDLkhp8yNZ+E0GnFIebJ6LcE7mGxCV9RQ/cCclU50nmyFwcK0vjF"
    "DD+GF0myTBsYdlEQSJCy55HJgVdQbJbvXZA2MHIuUDVa9tooJKynY2xCmvbIENQhd9vPCFKbtubdWUcHhu7Z4q8S"
    "u3aB4xBALqZaFnCR0c+ipcxf"
)

def _generate_keystream(key: bytes, length: int) -> bytes:
    stream = bytearray()
    counter = 0
    while len(stream) < length:
        h = hmac.new(key, counter.to_bytes(4, 'big'), hashlib.sha256).digest()
        stream.extend(h)
        counter += 1
    return bytes(stream[:length])

def get_embedded_config() -> dict:
    """Decrypts and returns the embedded application secrets in memory."""
    try:
        encrypted = base64.b64decode(_PAYLOAD.encode("ascii"))
        salt = hashlib.sha256((_SEED + "_salt_smartdoc").encode("utf-8")).digest()
        key = hashlib.pbkdf2_hmac("sha256", _SEED.encode("utf-8"), salt, iterations=50000, dklen=32)
        ks = _generate_keystream(key, len(encrypted))
        raw = bytes(b ^ k for b, k in zip(encrypted, ks))
        return json.loads(raw.decode("utf-8"))
    except Exception:
        return {}

def encrypt_config(data: dict) -> str:
    """Helper utility to produce an encrypted base64 payload for embedding."""
    raw = json.dumps(data).encode("utf-8")
    salt = hashlib.sha256((_SEED + "_salt_smartdoc").encode("utf-8")).digest()
    key = hashlib.pbkdf2_hmac("sha256", _SEED.encode("utf-8"), salt, iterations=50000, dklen=32)
    ks = _generate_keystream(key, len(raw))
    encrypted = bytes(b ^ k for b, k in zip(raw, ks))
    return base64.b64encode(encrypted).decode("ascii")
