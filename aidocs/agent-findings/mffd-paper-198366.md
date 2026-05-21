# MFFD Paper 198366 — Comprehensive Extraction

**Source:** https://elib.dlr.de/198366/1/610241.pdf  
**DLR eLib DocumentID:** 610241  
**DOI:** 10.25967/610241

---

## Bibliographic Reference

**Title:** Towards a Closed-Loop Data Collection and Processing Ecosystem

**Authors:** T. Haase, R. Glück, D. Görick, P. Kaufmann, F. Krebs, M. Mayer  
**Affiliation:** German Aerospace Center (DLR), Institute of Structures and Design, Am Technologiezentrum 4, 86159 Augsburg, Germany

**Venue:** Deutscher Luft- und Raumfahrtkongress 2023  
**Year:** 2023  
**Contact:** tobias.haase@dlr.de

---

## Abstract (Verbatim)

> "The German Aerospace Center (DLR) in Augsburg demonstrates the use of the shepard data management system using the example of robot-controlled production of an aircraft upper-shell with thermoplastic tapelaying processes. In the process, measured data from production and quality assurance is automatically gathered, interconnected and stored centrally. The data can then be searched and evaluated in shepard or analyzed in external applications."

**Keywords:** Research Data Management, Data Analysis, Quality Assurance, Automated Fiber Placement

---

## Use Case: MFFD Upper Shell Manufacturing

### Project Context

- **Project:** Multi Functional Fuselage Demonstrator (MFFD)
- **Funding:** EU, embedded in the joint undertaking **Clean Sky 2**
- **Location:** Center for Lightweight Production Technology (ZLP), Augsburg
- **Goal:** Manufacture the upper shell of a single aisle aircraft using **only thermoplastic materials** to facilitate a dustless assembly

### Key Advantage of Thermoplastic Approach

> "The key advantage of using thermoplastic materials in skin manufacturing is that no further process step is needed after the tape has been applied. In the traditional process, however, another process step is required by means of covering the skin with a vacuum bagging and putting the skin into an autoclave. The pressure and additional resin smooths minor defects. Skipping the autoclave saves time and reduces major costs."

Implication: Thermoplastic materials are malleable at high temperatures — two components can be joined by welding. Dustless assembly enables pre-equipped subassemblies with system and cabin elements to be integrated.

---

## Process Chain (4 Main Steps)

The paper describes **four main steps** to build the upper shell:

1. **Skin placement** (T-AFP — Thermoplastic in-situ Automated Fiber Placement) — *this paper's focus*
2. **Stringer integration** — using welding technology
3. **Frame integration** — using welding technology
4. **Cleat integration** — using welding technology (for stiffening the shell)

> "There are four main steps involved to build the shell: 1. skin placement and steps 2.-4. which use different welding technologies to integrate stringers (2.), frames (3.) and cleats (4.) for stiffening the shell."

The paper focuses **exclusively on Step 1 (skin placement)** and its data collection and processing.

---

## Part Specifications

| Parameter | Value |
|-----------|-------|
| Length | 8 m |
| Diameter | 4 m |
| Thickness range | 1.6 mm to 12.5 mm |
| Reinforced areas | Door corners, buttstrap, antenna patch |
| Ply groups | 53 ply groups, each 1–10 plies |
| Tracks per ply | 10 to 244 tracks |
| Track length range | 0.3 m to 11.5 m |
| Tapes per track | 3 (deposited in parallel by MTLH) |

> "The skin was manufactured in full-scale with a length of 8 m and a diameter of 4 m. The build up of the skin comprises areas with different thicknesses to reinforce the door corners, buttstrap and antenna patch. The thickness varies from 1.6 mm to 12.5 mm."

> "The skin build up is divided into 53 ply groups, each consisting of one up to ten plies. The smallest unit is one track consisting of 3 tapes and can vary in length from about 0.3 m to 11.5 m. The tracks are adjacent to each other so that one ply can comprise 10 to 244 tracks."

---

## Robot & Machine Details

### Robot
- **Type:** KUKA KR270 R2700
- **Mounting:** Ceiling mounted on a rail
- **Programming:** KRL (Kuka Robot Language) programs delivered by path planning software
- **Communication protocol:** OPC UA (via KRC — Kuka Robot Control)
- **Internal communication:** ProfiNet

### Multi-Tow Laying Head (MTLH)
- Deposits **3 tapes in parallel**
- Provides OPC UA nodes for: laser power, tape temperature, tape cut, etc.
- Has consolidation rollers (compaction rollers that can be changed)
- Material storage changes tracked as attributes

