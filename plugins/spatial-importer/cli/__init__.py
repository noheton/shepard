"""shepard-plugin-spatial-importer — promote opaque FileReferences into
SpatialDataContainer rows on the existing spatiotemporal substrate.

MFFD W7 wave. See aidocs/integrations/113-mffd-real-data-import-plan.md §W7
and aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md §GAP-5.

This is the *companion* importer to the v15 file/timeseries importer that
landed the raw files in W2. The v15 pass produces opaque FileReferences;
this pass reads them, parses the two ASCII formats (TPS 3D pointclouds and
FSD course 3D pointclouds), and creates `SpatialDataContainer`s that the
existing `vis-trace3d` plugin can render.
"""

__version__ = "1.0.0"
