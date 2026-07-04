---
title: Browse and download HDF5 datasets
description: How to create an HDF5 container, explore datasets via the browser or h5pyd, and download an offline copy of your data
permalink: /help/browse-hdf-datasets/
layout: default
audience: user
---
# Browse and download HDF5 datasets

Shepard's **HDF container** stores HDF5 scientific data using an HSDS
(Highly Scalable Data Service) sidecar. Once your administrator has enabled
the HDF feature, you can create HDF containers, attach them to DataObjects,
write and read datasets, and download the data as a standard `.h5` file.

> **Is HDF enabled on your instance?**  If the **Add container** menu does not
> show an "HDF5" option, contact your administrator.  The feature requires the
> `hdf` compose profile to be running (see `plugins/hdf5/docs/install.md`).

---

## Create an HDF container

1. Open a **DataObject** detail page.
2. In the **Containers** panel, click **Add container → HDF5**.
3. Enter a name and optional description, then click **Create**.

Shepard creates an `HdfContainer` and provisions an HSDS domain at
`/shepard/<containerAppId>/` automatically.  The container's appId is visible
in the detail panel and in the URL.

---

## Write data with h5pyd

Use the Python `h5pyd` library to write datasets from your local machine or a
Jupyter notebook.

```python
import h5pyd
import numpy as np

HSDS_ENDPOINT = "http://<your-shepard-host>:5101"  # ask your admin for the URL
HSDS_USERNAME = "admin"
HSDS_PASSWORD = "<hsds-password>"
DOMAIN = "/shepard/<containerAppId>/"  # from the container detail page

with h5pyd.File(DOMAIN, "a",
                endpoint=HSDS_ENDPOINT,
                username=HSDS_USERNAME,
                password=HSDS_PASSWORD) as f:
    f.create_dataset("accelerometer/z_axis", data=np.random.randn(10_000))
    f["accelerometer/z_axis"].attrs["unit"] = "g"
    f["accelerometer/z_axis"].attrs["sample_rate_hz"] = 10_000
```

The standard `h5pyd` API works identically to `h5py` — swap the import,
add the connection parameters, and your existing analysis scripts work unchanged.

---

## Attach a dataset path as a reference

An **HdfReference** anchors a specific dataset path inside an HDF container to
a DataObject.  This lets you link "the `accelerometer/z_axis` dataset in
container `TR-004-accel`" as a named reference alongside other references.

1. Open the DataObject detail page.
2. In the **References** panel, click **Add reference → HDF reference**.
3. Select the HDF container, enter the dataset path (e.g.
   `accelerometer/z_axis`), and optionally enter a description.
4. Click **Save**.

The reference appId can be used by downstream tools (MCP, REST API) to
retrieve the dataset directly without knowing the container appId.

---

## Download as an offline .h5 file

Shepard exports any HDF container as a byte-identical `.h5` file:

1. Open the **HDF container** detail page.
2. Click **Download as HDF5** (top right).

The file downloads directly to your browser.  You can open it with `h5py`,
HDFView, or MATLAB without any changes.

```python
import h5py

with h5py.File("TR-004-accel.h5", "r") as f:
    data = f["accelerometer/z_axis"][:]
    unit = f["accelerometer/z_axis"].attrs["unit"]
    print(f"Shape: {data.shape}, unit: {unit}")
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| "HDF5" not listed in Add container menu | HDF feature is disabled | Ask your admin to enable the `hdf` compose profile and config keys |
| `h5pyd` connection refused | HSDS sidecar not reachable from your machine | Verify the HSDS endpoint URL and port with your admin; you may need a VPN |
| `h5pyd` authentication error | Wrong username or password | Check the credentials with your admin — they are set in `docker compose .env` |
| Download returns empty file | The HDF container has no datasets yet | Write at least one dataset first with `h5pyd` |

---

## Related help

- [Upload data](/help/upload-data/) — store a pre-existing `.h5` file as a FileReference instead
- [Annotating data](/help/annotating-data/) — tag your HDF container with semantic metadata
- [Work with notebooks](/help/work-with-notebooks/) — run h5pyd analysis inside JupyterHub
