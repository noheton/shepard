# BT-KVS docket showcase — quick start

```bash
python3 seed.py       --host https://shepard-api.nuclide.systems --apikey <token>
python3 seed_vocab.py --host https://shepard-api.nuclide.systems --apikey <token> \
                      --source /path/to/laufzettel-readout/src

# BTKVS-B2 — register the docket :general form template (SHACL shape via the
# builder DSL; verified through GET /v2/templates/{appId}/form):
python3 seed_form_template.py --host https://shepard-api.nuclide.systems --apikey <admin-token>

# Render + submit the form (Streamlit; 422 violations[] become inline field errors):
streamlit run form_demo.py -- --host https://shepard-api.nuclide.systems \
    --apikey <token> --template <templateAppId> --collection <collectionAppId>
python3 form_demo.py --selftest   # headless check, no network needed
```

Full narrative: [`SHOWCASE.md`](SHOWCASE.md). Idempotent; `--reset` recreates.
Form-template design: [`aidocs/integrations/125`](../../aidocs/integrations/125-btkvs-shacl-form-templates.md).
