# BT-KVS docket showcase — quick start

```bash
python3 seed.py       --host https://shepard-api.nuclide.systems --apikey <token>
python3 seed_vocab.py --host https://shepard-api.nuclide.systems --apikey <token> \
                      --source /path/to/laufzettel-readout/src
```

Full narrative: [`SHOWCASE.md`](SHOWCASE.md). Idempotent; `--reset` recreates.
