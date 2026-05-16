# aidocs/76 — shepard: Known users, citations, and ecosystem

**Date:** 2026-05-16
**Status:** Research snapshot — update as new citations or deployments are found.
**Audience:** Contributors, PI, DFG proposal authors, potential new adopters.
**Purpose:** Catalogue confirmed academic citations, institutional users, and the
ecosystem of tools built on top of shepard. Useful as evidence in DFG proposals
(Bedarfsanalyse), upstream merge proposals, and stakeholder conversations.

Research method: searches of elib.dlr.de, Zenodo, Helmholtz Research Software
Directory, DGLR proceedings, Semantic Scholar, SCITEPRESS, and HMC resources.
Last searched: 2026-05-16.

---

## 1. Canonical citable record

| Field | Value |
|---|---|
| Title | "shepard – storage for heterogeneous product and research data" |
| Authors | Haase, T.; Glück, R.; Kaufmann, P.; Willmeroth, M. |
| Year | 2021 |
| Type | Software paper + Zenodo deposit |
| DOI | [10.5281/zenodo.5091603](https://doi.org/10.5281/zenodo.5091604) |
| elib | https://elib.dlr.de/143136/ |
| Notes | All downstream papers cite this record. Primary identifier for the project. |

The Helmholtz Research Software Directory entry
(https://helmholtz.software/software/shepard) links to this record.
Registration date: 2023-04-19. Keywords: "Aeronautics, Space and Transport",
"Industry 4.0". Shows 5 contributors, 5 external mentions.

---

## 2. Peer-reviewed conference papers

### 2.1 ETFA 2022 — Hanke et al. (Universität Augsburg)

> **Hanke, J.; Eymüller, C.; Reichmann, J.; Trauth, A.; Sause, M.; Reif, W. (2022).**
> "Software-defined testing facility for component testing with industrial robots."
> *27th IEEE International Conference on Emerging Technologies and Factory Automation
> (ETFA 2022)*, Stuttgart, Sep 2022, pp. 1–8.
> DOI: [10.1109/ETFA52439.2022.9921625](https://doi.org/10.1109/ETFA52439.2022.9921625)

**Significance:** The only confirmed citation from a **non-DLR institution**.
Authors are from Universität Augsburg (Faculty of Applied Informatics, Institut für
Software & Systems Engineering; Institut für Materials Resource Management). The paper
uses shepard as the unified data platform for a robotic component testing bench,
building a "software-defined testing facility" that writes structured test results
and robot trajectory data directly into shepard. Listed as a "mention" in the
Helmholtz RSD.

### 2.2 ICINCO 2023 — Vistein et al. (DLR ZLP)

> **Vistein, M.; Mayer, M.; Endraß, M.; Fischer, F. (2023).**
> "Single Source of Truth: Integrated Process Control and Data Acquisition System
> for the Development of Resistance Welding of CFRP Parts."
> *20th International Conference on Informatics in Control, Automation and Robotics
> (ICINCO 2023)*, Rome, Nov 2023, pp. 592–599.
> DOI: [10.5220/0012161500003543](https://doi.org/10.5220/0012161500003543)

**Significance:** Introduces **ProcessControl** and **ProcessMonitoring** — two
open-source SCADA systems described as "the first SCADA software built on top of
shepard." Uses shepard as the data persistence layer for MFFD (Multi-Functional
Fuselage Demonstrator) resistance welding of CFRP parts within Clean Sky 2 / AIRFRAME
ITD.

---

## 3. DGLR conference papers

The *Deutscher Luft- und Raumfahrtkongress* (DGLR) is the primary German aerospace
research conference with peer-reviewed proceedings indexed by DGLR and DLR elib.

| Year | Authors | Title (short) | DOI | Domain |
|---|---|---|---|---|
| 2021 | Krebs F., Willmeroth M., Haase T., Kaufmann P., Glück R., Deden D., Brandt L., Mayer M. | "Systematische Erfassung, Verwaltung und Nutzung von Daten aus Experimenten" | [10.25967/550315](https://doi.org/10.25967/550315) | AFP automated fibre placement |
| 2022 | Dressel F., Rädel M., Weinert A., Struck M.C., Haase T., Otten M. | "Common Source & Provenance at Virtual Product House" | [10.25967/570066](https://doi.org/10.25967/570066) | VPH multi-stakeholder workflow |
| 2023 | Haase T., Glück R., Görick D., Kaufmann P., Krebs F., Mayer M. | "Towards a Closed-Loop Data Collection and Processing Ecosystem" | [10.25967/610241](https://doi.org/10.25967/610241) | Thermoplastic AFP production |
| 2025 | Vistein M., Nieberl D., Kaufmann P., Buchheim A. | "Data Management For Quality Assurance in Semi-Automated Processes" | [10.25967/650226](https://doi.org/10.25967/650226) | CFRP helicopter part manufacturing QA |

---

## 4. HMC contributions and grey literature

### 4.1 HMC conference presentations

| Year | Authors | Title | DOI | Notes |
|---|---|---|---|---|
| 2023 | Vinot M., Unger N., Kamble P., Glück R., Toso N. | "Project MEMAS: ontology-based storage system for manufacturing and simulation data in the field of composite materials" | [10.5281/zenodo.10074677](https://doi.org/10.5281/zenodo.10074677) | HMC Conference 2023; elib: dlr.de/198099 |
| 2025 | Kamble P., Unger N., Vinot M., Glück R. | "Project MEMAS: A Framework for FAIR Data Storage in Composite Engineering" | [10.5281/zenodo.15482075](https://doi.org/10.5281/zenodo.15482075) | HMC Conference 2025, Cologne; elib: dlr.de/215508 |

**MEMAS** is a funded HMC (Helmholtz Metadata Collaboration) project at DLR ZLP
that explicitly uses shepard as the RDMS:
https://helmholtz-metadaten.de/en/inf-projects/memas

### 4.2 SAMPE 2022

> **Endraß, M.; Engelschall, M.; Mayer, M.; et al. (2022).**
> "ROBUST ASSEMBLY – Quality Assured Welding Technologies for Full-Scale Applications."
> *SAMPE Europe Conference 2022*, Hamburg.
> elib: https://elib.dlr.de/192159/

Describes shepard as "the superordinate quality assurance data system" for MFFD
upper shell welding assembly (Clean Sky 2).

### 4.3 Software and version deposits on elib

| Item | Authors | Year | elib |
|---|---|---|---|
| shepard Version 5.1.2 (software artifact) | Glück R., Kaufmann P., Krebs F., Lettowsky F.J., Vistein M. | 2025 | https://elib.dlr.de/220900/ |
| shepard Process Wizard v1.0.0 | Vistein M., Haase T., Kaufmann P. | 2025 | https://elib.dlr.de/218187/ |

---

## 5. Confirmed user institutions

| Institution | Role | Evidence |
|---|---|---|
| **DLR-ZLP** (Zentrum für Leichtbauproduktionstechnologie, Augsburg) | Primary developer and largest user. AFP, MFFD, resistance welding, robot testing. | All DGLR/HMC papers; Helmholtz RSD entry; project pages |
| **DLR-BT** (Institut für Bauweisen und Strukturtechnologie, Stuttgart) | User via ADAPT project (hydrogen propulsion digitalization, 2022–2025) | DLR ADAPT project page https://www.dlr.de/en/bt/research-transfer/projects/ongoing-projects/adapt |
| **DLR-Institute of Software Methods for Product Virtualization** | User via Virtual Product House (VPH); developed RCE2Shepard adapter | Dressel et al. DGLR 2022 |
| **DLR-SA / multiple institutes** | ADAPT project spans aerodynamics, aeroelasticity, combustion, structures | ADAPT page lists DLR-AS, -AT, -BT, -TT |
| **Universität Augsburg** (Fak. Informatik + Materials Res. Management) | External user; robotic component testing bench | Hanke et al. ETFA 2022 |

No confirmed deployments at institutions outside DLR and Universität Augsburg were
found as of this research date.

---

## 6. Ecosystem tools built on or coupled to shepard

| Tool | Authors | Description | Evidence |
|---|---|---|---|
| **ProcessControl** | Vistein M., DLR ZLP | Open-source SCADA system built on top of shepard for CFRP welding process control | ICINCO 2023 paper |
| **ProcessMonitoring** | Vistein M., DLR ZLP | Open-source monitoring dashboard built on top of shepard; used alongside ProcessControl | ICINCO 2023 paper |
| **shepard Process Wizard (spw)** | Vistein M., Haase T., Kaufmann P. | GUI tool for modelling and running experiment processes that stores results in shepard | elib.dlr.de/218187 |
| **RCE2Shepard** | Dressel F. et al., DLR VPH | Adapter connecting the Remote Component Environment (RCE) workflow tool to shepard as a persistence layer | Dressel et al. DGLR 2022 |
| **shepard-timeseries-collector (sTC)** | DLR ZLP | Standalone timeseries ingest tool for shepard; Rust-based | aidocs/40 §3 |
| **shepard-dataship** | DLR | Python/async data transfer and staging client | aidocs/16 dispatcher X1 |

---

## 7. Research domains documented

| Domain | Campaign/project | Key shepard use |
|---|---|---|
| Automated Fibre Placement (AFP) | DLR ZLP, Flextelligent topic | Layup sequences, robot trajectories, quality inspection linked to structural results |
| MFFD upper shell (CFRP welding) | Clean Sky 2 / AIRFRAME ITD | Resistance welding process data + QA + SCADA via ProcessControl |
| Virtual Product House (VPH) | DLR SR, multi-institute | Provenance of CFD/FEM simulation chains; RCE2Shepard adapter |
| ADAPT (hydrogen propulsion) | DLR-BT + 4 other institutes | Thermal characterisation parameter sweeps; HDF5 simulation + thermocouple timeseries |
| MEMAS (composite materials) | DLR ZLP + HMC | Ontology-based material property storage; FAIR data for composites R&D |
| Robotic component testing | Universität Augsburg | Software-defined test bench; robot trajectory + force + sensor data in shepard |

---

## 8. Implications for DFG proposal (aidocs/75)

The citation record is relevant to the **Bedarfsanalyse** and **Umfeldanalyse** sections:

1. **Demonstrated demand beyond the originating group**: Universität Augsburg (Hanke et al.,
   ETFA 2022) adopted shepard independently for robotic testing — this is the strongest
   evidence that the tool has utility beyond its developers. A DFG proposal can cite this
   as evidence of cross-institution demand.

2. **Helmholtz RSD registration**: inclusion in the Helmholtz Research Software Directory
   demonstrates that HMC has recognised the tool as community infrastructure, supporting the
   claim for Helmholtz KG and NFDI alignment (§5.4 of aidocs/75).

3. **MEMAS HMC project**: an already-funded HMC cohort project explicitly using shepard
   strengthens the argument that Helmholtz has an institutional stake in the tool's
   sustainability.

4. **Gap in external adoption**: the absence of confirmed deployments outside DLR / Uni
   Augsburg is the honest gap — the DFG proposal's Phase 2 pilot work (WP1–WP2) is
   precisely the mechanism to create the first external-to-DLR deployment at a university
   partner, converting this gap into a success metric.

5. **ADAPT project** (DLR-BT): confirms that DLR-BT already uses shepard in live research
   (not just planned) — the DLR-BT pilot proposal (aidocs/74) has an existing foothold.

---

## 9. See also

- `aidocs/74-dlr-bt-stakeholder.md` — DLR-BT institute brief; ADAPT project mentioned
- `aidocs/75-dfg-eresearch-funding.md` — DFG funding sketch; Bedarfsnachweis section
- `aidocs/73-dlr-stakeholder.md` — DLR-wide stakeholder brief
- https://helmholtz.software/software/shepard — Helmholtz RSD entry
- https://elib.dlr.de/143136/ — Canonical Zenodo/elib record (Haase et al. 2021)
- https://www.dlr.de/en/bt/research-transfer/projects/ongoing-projects/adapt — ADAPT project
