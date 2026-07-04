# Grammar provenance

The `KrlLexer.g4` + `KrlParser.g4` files in this directory are
**original work** under the MIT licence of this plugin.

## Reuse survey (2026-05-30)

Per the design doc `aidocs/integrations/117-krl-interpreter.md §2.1`
the preferred reuse target was `pykrlparser` (MIT, ANTLR4). At the
time of writing (2026-05-30) we could not locate a public
`pykrlparser` repository on GitHub or PyPI — the design-doc table
references the name but no canonical URL was reachable.

The closest match we surveyed:

| Candidate                                                              | Commit / fetched     | Licence              | Verdict                                                                                |
| ---------------------------------------------------------------------- | -------------------- | -------------------- | -------------------------------------------------------------------------------------- |
| [`Roiki11/KRLparser`](https://github.com/Roiki11/KRLparser)            | `master` 2021-06-02  | **no licence file**  | Cannot reuse — no licence = no permission. ABNF reference `krl_bnf.ebnf` is informative only. |
| [`andre-shap/krlParser`](https://github.com/andre-shap/krlParser)      | n/a — no `.g4`       | n/a                  | PyQt5 desktop app, no grammar artefact.                                                |

Both repos surfaced from `gh search repos krl parser python`.

**Decision.** Per the CLAUDE.md "DO NOT introduce a non-MIT / BSD /
Apache parser dep" guard and the design doc §2.1 fallback ("Rolling
fresh ANTLR4 from the KUKA KRL 5.x reference manual"), we wrote the
grammar from scratch, informed by:

1. The public-domain ABNF in `Roiki11/KRLparser/krl_bnf.ebnf`
   (we read it as a *structural reference*; no grammar text was
   copied — ANTLR4 syntax differs from ABNF and we made independent
   coverage choices per `aidocs/integrations/117 §4`).
2. The publicly-redistributable subset of the KUKA System Software
   8.x KRL Reference Manual (cited section numbers in
   `aidocs/integrations/117 §4`).

## Regeneration

The generated Python files in `generated/` are produced by:

```bash
cd plugins/krl-interpreter/krl_interpreter/parser/grammar
java -jar antlr-4.13.2-complete.jar \
  -Dlanguage=Python3 \
  -o generated -Xexact-output-dir \
  KrlLexer.g4 KrlParser.g4
```

The `antlr4-python3-runtime` version in `pyproject.toml` must match
the major.minor of the tool jar above.

## Upstream contribution

If a maintained `pykrlparser` repository surfaces, the tier-1
grammar extensions listed in `aidocs/integrations/117 §2.1` should be
filed as upstream PRs and this grammar archived in favour of the
canonical one.