### Tape Placement Sensor (TPS)
- **Measurement principle:** Laser triangulation
- A line laser is deflected by a mirror onto the surface/track; reflection recorded by camera
- **Spatial resolution:** Height profile triggered **every 2 mm** by the robot
- **Output:** 16-bit TIFF file (height profiles) + CSV file (recording positions with 6 degrees of freedom)
- Connected to DRG via OPC UA; uploads data to file container linked to track DataObject

### Programmable Logic Controller (PLC)
- Provides data via OPC UA
- Part of the robot cell

---

## Data Sources and Types

The paper explicitly categorizes data into **three categories**:

### 1. Timeseries Data
**Sources:** KRC (robot), MTLH, TPS

**KRC channels include:**
- Speed
- Temperatures
- Movement state
- Position (TCP — Tool Center Point, 6 degrees of freedom x, y, z, roll, pitch, yaw)
- Robot axis positions (in degrees)

**MTLH channels include:**
- Laser power
- Tape temperature
- Tape cut status
- Other production parameters

**TPS channels include:**
- Defect size
- Defect location
- Defect width
- Other defect metrics

**Collection method:** OPC Router software, configured to forward values to shepard timeseries container  
**Rate:** Up to **210 variables**, updated at up to **15 Hz** → maximum **3,150 values per second**  
**Batching:** Required to avoid overloading; bulk values sent every few seconds  
**Storage:** InfluxDB (via shepard)  
**Note:** InfluxDB + shepard backend together require **more than 64 GB of memory** at full 3,150 values/second load

### 2. File-Based Data
**Sources:** TPS (primary)

Per track, TPS produces:
- **4 CSV files** (including recording positions with 6 degrees of freedom)
- **1 × 16-bit TIFF file** (height profiles — all profiles for one track)

**Usage:** TIFF files evaluated with computer vision algorithms to detect gaps and overlaps between tracks and tapes. Results plotted as point clouds (Fig 6 in paper: penultimate ply with full 4×8 m coverage).

**CAD tool used for visualization:** Catia V5

**Defect types measured:** Gaps and overlaps between tracks (paper focuses on gaps)

### 3. Key-Value Pairs (Structured Data / Attributes)
**Source:** Machine operator handwritten notes (preprocessed to machine-readable form)

**Content:** Manual rework or maintenance events, including:
- Material storage changes
- Consolidation roller changes

**Process:** Notes transferred to CSV, then parsed by a script to attach attributes to corresponding track DataObjects in shepard

**Use case value:** Enables fast filtering of DataObjects (e.g., "find all tracks with a compaction roller change")

---

## Data Collection Architecture

### Automated Components

**OPC Router:**
- Scans all OPC UA servers available
- Exports discovered nodes to CSV
- Relevant nodes selected by hand, imported into OPC Router configuration
- Configured to send incoming data directly to shepard timeseries container
- 210 variables at up to 15 Hz → 3,150 values/second maximum

**Data Reference Generator (DRG):**
- Python script
- Subscribed to KRC via OPC UA
- Before each track: notified by robot about actual ply and track to be manufactured
- Knows overall structure + current process state
- Creates DataObjects in shepard accordingly (ply groups, plies, tracks)
- Creates timeseries references in shepard (start/end timestamps per track)
- Currently a command line tool running in background

**TPS Data Collection:**
- Started at each track beginning via KUKA EthernetKRL technology package
- TPS software connected to DRG
- Receives relevant DataObject and file container identifier via OPC UA
- Uploads CSV + TIFF measurement data to file container linked to track DataObject at end of each track

**shepard Timeseries Collector (sTC):**
- Developed at ZLP Augsburg
- Edge device application for collecting data from different sources and sending in batches to shepard
- Repository: https://gitlab.com/dlr-shepard/shepard-timeseries-collect
- Planned for testing in larger use cases

### Manual Components

Handwritten operator notes → CSV → parse script → key-value attributes on track DataObjects

---

## Shepard Data Structure Used

The paper describes the **hierarchical DataObject structure** used for MFFD:

```
Collection
  └── Layup  [root DataObject]
        └── Ply Group 1  [child DataObject]
              └── Ply 1  [child DataObject]
                    ├── Track 1  [child DataObject]
                    ├── Track 2  [child DataObject]
                    └── Track n  [child DataObject]
        └── Ply Group 2 ...
        └── ...
```

> "The Layup data object is a parent object to other data objects representing different ply groups. The ply groups themselves are parents of the respective layers, which in turn are parents of the various tracks. Each layer and track is connected to its respective predecessors and successors via the corresponding relationship in shepard."

### Container Setup
- **1 file container** (one per type — single container sufficient for this use case)
- **1 timeseries container**
- **1 structured data container**

Note: Multiple containers per type would be needed for different permission sets; not required here.

### Timeseries Reference Pattern (Key Design Decision)

