"""
shepard — convenience wrapper around the generated ``shepard-client`` package.

Quick start::

    import shepard

    sh = shepard.Client(
        host="https://backend.shepard.example.com/shepard/api",
        apikey="sk-...",
    )
    # Domain proxies mirror the generated *Api classes:
    collections = sh.collections.get_all_collections()

    # Typed models live in shepard.models (re-exported from shepard_client):
    from shepard.models import Collection
    created = sh.collections.create_collection(
        collection=Collection(name="My Dataset")
    )

See https://github.com/noheton/shepard for full documentation.
"""

from shepard.client import Client
from shepard.errors import (
    ShepardBadRequest,
    ShepardConflict,
    ShepardError,
    ShepardForbidden,
    ShepardNotFound,
    ShepardServerError,
    ShepardUnauthorized,
    ShepardValidation,
)

# Re-export generated models as ``shepard.models`` so users can write
# ``from shepard.models import Collection`` without knowing the generated
# package name.
try:
    import shepard_client.models as models
except ImportError:
    models = None  # type: ignore[assignment]

__all__ = [
    "Client",
    "ShepardError",
    "ShepardBadRequest",
    "ShepardUnauthorized",
    "ShepardForbidden",
    "ShepardNotFound",
    "ShepardConflict",
    "ShepardValidation",
    "ShepardServerError",
    "models",
]
