"""FastAPI sidecar for the KRL interpreter (KRL-INTERPRETER-04).

The sidecar is the deployment surface for `shepard-plugin-krl-interpreter`:
a Python REST service that takes a KRL ``.src`` (plus optional ``.dat``)
plus a URDF path, parses + IK-solves the program, and returns a joint
trajectory.

The Shepard backend (KRL-INTERPRETER-05) reaches this sidecar via
internal compose DNS at ``http://krl-interpreter-sidecar:8000``; the
sidecar exposes no host-side port (operator opt-in only).

Public modules:
- :mod:`krl_interpreter.sidecar.app` - the FastAPI app + endpoints.
- :mod:`krl_interpreter.sidecar.composer` - IR -> joint trajectory.
- :mod:`krl_interpreter.sidecar.schemas` - Pydantic v2 request / response.
- :mod:`krl_interpreter.sidecar.config` - env-driven settings.

See ``aidocs/integrations/117-krl-interpreter.md §6`` for the sidecar
protocol contract this implements.
"""

from krl_interpreter.sidecar.composer import (  # noqa: F401
    Composer,
    ComposerOptions,
    ComposerResult,
    TrajectoryPoint,
)
from krl_interpreter.sidecar.schemas import (  # noqa: F401
    FramePayload,
    InterpretRequest,
    InterpretResponse,
    InterpretWarning,
    InterpretUnsupported,
    IkSolverStats,
    InterpretOptions,
    TrajectorySample,
)
