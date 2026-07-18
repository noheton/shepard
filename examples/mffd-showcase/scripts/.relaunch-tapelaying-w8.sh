#!/usr/bin/env bash
# v16.8 UPLOAD-FANOUT relaunch — same run, resumes from state file, 8 upload workers.
set -a
source /opt/shepard/examples/mffd-showcase/scripts/.env.local
set +a
cd /opt/shepard/examples/mffd-showcase/scripts
export DATA_DIR='/mnt/pve/unas/dump/dataset/cube3-export/mffd-export/ts-export'
export SESSION_ID='tapelaying-20260710b'
export MFFD_UPLOAD_WORKERS=8
exec ./mffd-runner.sh --workers 4 \
  --default-license "${MFFD_DEFAULT_LICENSE:-proprietary}" \
  --default-access-rights "${MFFD_DEFAULT_ACCESS_RIGHTS:-restricted access}" \
  2>&1 | tee -a /tmp/mffd-ingest-tapelaying-20260710b-w8.log
