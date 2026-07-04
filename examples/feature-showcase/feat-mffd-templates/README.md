# feat-mffd-templates

Keystone MFFD demonstrator. Instantiates the built-in V100 MFFD process
templates (`MFFD AFP Layup` / `Ultrasonic Welding` / `NDT Inspection`) into a
synthetic `material-batch → AFP×2 → ultrasonic weld → NDT(PASS)` chain with
typed Predecessor edges and `urn:shepard:mffd:material-batch` IRI back-references
on every step (the DIN EN 9100 traceability join). Synthetic — no real DLR/MFFD IP.

Run: `python seed.py --reset` (needs `API_KEY` + `BACKEND_URL`, or `--apikey`/`--host`).

RESEED-FIND: `from-template` instantiation can't carry `typedPredecessors`, and a
post-hoc PATCH of `typedPredecessors` is silently ignored — predecessors are only
honoured at create time. The chain is therefore built from plain DataObjects with
create-time `typedPredecessors`; one standalone from-template instance still proves
the template picker path.