> "Timeseries, on the other hand, by definition do not have a start or end time, so each timeseries reference has to define these timestamps. For example, all tracks refer to the same tape temperature timeseries but each timeseries reference is assigned a different start and stop time."

This is a critical design insight: **all tracks share one timeseries but are differentiated by start/stop timestamps on the reference**. Avoids data duplication.

### TPS Calibration File Pattern

> "It is possible to link all track data objects to the same TPS calibration file stored in the file container, because the calibration routine was done only once prior to the tape laying start."

One calibration file shared across all track DataObjects via the file reference mechanism.

### Track-Level Attributes

Tracks carry attributes including:
- Whether a material storage change was conducted
- Adjacent direction of predecessor track placement (left or right)

---

## Analysis Tools Described

### 1. Shepard Web Frontend (Built-In)

**Interactive graph view:**
- Built using **vis-network** open source library (https://github.com/visjs/vis-network)
- Rendered client-side in browser at runtime
- Red arrows = parent-child relationships
- Blue arrows = predecessor-successor relationships
- Nodes can be individually expanded/collapsed

**Timeseries plotting modal:**
- Immediate visualization after upload
- Can save plot to local system (for presentations/scientific work)
- Example: tape temperature during production process (Fig 9)

**CSV visualization modal:**
- Parses CSV with user-configurable options: start row, delimiter, decimal format, header presence
- Auto-names columns (Col1, Col2, ...) if no header
- User selects x-axis column + one or more y-axis columns
- Renders interactive plot

**Structured data (JSON) editor modal:**
- Advanced JSON editor (https://github.com/josdejong/jsoneditor)
- Handles large JSON structures

**File reference types:** structured data, file, timeseries, URI, data object, collection references

### 2. External API-Based Analysis

**Authentication:**
- Frontend: OpenID Connect (OAuth 2.0)
- Scripts/machines: **Static API keys** (JWT tokens signed by shepard backend) — persistent from creation to deletion

**Client libraries generated by CI/CD pipeline:**
- Languages: Python, Java, TypeScript, C++
- Generated via OpenAPI Generator from shepard's OpenAPI spec
- Automatically uploaded to GitLab

**Jupyter Notebooks example:**
- Retrieve all tracks for a ply via parent ID query (single query)
- Filter timeseries by selected channels to reduce memory footprint
- Select TCP coordinates + MTLH tape temperature
- Resampling needed (InfluxDB provides this, exposed via shepard API)
- Plot with **Plotly Express** library
- Finding: temperature relatively steady in main area; errors visible (diagonal violet line of dots in Fig 12); start-up and shut-down phases identifiable by lower temperature

**Dash Open Source dashboard example:**
- Creates interactive web pages with embedded input forms
- Example: gap visualization between tracks (Fig 13)
- Hypothesis confirmed: **compaction roller change influenced gap formation between tracks**
- Roller change stored as attribute on track → displayed as highlights in point cloud

**Grafana + Infinity Datasource plugin:**
- Fetches data directly from shepard REST API
- Headers include API key + query parameters for timeseries filtering
- Configured to fetch at specific intervals for live data
- Example: most recent robot movements as axis positions in degrees (Fig 14)

---

## Key Findings and Results

### Process Quality Finding
> "We were able to show the influence of the change of the compaction roller on the formation of gaps and overlaps between the tracks and thus on the quality of the finished part."

This is the paper's headline scientific result: correlating compaction roller change events (stored as attributes) with gap defects (measured by TPS) was only possible because both data types were linked to the same track DataObjects in shepard.

### Temperature Anomaly Pattern
> "In this plot it can be seen that the temperature is relatively steady in the main area of the measurement but also single errors are visible where the process did not work properly (Fig 12, diagonal violet line of dots). In addition, the start-up and shut-down phases of can be easily identified as their temperature is lower than during the main phases."

### Defect Type: Gaps Between Tracks
- Fig 4: gaps between tracks of one ply (full 4×8 m mould coverage)
- Fig 6: gaps between tracks of ply 4 in ply group 4
- Detected by computer vision on 16-bit TIFF files

---

## Performance and Scaling Notes

| Issue | Detail |
|-------|--------|
| OPC Router rate | 210 variables × 15 Hz = 3,150 values/second maximum |
| Memory bottleneck | InfluxDB + shepard backend together need >64 GB RAM at max throughput |
| Solution | Bulk/batch sending — combine most recent values and send every few seconds |
| OPC Router limitation | Event-based; configuring batching is "not impossible but cumbersome" |
| sTC alternative | shepard Timeseries Collector — purpose-built edge device for batch collection |

---

## Identified Improvement Areas (from Conclusion)

1. **Timeseries batching:** OPC Router event-based design makes batching cumbersome; sTC is the solution
2. **DRG improvement:** Currently CLI tool; proposed enhancement: GUI so operator can see current state + create annotations manually + potentially replace handwritten notes entirely

---

## Key Terminology

| Acronym | Full Form |
|---------|-----------|
| MFFD | Multi Functional Fuselage Demonstrator |
| MTLH | Multi-Tow Laying Head |
| T-AFP | Thermoplastic in-situ Automated Fiber Placement |
| TPS | Tape Placement Sensor |
| TCP | Tool Center Point |
| DRG | Data Reference Generator |
| sTC | shepard Timeseries Collector |
| KRC | Kuka Robot Control |
| OPC UA | Open Platform Communications Unified Architecture |
| PLC | Programmable Logic Controller |

---

## References Cited in Paper (Selected Key References)

- **[1]** Frommel, Krebs, Haase et al. "Automated manufacturing of large composites utilizing a process orchestration system." Procedia Manufacturing, 51:470–477, 2020. DOI: 10.1016/j.promfg.2020.10.066
- **[2]** Wilkinson et al. "The FAIR Guiding Principles for scientific data management and stewardship." Scientific Data, 3(1):160018, 2016. DOI: 10.1038/sdata.2016.18
- **[3]** Haase, Glück, Kaufmann, Willmeroth. "shepard — storage for heterogeneous product and research data." July 2021. DOI: 10.5281/ZENODO.5091604
- **[4]** Krebs, Willmeroth, Haase, Kaufmann, Glück, Deden, Brandt, Mayer. "Systematische Erfassung, Verwaltung und Nutzung von Daten aus Experimenten." DLRK 2021. DOI: 10.25967/550315
- **[8]** Deden, Brandt, Hellbach, Fischer. "Upscaling of in-situ Automated Fiber Placement with LM-PAEK — From Panel to Fuselage." ECCM 2022.
- **[9]** Mayer, Schuster, Brandt, Deden, Fischer. "Integral quality assurance method for a CFRP aircraft fuselage skin: Gap and overlap measurement for thermoplastic AFP." FAIM2023.
- **[10]** Fischer, Endraß, Deden, Brandt et al. "How to Produce a Thermoplastic Fuselage." ITHEC 2022.
- **[11]** Mayer, Schuster, Brandt, Deden, Fischer, Schmorell, Vistein. "Quality Assured Aircraft Fuselage Production: Data Evaluation of a Quality Control Sensor for Thermoplastic Automated Fiber Placement." DLRK 2022. DOI: 10.25967/570129

**Note on fiber material:** Reference [8] specifically mentions **LM-PAEK** (Low-Melt PolyArylEtherKetone) as the thermoplastic matrix material used in the AFP process for fuselage-scale manufacturing.

---

## Implications for MFFD Seed Data

This paper directly informs what a faithful MFFD seed dataset should contain:

### DataObject Hierarchy to Model
```
Collection: "MFFD Upper Shell Layup Campaign"
  └── Layup [root]
        ├── Ply Group 1 (of 53)
        │     ├── Ply 1
        │     │     ├── Track 1  {attributes: predecessor_direction: "left"|"right", material_storage_change: bool, roller_change: bool}
        │     │     ├── Track 2
        │     │     └── Track n (10–244 tracks per ply)
        │     └── Ply 2
        └── Ply Group 2 ... (53 ply groups total)
```

### Timeseries Channels to Seed (per track)
- TCP position (x, y, z, roll, pitch, yaw) — from KRC
- Robot speed — from KRC
- Robot movement state — from KRC
- Robot axis positions (in degrees, 6 axes) — from KRC
- Tape temperature — from MTLH (key quality indicator)
- Laser power — from MTLH
- Tape cut status — from MTLH
- Defect size — from TPS
- Defect location — from TPS
- Defect width — from TPS

### Files to Seed (per track)
- 16-bit TIFF (height profile scan, triggered every 2 mm along track)
- 4 × CSV files (recording positions with 6 DOF)

### Shared Files (once per campaign)
- TPS calibration file (linked to all tracks via file reference)

### Key Attributes on Track DataObjects
- `predecessor_direction`: "left" or "right"
- `material_storage_change`: true/false
- `roller_change`: true/false (the key to reproducing the paper's finding)
- `ply_group`: integer
- `ply`: integer
- `track`: integer

### Anomaly / Finding to Recreate
The paper's central finding is the **correlation between compaction roller changes and gap formation**. A seed dataset should include:
- Several tracks before a roller change (low gap values)
- The roller change event (attribute: `roller_change: true`)
- Several tracks after the roller change showing elevated gap measurements
- This recreates exactly the Dash app visualization in Fig 13
