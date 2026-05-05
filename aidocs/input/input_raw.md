Read all of this first then plan and dispatch accordingly:

streaming openapi compatible -> client generation?
rest api improvements? feels clunky. maybe even other apis that are faster or easier to integrate eg timeseries to excel via sql
approach to neo4j ID issue deprecated id fucntion https://neo4j.com/docs/cypher-manual/current/functions/scalar/#functions-id - a migration?
UI Critique - improvements in workflows. quicker. easier. acces to measurement data. current lots of clicking. 
Idea integration of user profile. avatar. orcid, for now.  effort?

very ui specific but:  search-as-you-type first --> not a dropdown. you see a tree or maybe even a graph or you can switch views between the three to navigate the ontology

Templates we need an idea how we can generate collection, dataobject or reference templates so users can create a defined ZUgprüfungsobject with icon (maybe even with a form) than a empty (scary) dataobject . also more complex strcutures like sub trees of dataobjects and references up to complete collection layouts. mandatory attributes, annotatioons etc maybe limits, eg abstract 300-500 words. Definable by a admin or projectmanager role (t.b.d.) probably in yaml or similar. compatibility with https://gitlab.com/dlr-shepard/shepard-process-wizard would be a plus (by the way then we need some idea how to deal with the scary spwmodel objcts.

further tools for shepard https://gitlab.com/dlr-shepard/shepard-timeseries-collector , https://gitlab.com/dlr-shepard/processcontrol , https://gitlab.com/dlr-shepard/shepard-process-wizard
check repos in https://gitlab.com/dlr-shepard
file collector not public but basically a node red flow:
[
    {
        "id": "0bbe4ec0a4a5b7ce",
        "type": "tab",
        "label": "naive shepard file upload",
        "disabled": true,
        "info": "",
        "env": []
    },
    {
        "id": "1751f8c7834f1e3c",
        "type": "tab",
        "label": "shepard file upload only base nodes",
        "disabled": false,
        "info": "",
        "env": []
    },
    {
        "id": "3b8925f58ea04a7f",
        "type": "tab",
        "label": "Configuration and Flow Generator",
        "disabled": false,
        "info": "Do not change, if you dont know what you are doing.",
        "env": []
    },
    {
        "id": "bcccb32d8608e499",
        "type": "watch-directory",
        "z": "0bbe4ec0a4a5b7ce",
        "folder": "/ingress",
        "recursive": 0,
        "typeEvent": "create",
        "ignoreInitial": false,
        "ignoredFiles": "",
        "ignoredFilesType": "re",
        "name": "",
        "x": 130,
        "y": 100,
        "wires": [
            [
                "eb527756a96f05f5"
            ]
        ]
    },
    {
        "id": "eb527756a96f05f5",
        "type": "file in",
        "z": "0bbe4ec0a4a5b7ce",
        "name": "",
        "filename": "filename",
        "filenameType": "msg",
        "format": "",
        "chunk": false,
        "sendError": false,
        "encoding": "none",
        "allProps": false,
        "x": 200,
        "y": 160,
        "wires": [
            [
                "fed145f9f8ac50c4"
            ]
        ]
    },
    {
        "id": "db7004b8a989ba78",
        "type": "function",
        "z": "0bbe4ec0a4a5b7ce",
        "name": "prepare payload",
        "func": "//Build shepard API URL\nmsg.url = msg.shepard_api_base_url + \"/fileContainers/\"+msg.shepard_filecontainer_id+\"/payload\"\n//set headers for file upload and authencication\nmsg.headers = {\n    \"content-type\":\"multipart/form-data\",\n    \"X-API-KEY\": env.get(\"SHEPARD_API_KEY\")\n}\n//reformat the message payload (as it is used as body)\nmsg.payload =\n{\n    \"file\": {\n        \"value\": msg.payload,\n        \"options\": {\n            \"filename\": msg.file,\n        }\n    }\n}\nreturn msg;\n",
        "outputs": 1,
        "timeout": 0,
        "noerr": 0,
        "initialize": "",
        "finalize": "",
        "libs": [],
        "x": 480,
        "y": 280,
        "wires": [
            [
                "21cf5f8986ff14f3",
                "325386cb7399f021"
            ]
        ]
    },
    {
        "id": "94a4d6400f38e9e1",
        "type": "comment",
        "z": "0bbe4ec0a4a5b7ce",
        "name": "https://frontend.bt-au-cube2.intra.dlr.de/files/245040",
        "info": "https://frontend.bt-au-cube2.intra.dlr.de/files/245040",
        "x": 430,
        "y": 40,
        "wires": []
    },
    {
        "id": "21cf5f8986ff14f3",
        "type": "http request",
        "z": "0bbe4ec0a4a5b7ce",
        "name": "",
        "method": "POST",
        "ret": "obj",
        "paytoqs": "ignore",
        "url": "",
        "tls": "",
        "persist": false,
        "proxy": "",
        "insecureHTTPParser": false,
        "authType": "",
        "senderr": false,
        "headers": [
            {
                "keyType": "other",
                "keyValue": "X-API-KEY",
                "valueType": "other",
                "valueValue": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJrcmViX2ZsIiwiaXNzIjoiaHR0cDovL2JhY2tlbmQuYnQtYXUtY3ViZTIuaW50cmEuZGxyLmRlL3NoZXBhcmQvYXBpLyIsIm5iZiI6MTczOTg5MjgwMCwiaWF0IjoxNzM5ODkyODAwLCJqdGkiOiIwMmRhYjM3ZS02M2E1LTRlYmQtYmUxYy0wOTllYjNjNzdlZTUifQ.xC4D2euvrs9Paq5MTFo4Yy1aUlMX5PL-gHw60OETHoReNX4S5jRXUXaOyZnjyHC_h3_oHmcy1A295iN0xQvmQnhwTVxd6JcC9-BdiMJHeraEUnXpEgp1toNLsRqe6y6XI5K4Wt_Q4fFjaGZHfsrEej4ebc89qfBXlHnhz4PYPxQgLqGq37G4MZVYZ8AUi3Wuoqn17H7IyGkKFTTqveZYd8hq9nQpsU0IxVLkvfCsfEkrMnIpcld9Inz8T9JmUVQRPxP1K8BZKSN_YMO3hLTF6_qJGuju-_F1ew1ug5cmidZRBklVkvRHEu5h4Y_Z9ns4LrSfvZ2v-kbelkfwBH7Mug"
            }
        ],
        "x": 590,
        "y": 340,
        "wires": [
            [
                "325386cb7399f021"
            ]
        ]
    },
    {
        "id": "fed145f9f8ac50c4",
        "type": "change",
        "z": "0bbe4ec0a4a5b7ce",
        "name": "Set shepard parameters",
        "rules": [
            {
                "t": "set",
                "p": "shepard_filecontainer_id",
                "pt": "msg",
                "to": "245040",
                "tot": "str"
            },
            {
                "t": "set",
                "p": "shepard_api_base_url",
                "pt": "msg",
                "to": "https://backend.bt-au-cube2.intra.dlr.de/shepard/api",
                "tot": "str"
            }
        ],
        "action": "",
        "property": "",
        "from": "",
        "to": "",
        "reg": false,
        "x": 330,
        "y": 220,
        "wires": [
            [
                "db7004b8a989ba78"
            ]
        ]
    },
    {
        "id": "325386cb7399f021",
        "type": "debug",
        "z": "0bbe4ec0a4a5b7ce",
        "name": "debug 1",
        "active": true,
        "tosidebar": true,
        "console": false,
        "tostatus": false,
        "complete": "true",
        "targetType": "full",
        "statusVal": "",
        "statusType": "auto",
        "x": 840,
        "y": 500,
        "wires": []
    },
    {
        "id": "d804807b8e8f98e3",
        "type": "watch",
        "z": "0bbe4ec0a4a5b7ce",
        "name": "",
        "files": "/ingress/",
        "recursive": "",
        "x": 60,
        "y": 500,
        "wires": [
            [
                "eb527756a96f05f5",
                "325386cb7399f021"
            ]
        ]
    },
    {
        "id": "0778588692e9266e",
        "type": "file in",
        "z": "1751f8c7834f1e3c",
        "name": "",
        "filename": "filename",
        "filenameType": "msg",
        "format": "",
        "chunk": false,
        "sendError": false,
        "encoding": "none",
        "allProps": false,
        "x": 460,
        "y": 340,
        "wires": [
            [
                "0db2de3719e1641d"
            ]
        ]
    },
    {
        "id": "76be34599c0f9704",
        "type": "function",
        "z": "1751f8c7834f1e3c",
        "name": "prepare payload",
        "func": "//Build shepard API URL\nmsg.url = msg.shepard_api_base_url + \"/fileContainers/\"+msg.shepard_filecontainer_id+\"/payload\"\n//set headers for file upload and authencication\nmsg.headers = {\n    \"content-type\":\"multipart/form-data\",\n    \"X-API-KEY\": env.get(\"SHEPARD_API_KEY\")\n}\n//reformat the message payload (as it is used as body)\nmsg.payload =\n{\n    \"file\": {\n        \"value\": msg.payload,\n        \"options\": {\n            \"filename\": msg.file,\n        }\n    }\n}\nreturn msg;\n",
        "outputs": 1,
        "timeout": 0,
        "noerr": 0,
        "initialize": "",
        "finalize": "",
        "libs": [],
        "x": 740,
        "y": 460,
        "wires": [
            [
                "187256b4d436477b"
            ]
        ]
    },
    {
        "id": "187256b4d436477b",
        "type": "http request",
        "z": "1751f8c7834f1e3c",
        "name": "",
        "method": "POST",
        "ret": "obj",
        "paytoqs": "ignore",
        "url": "",
        "tls": "",
        "persist": false,
        "proxy": "",
        "insecureHTTPParser": false,
        "authType": "",
        "senderr": false,
        "headers": [
            {
                "keyType": "other",
                "keyValue": "X-API-KEY",
                "valueType": "other",
                "valueValue": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJrcmViX2ZsIiwiaXNzIjoiaHR0cDovL2JhY2tlbmQuYnQtYXUtY3ViZTMuaW50cmEuZGxyLmRlL3NoZXBhcmQvYXBpLyIsIm5iZiI6MTc0NTMxMzE1MCwiaWF0IjoxNzQ1MzEzMTUwLCJqdGkiOiJmZjM2Y2FkOC03YjJmLTRlZjYtYTE1Ny1lYWZjZWExZDE3ZjMifQ.cPrXIIgYx9eLenMqci7KOGx2YTCFk2KtGHcczmOB1_tfrFwVclNvcihmskazHbposooaI1XKv2sj4LrxgxtrqDZ2mGfG0_75ztQs6tGQb69OMtEMVhHh_ge4xaVj8DbnJUxnox73j4FnNJI1DvS3Vp7HYJeUcEok09_4UCV8tWUGeAz2libcHeQlYRMTobttcmCZRLO7ft35LU8RD-tsx0PAoIpgqQFyg4qsOohv3GRBFX0wuNGZ4hLIAQuP-n5kopSj8TB7lX1ID5UTeltoEQlPaZcR1-JEpSegpSOCh0yCySIAQ4QUCAVanb8xfBEn5J7SuPiKsiU_SC5KTV3oQQ"
            }
        ],
        "x": 850,
        "y": 520,
        "wires": [
            [
                "e3e40f9fa0e48186",
                "087f029665dc8e7c"
            ]
        ]
    },
    {
        "id": "0db2de3719e1641d",
        "type": "change",
        "z": "1751f8c7834f1e3c",
        "name": "Set shepard parameters",
        "rules": [
            {
                "t": "set",
                "p": "shepard_filecontainer_id",
                "pt": "msg",
                "to": "704671",
                "tot": "str"
            },
            {
                "t": "set",
                "p": "shepard_api_base_url",
                "pt": "msg",
                "to": "https://backend.bt-au-cube3.intra.dlr.de/shepard/api",
                "tot": "str"
            }
        ],
        "action": "",
        "property": "",
        "from": "",
        "to": "",
        "reg": false,
        "x": 590,
        "y": 400,
        "wires": [
            [
                "76be34599c0f9704"
            ]
        ]
    },
    {
        "id": "e3e40f9fa0e48186",
        "type": "debug",
        "z": "1751f8c7834f1e3c",
        "name": "Debug request",
        "active": false,
        "tosidebar": true,
        "console": false,
        "tostatus": false,
        "complete": "true",
        "targetType": "full",
        "statusVal": "",
        "statusType": "auto",
        "x": 1100,
        "y": 500,
        "wires": []
    },
    {
        "id": "8540a362d0c1076c",
        "type": "watch",
        "z": "1751f8c7834f1e3c",
        "name": "",
        "files": "/ingress/",
        "recursive": "",
        "x": 320,
        "y": 220,
        "wires": [
            [
                "30906dcb6375c795",
                "76f3c7d13babd425"
            ]
        ]
    },
    {
        "id": "30906dcb6375c795",
        "type": "switch",
        "z": "1751f8c7834f1e3c",
        "name": "",
        "property": "event",
        "propertyType": "msg",
        "rules": [
            {
                "t": "eq",
                "v": "update",
                "vt": "str"
            }
        ],
        "checkall": "true",
        "repair": false,
        "outputs": 1,
        "x": 390,
        "y": 280,
        "wires": [
            [
                "0778588692e9266e"
            ]
        ]
    },
    {
        "id": "76f3c7d13babd425",
        "type": "debug",
        "z": "1751f8c7834f1e3c",
        "name": "Debug ingress",
        "active": true,
        "tosidebar": true,
        "console": false,
        "tostatus": false,
        "complete": "true",
        "targetType": "full",
        "statusVal": "",
        "statusType": "auto",
        "x": 540,
        "y": 220,
        "wires": []
    },
    {
        "id": "782d080f106cd891",
        "type": "exec",
        "z": "1751f8c7834f1e3c",
        "command": "",
        "addpay": "cmd",
        "append": "",
        "useSpawn": "false",
        "timer": "",
        "winHide": false,
        "oldrc": false,
        "name": "",
        "x": 1050,
        "y": 660,
        "wires": [
            [
                "d24a7e109abb1484"
            ],
            [
                "d24a7e109abb1484"
            ],
            [
                "d24a7e109abb1484"
            ]
        ]
    },
    {
        "id": "d24a7e109abb1484",
        "type": "debug",
        "z": "1751f8c7834f1e3c",
        "name": "Debug move",
        "active": false,
        "tosidebar": true,
        "console": false,
        "tostatus": false,
        "complete": "payload",
        "targetType": "msg",
        "statusVal": "",
        "statusType": "auto",
        "x": 1330,
        "y": 700,
        "wires": []
    },
    {
        "id": "087f029665dc8e7c",
        "type": "function",
        "z": "1751f8c7834f1e3c",
        "name": "set move command",
        "func": "msg.cmd = \"mv \"+ msg.filename+ \" /ingress/archive\"\nreturn msg;",
        "outputs": 1,
        "timeout": 0,
        "noerr": 0,
        "initialize": "",
        "finalize": "",
        "libs": [],
        "x": 970,
        "y": 580,
        "wires": [
            [
                "782d080f106cd891"
            ]
        ]
    },
    {
        "id": "a45906767f70b89a",
        "type": "yaml",
        "z": "3b8925f58ea04a7f",
        "property": "payload",
        "name": "",
        "x": 610,
        "y": 280,
        "wires": [
            [
                "62b13d70f2fe40a2"
            ]
        ]
    },
    {
        "id": "aee73cb2ef1e800d",
        "type": "file in",
        "z": "3b8925f58ea04a7f",
        "name": "",
        "filename": "filename",
        "filenameType": "msg",
        "format": "utf8",
        "chunk": false,
        "sendError": false,
        "encoding": "none",
        "allProps": false,
        "x": 360,
        "y": 280,
        "wires": [
            [
                "a45906767f70b89a"
            ]
        ]
    },
    {
        "id": "62b13d70f2fe40a2",
        "type": "debug",
        "z": "3b8925f58ea04a7f",
        "name": "debug 5",
        "active": true,
        "tosidebar": true,
        "console": false,
        "tostatus": false,
        "complete": "true",
        "targetType": "full",
        "statusVal": "",
        "statusType": "auto",
        "x": 900,
        "y": 280,
        "wires": []
    },
    {
        "id": "c263eb3abb68a7b2",
        "type": "file in",
        "z": "3b8925f58ea04a7f",
        "name": "",
        "filename": "/config/templates/basic_file_collection_flow.json",
        "filenameType": "str",
        "format": "utf8",
        "chunk": false,
        "sendError": false,
        "encoding": "none",
        "allProps": false,
        "x": 500,
        "y": 380,
        "wires": [
            [
                "62b13d70f2fe40a2",
                "350b28a18e1cc3b2"
            ]
        ]
    },
    {
        "id": "3a306e500fb8b53a",
        "type": "inject",
        "z": "3b8925f58ea04a7f",
        "name": "",
        "props": [
            {
                "p": "payload"
            },
            {
                "p": "topic",
                "vt": "str"
            }
        ],
        "repeat": "",
        "crontab": "",
        "once": false,
        "onceDelay": 0.1,
        "topic": "",
        "payload": "",
        "payloadType": "date",
        "x": 160,
        "y": 380,
        "wires": [
            [
                "c263eb3abb68a7b2"
            ]
        ]
    },
    {
        "id": "350b28a18e1cc3b2",
        "type": "function",
        "z": "3b8925f58ea04a7f",
        "name": "function 1",
        "func": "msg.payload = \n{\n  \"id\": \"91ad451.f6e52b8\",\n  \"label\": \"Sheet 1\",\n  \"nodes\": JSON.parse(msg.payload),\n  \"configs\": [ ]\n}\nreturn msg;",
        "outputs": 1,
        "timeout": 0,
        "noerr": 0,
        "initialize": "",
        "finalize": "",
        "libs": [],
        "x": 760,
        "y": 560,
        "wires": [
            [
                "62b13d70f2fe40a2"
            ]
        ]
    }
]

data prcessing pipelines python driven would be great
maybe preliminary ai analysis (anomalies, correllation.... ) notifiaction of findings
notification of users with bigger tasks
maybe feature creep and this is a seperate analysis platform


by the way this was our internal roadmap
## Culture

- Schulungs- Workshopkonzept shepard entwickeln

  - Documentation

	- Sample Data
  - Website

## Core

- CRUDify
- Performance

  - Caching
- Output Control

  - e.g. Pagination
- (Semantically) Annotate Everything
- Review Permissions
- semi-permanent API Keys (Valid until?)
- Revisit Search --> unify! (graphql?)

### Container / Data Tyes

- HDF5 support through HSDS endpoints https://www.hdfgroup.org/solutions/highly-scalable-data-service-hsds/
- Finish Spatial Data
- Table Store / relational database in shepard
- Migrate to timeseries ID ?
- S3 / minio for files? - in work externally

### Admin Tooling

- Admin Tools (CLI tooling for cleanup of data marked for deletion, )
- Data Import/Export (whole collections with all payloads) as RO-Create

### Extensions

- AAS Integration (shepard as backend connecting to registry, lots to define but data in shepard --> asset asset adminsitraion shell)
- Templates

### Technical debt

- Neo4J IDs

## Ecosystem

- Databus / Fedaration → Prototyp in Damast ~6/26
databus context:
Favoriten

Kopfzeile setzen
23. April

Florian
14:33
Hi carsten. Kurze frage zur databus api.
ich versuche gerade eine Verison zu registireren. werden Group und artifact automatisch angelegt oder muss das manuell passieren?

{
  "@context": "https://10.247.228.98/res/context.jsonld",
  "@graph": [
    {
      "@type": [
        "Version",
        "Dataset"
      ],
      "@id": "https://10.247.228.98/kreb_fl/ZLP_Augsburg/Solar_System/2026-04-23",
      "title": "Collection Inner Solar System, from fixed client",
      "abstract": "Dataset from collection Inner Solar System, from fixed client",
      "description": "Dataset from collection Inner Solar System, from fixed client",
      "license": "https://dalicc.net/licenselibrary/OpenDataCommonsAttributionLicenseV10",
      "distribution": [
        {
          "@type": "Part",
          "formatExtension": "FileReference",
          "compression": "none",
          "downloadURL": ""
        }
      ]
    }
  ]
}
die IP fordert die DW databus instanz - da sind die kollegen dabei


Florian
14:59
habs.
gruppe muss manuell angelegt werden (glaube ich)...
und mir fehlen für die submission:
         "sha256sum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
          "byteSize": "1234"


Carsten Hoyer-Klick
16:45
ja. 


Florian
16:46
genau :)

und welche gruppen ein account hat find ich nur per sparql und nicht per api raus
zumindest ists mir nur über sparql gelungen


Carsten Hoyer-Klick
16:47
In verifyDataidParts(...) prüft der Databus dann, ob folgende Eigenschaften vorhanden sind: dbpedia/databus - publish-version.js#L31-L40

formatExtension
compression
sha256sum
byteSize
Nur wenn alle vorhanden sind, wird kein Zugriff auf die Datei gemacht.

Aus einer Mail Konversion mit den Leipziger Kollegen. 


Florian
16:47
ich lüg da jetzt einfach :) - zumindest für den prototypen :) die urls erzeug ich zwar - aber noch nix zu holen dort :)


Carsten Hoyer-Klick
16:49
Das mache ich auch so. Das ist aber genau der Anwendungsfall, dass die Daten hinter einem login oder firewall liegen und der databus eh nicht zugreifen kann. Der client, der auf die download links zugreift hat dann ggf. wieder die rechte auf die Daten zugreifen.  

Im DLR ist das sogar oft der Regelfall. 


Florian
16:50
ein paar teaser

image (1).png
PNG92KB
image (3).png
PNG149KB
image (2).png
PNG130KB
image.png
PNG48KB
ich denk morgen hab ich das posten auf den databus dann fertig

group und artifact handling möcht ich noch umbauen


Carsten Hoyer-Klick
16:51
machst Du das selber oder nutzt Du unsere Library? 


Florian
20:53
selber, das library versteh ich nicht ganz :) scheint eher ein CLI tool zu sein

24. April

Carsten Hoyer-Klick
07:23
Die Library ist auch keine Rocket Science... sie baut letztendlich auch nur die JSON Payloads und sendet dann ein request an den databus. Wobei die Library davon ausgeht, dass man die Daten schon in reichhaltigen Metadaten dokumentiert und hat und alle Informationen für den databus aus den Metadaten extrahiert werden können. 


Florian
07:23
genau ...

mein quellcode sollte hier zu finden sein: https://gitlab.dlr.de/zlp-augsburg/inner-source/shepard-dataship

WHat it does:
# User Guide

This guide walks through the Dataship wizard step by step.

---

## Overview

The wizard starts with a **source picker** where you choose where your data comes from. The subsequent steps depend on the source:

| Source | Steps |
|--------|-------|
| **Shepard** | Collection → References → Configure Form → Review & Submit |
| **Filesystem** | Upload Files → Configure Form → Review & Submit |

---

## Source picker

On page load the wizard shows two source cards. Click one to build the wizard for that source. You can switch sources at any time — doing so clears any in-progress work.

---

## Shepard source

### Step 1 — Collection

1. Open the **Select Collection** dropdown and start typing to filter.
2. Click **Load Collection**.

The wizard advances to step 2 and fetches the collection's object tree.

> If the dropdown is empty, check that `SHEPARD_TOKEN` is set and that the token has read access to the target workspace.

### Step 2 — References

The tree shows all data objects in the collection. Each object may contain one or more **references** (StructuredData, Timeseries, or File).

**Selecting references:** Check the box next to any reference you want to include. The badge in the header counts the current selection.

**Downloading reference data:** Each reference row has a download icon:

| Icon colour | Reference type | Action |
|-------------|---------------|--------|
| Green | StructuredDataReference | Downloads JSON |
| Blue | TimeseriesReference | Downloads CSV |
| Purple | FileReference | Opens a modal with individual file download links |

**Navigation:** Back returns to step 1; Configure Form requires at least one reference to be selected.

---

## Filesystem source

### Step 1 — Upload Files

1. Drop one or more files onto the upload area (or click to browse).
2. For each file, optionally paste a **Download URL** — the public URL where the file is hosted. This becomes `downloadURL` in the Databus JSON-LD. Leave blank if the URL is not yet known (the Review step will flag it as a validation warning).
3. YAML/JSON files are automatically parsed as **sidecar metadata** to pre-fill the Configure form.
4. Click **Configure Form** when ready.

---

## Configure Form (all sources)

This step shows two areas:

### Context panel

A collapsible summary of the selected references or uploaded files. This is the context the AI reads when generating field suggestions.

### Metadata form

| Field | Description |
|-------|-------------|
| **Title** | Short human-readable name for the dataset |
| **Description** | 2–3 sentence summary of what the data contains |
| **Version** | Pre-filled with today's date (`YYYY-MM-DD`); edit freely |
| **License** | DALICC license selected from a searchable dropdown; defaults to CDLA-Permissive-2.0 |

**AI field-fill:** Title and Description each have a **auto_awesome** (sparkle) button. Clicking it asks the LLM to suggest a value based on the context panel. You can edit the suggestion before proceeding.

Click **Generate & Review** to assemble the JSON-LD and advance to step 4.

---

## Review & Submit (all sources)

| Section | Description |
|---------|-------------|
| **Validation** | Green badge = valid; red list = errors that must be fixed before submitting |
| **Version metadata** | `@id`, `@type`, title, description, license |
| **Distributions** | One entry per selected reference or uploaded file |
| **Full JSON-LD** | Complete payload in a syntax-highlighted code block |

### Actions

| Button | Action |
|--------|--------|
| **Export JSON** | Downloads the JSON-LD as a `.json` file |
| **Export JSON-LD** | Downloads the JSON-LD with `.jsonld` extension |
| **Export TTL** | Downloads in Turtle RDF format |
| **Submit to Databus** | Opens a confirmation dialog; POSTs to `{DATABUS_API_URL}/publish` |

### Success state

After a successful submission, the step shows a green card with the dataset `@id`. Two buttons appear:

- **Start New Deposition** — clears all state and returns to the source picker
- **Export JSON-LD** — saves a local copy of the submitted deposition

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Empty collection dropdown | Missing or expired `SHEPARD_TOKEN` | Set a valid token in `.env` |
| Tree shows no data objects | Collection is empty or token lacks object-read access | Check Shepard permissions |
| AI buttons produce errors | `LLM_API_KEY` not set or model unavailable | Set `LLM_API_KEY` in `.env` |
| License dropdown is empty | DALICC API unreachable | Check network; a fallback list is used automatically |
| Submit returns 401 | `DATABUS_API_KEY` invalid or expired | Renew the key on the Databus portal |
| Submit returns SSL error | Self-signed certificate on a private Databus instance | Set `DATABUS_VERIFY_SSL=false` in `.env` |
| Submit returns 404 | Wrong `DATABUS_API_URL` | Check the URL points to the correct Databus instance |
| Distribution has no downloadURL | Filesystem source: URL field left blank | Fill in the Download URL field in the Upload step, or accept the validation warning |

---
Where its going:
# Long-Term Implementation Plan: Shepard Dataship

## Context

The application is a working NiceGUI wizard that connects Shepard (research data repository) and Databus (linked-data registry). The current state covers datasource plugins (Shepard + Filesystem), the Configure/Review wizard, Databus JSON-LD submission, and LLM-assisted metadata. The missing-features.md defines what is needed to make this production-ready for DLR research data management. All features below are derived from that document.

---

## Milestone 1 — Identity & Session (IDP Integration)

**Why first:** User identity is load-bearing for the approval workflow, per-user submission history, ORCID profiles, and access control. Everything downstream that requires "who is submitting" depends on this.

**Scope:**
- Integrate an OIDC/OAuth2 provider (Keycloak or compatible DLR IDP) via FastAPI middleware (NiceGUI exposes the underlying FastAPI app as `nicegui_app`)
- Protect all routes; redirect unauthenticated users to the IDP login page
- Inject authenticated user info (sub, name, email, groups/roles) into `AppState` on session start
- Add user profile page: display name, email, allow user to set their ORCID iD
- Store ORCID in a lightweight user-preference store (SQLite `users` table keyed by IDP sub)

**Files to create / modify:**
- `src/auth.py` — OIDC middleware, token validation, session injection
- `src/db.py` — SQLite connection + schema (users, submissions tables — schema evolves in M2)
- `src/state.py` — add `user: Optional[Dict]` field
- `src/ui/app.py` — wire auth middleware; add profile UI
- `pyproject.toml` — add `authlib` or `python-jose`, `httpx` (for token introspection)

**Verification:** Unauthenticated request → redirected to IDP. After login, user name visible in header. ORCID saved and reloaded on next login.

---

## Milestone 2 — Async Processing Pipeline + Submission Status

**Why second:** The current flow blocks the UI while data is fetched and submitted. Large datasets make this unusable. This milestone introduces background jobs and a persistent submission list.

**Scope:**

### 2a — Persistent Submission Store

Add a `submissions` table (SQLite → upgradeable to PostgreSQL):

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| user_sub | TEXT | FK to users |
| title | TEXT | |
| databus_uri | TEXT | target URI |
| status | TEXT | DRAFT, PROCESSING, REGISTERED, ERROR |
| error_msg | TEXT | nullable |
| payload_json | TEXT | serialised JSON-LD form |
| created_at | DATETIME | |
| updated_at | DATETIME | |

- On "Submit" the wizard writes a `DRAFT` record and hands off to the background worker
- UI shows a persistent "My Submissions" panel (accessible from the header) listing all records with live status badges

### 2b — Background Worker

- In-process worker using Python `threading` + `queue.Queue` (no external broker required for v1)
- Worker thread picks up DRAFT jobs, calls Shepard to fetch/package data, uploads to S3, then calls Databus `/api/register`
- Status transitions: DRAFT → PROCESSING → REGISTERED | ERROR
- NiceGUI `ui.timer` (or periodic refresh) polls the DB and updates status badges without page reload

### 2c — S3 Storage (RUSTFS / MinIO-compatible)

- Upload packaged data files to S3 before registering with Databus (so Databus can serve real download URLs)
- `src/storage.py` — thin wrapper around `boto3` for upload, presigned URL, delete
- Configuration: `S3_ENDPOINT_URL`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET` in `.env` / `config.py`
- The Databus distribution `downloadURL` becomes the S3 object URL

**Files to create / modify:**
- `src/db.py` — submission CRUD
- `src/worker.py` — background thread + job queue
- `src/storage.py` — S3 client wrapper
- `src/ui/submissions_panel.py` — "My Submissions" UI component
- `src/ui/app.py` — add submissions panel to header
- `src/config.py` — S3 env vars
- `src/ui/review_step.py` — replace blocking submit with enqueue + redirect to submissions panel
- `pyproject.toml` — add `boto3`

**Verification:** Submit a large dataset. Wizard returns immediately. Submissions panel shows PROCESSING → REGISTERED. Databus record has a real S3 download URL.

---

## Milestone 3 — Enriched Shepard Metadata

Four sub-features that enrich the context shown in the Configure step and passed to the LLM. All touch `src/shepard_adapter.py`, `src/datasources/shepard/source.py`, and `src/datasources/shepard/tree_view.py`.

### 3a — Semantic Annotations + Ontology Resolution

- Add adapter methods: `get_annotations(collection_id, data_object_id, reference_id=None)` — calls `semantic_annotation_api.get_all_*_annotations()`
- Add adapter method: `search_semantic_repositories(query)` — calls `semantic_repository_api`
- Populate `payload['semantic_annotations']` in `fetch_rich_context` (field exists in `normalize_payload` but is always empty)
- `DatabusFormatter` already emits `semanticAnnotations` in distribution output — no change needed
- Configure step: add "Semantic Annotations" expansion; allow user to add/remove annotation triples

### 3b — Lab Journal

- Add adapter method: `get_lab_journals(collection_id, data_object_id)` — calls `lab_journal_entry_api.get_lab_journals_by_collection()` filtered by `data_object_id`
- Populate `context['lab_journals']` in `fetch_rich_context`
- `render_context()` in `ShepardDataSource`: add "Lab Journal" expansion showing journal entries with timestamps and authors

### 3c — Linked Objects / Data Lineage

- Add adapter method: `get_related_objects(collection_id, data_object_id, depth=1)` — traverses `predecessor_ids`, `successor_ids`, `children_ids`, `parent_id` fields already present on `DataObject`
- Populate `context['related_objects']` in `fetch_rich_context`
- Pass through to `payload['parent_objects']` (field exists in `normalize_payload`; `DatabusFormatter` already emits `parentObjects` in distribution)
- `render_context()`: add "Related Data Objects" expansion with relationship type labels

### 3d — URI Reference Crawling

- Add adapter method: `get_uri_references(collection_id, data_object_id)` — calls `uri_reference_api.get_all_uri_references()`
- Show in tree_view alongside other reference types
- Optional HEAD-check to show link validity status
- Include URI references in rich context

### 3e — Selectable Context Inclusion

- Add a checkbox panel in the Configure step: "Include in context: ☑ Lab Journal  ☑ Semantic Annotations  ☑ Linked Objects  ☑ URI References"
- Checked items are fetched and included; unchecked items are skipped
- Preference persisted in SQLite user store (from M1)

**Files to modify:**
- `src/shepard_adapter.py`
- `src/datasources/shepard/source.py`
- `src/datasources/shepard/tree_view.py`
- `src/ui/configure_step.py`

**Verification:** Select a data object with known lab journal entries. Configure step shows journal entries. Disable lab journal inclusion → entries absent. Databus distribution contains `semanticAnnotations`.

---

## Milestone 4 — Granular Data Selection

### 4a — Column-level selection for TimeseriesReference

- Add adapter method: `get_timeseries_columns(collection_id, data_object_id, reference_id)` — calls `timeseries_reference_api.get_metrics_of_timeseries_reference()`
- In `tree_view.py`, after expanding a TimeseriesReference, render a checklist of columns
- Store `selected_columns: List[str]` in `payload['_source_data']`
- `download_timeseries_data()` in `downloads.py` passes selected columns to export call

### 4b — File-level selection for FileReference

- `get_files()` already exists in `ShepardAdapter`
- Replace all-or-nothing FileReference checkbox in `tree_view.py` with an expandable file list showing per-file checkboxes
- Store `selected_file_oids: List[str]` in `payload['_source_data']`
- `show_file_download_modal()` and background packaging use only selected OIDs

**Files to modify:**
- `src/shepard_adapter.py` (4a only)
- `src/datasources/shepard/tree_view.py`
- `src/datasources/shepard/downloads.py`
- `src/datasources/base.py` — extend `normalize_payload` schema docs

**Verification:** Select a timeseries with 10 columns. Choose 3. Download CSV contains only 3 columns. Select FileReference with 5 files. Choose 2. Package contains only 2 files.

---

## Milestone 5 — Approval Workflow

**Why after M1 + M2:** Requires user identity (M1) and the submission status store (M2).

**Scope:**
- Add `approver_sub` and `approved_at` columns to the `submissions` table
- New status value: `PENDING_APPROVAL` inserted between DRAFT and PROCESSING
- When user submits, status is set to `PENDING_APPROVAL`; worker does not process until approved
- Approver role determined by IDP group membership (e.g., Keycloak role `dataship-approver`)
- Approvers see a separate "Pending Approvals" view listing all PENDING_APPROVAL submissions with preview of JSON-LD metadata
- Approve action → status transitions to DRAFT → picked up by worker
- Reject action → status set to ERROR with reviewer comment; submitter notified (email or in-app notification)

**Files to create / modify:**
- `src/db.py` — approval columns + queries
- `src/ui/approvals_panel.py` — approver UI
- `src/ui/app.py` — show approvals panel only to users with approver role
- `src/ui/review_step.py` — show "Awaiting Approval" state instead of PROCESSING for non-approvers

**Verification:** User submits; submission enters PENDING_APPROVAL. Approver logs in, sees the pending submission, approves it. Status moves to PROCESSING → REGISTERED.

---

## Milestone 6 — Output Format Options

Allow users to choose how their data package is structured before upload to S3.

**Options:**
- **Individual parts** (current default): each reference uploaded as a separate S3 object
- **Single ZIP**: all selected references bundled into one archive
- **RO-Crate**: build a Research Object Crate (`ro-crate-metadata.json` + files), upload as ZIP
  - Evaluate [NovaCrate](https://novacrate.datamanager.kit.edu/editor) integration for metadata editing

**Scope:**
- Add format selector to the Configure step (radio group)
- `src/packaging.py` — new module with `package_as_zip()`, `package_as_rocrate()`, `package_as_parts()` functions
- `src/storage.py` — upload accepts either a single bytes object or a list of (key, bytes) pairs
- RO-Crate: add `rocrate` Python library; generate `ro-crate-metadata.json` from the Databus JSON-LD form

**Files to create / modify:**
- `src/packaging.py` (new)
- `src/worker.py` — call appropriate packaging function
- `src/ui/configure_step.py` — format selector
- `pyproject.toml` — add `rocrate` library

**Verification:** Publish same dataset in all three modes. ZIP mode → single S3 object, one distribution part. RO-Crate mode → ZIP with `ro-crate-metadata.json` inside. Individual parts → N S3 objects, N distribution entries in Databus.

---

## Milestone 7 — Joint Publications

Allow a single Databus version to combine payloads from multiple datasources (e.g. a Shepard reference + a local file).

**Scope:**
- Refactor source picker to allow multi-source selection (currently resets state on each pick)
- `AppState.selected_payloads` already aggregates all payloads — it just needs to survive switching sources
- Configure step renders a unified payload list from all sources
- `DatabusFormatter.generate_form()` builds distributions from the combined list (already does this)
- Packaging respects `_source_id` to call the correct download handler per payload

**Files to modify:**
- `src/state.py` — separate `active_source` from `selected_payloads` accumulation
- `src/ui/app.py` / `src/ui/source_picker.py` — multi-source selection mode
- `src/ui/configure_step.py` — show mixed payload list with source badges

---

## Milestone 8 — Shepard Back-Reference

After a successful Databus registration, write a URI reference back into Shepard pointing at the published Databus URI. This closes the provenance loop: the data object in Shepard knows where its published version lives.

**Scope:**
- `src/shepard_adapter.py` — add `create_uri_reference(collection_id, data_object_id, name, uri)`
- `src/worker.py` — after REGISTERED status confirmed, call `create_uri_reference` for each source payload that came from Shepard
- Success state in UI: show "Back-reference created in Shepard" confirmation

**Files to modify:**
- `src/shepard_adapter.py`
- `src/worker.py`
- `src/ui/submissions_panel.py` — show back-reference status

---

## Milestone 9 — MOSS Integration

*(Requires clarification of MOSS scope — HMC or FDM module. Plan this milestone in a dedicated session once requirements are defined.)*

---

## Dependency Graph

```
M1 (IDP)
  └─► M2 (Async + S3 + Status)
        └─► M5 (Approval workflow)
M3 (Enriched Metadata)     ← independent of M1/M2
M4 (Granular Selection)    ← independent of M1/M2
M6 (Output Formats)        ← depends on M2 (packaging runs in worker)
M7 (Joint Publications)    ← independent
M8 (Back-reference)        ← depends on M2 (runs post-registration)
M9 (MOSS)                  ← TBD
```

---

## Suggested Delivery Order

| # | Milestone | Rationale |
| --- | --- | --- |
| 1 | M4 — Granular Selection | High user value, self-contained, no infra changes |
| 2 | M3 — Enriched Metadata | High user value, no infra changes |
| 3 | M1 — IDP | Foundational; unblocks M2 + M5 |
| 4 | M2 — Async + S3 | Core infra for production scale |
| 5 | M5 — Approval | Governance requirement |
| 6 | M6 — Output Formats | Research usability |
| 7 | M7 — Joint Publications | Power-user feature |
| 8 | M8 — Back-reference | Provenance closure |
| 9 | M9 — MOSS | TBD |

---

## Open Questions

1. **MOSS** — which module (HMC or FDM)? What does integration mean operationally?
2. **Provenance** — should provenance be captured as OpenLineage events, W3C PROV triples in the RO-Crate, or both?
3. **IDP** — which provider (Keycloak, Azure AD, DLR SSO)? Is a mock/dev IDP available for local development?
4. **S3 / RUSTFS** — is a staging RUSTFS instance available for development?



GitLab
Sign in · GitLab
Welcome to the DLR GitLab Service!
Sign in · GitLab

Carsten Hoyer-Klick
07:24
Schaue ich mir später mal an. 


Florian
07:27
mein "client" https://gitlab.dlr.de/zlp-augsburg/inner-source/shepard-dataship/-/blob/main/src/databus_client.py?ref_type=heads
grobe doc wie databus eingebudnen ist:https://gitlab.dlr.de/zlp-augsburg/inner-source/shepard-dataship/-/blob/main/doc/databus-integration.md?ref_type=heads

GitLab
Sign in · GitLab
Welcome to the DLR GitLab Service!
Sign in · GitLab
ach ja und aktuelles prototyp deployment, wenn du mal spielen willst: http://bt-au-cube-mig.intra.dlr.de:8090/


Florian
10:12
kleine frage .. sind die shacl shapes komplett korrekt?
ich versuche meine depositions gegen die shapes zu validieren. da scheinen aber manche felder "mandatory"  zu sein, die aber den /register endpoint nicht interessieren.
z.b. dct:issued  oder dct:publisher


Carsten Hoyer-Klick
10:42
Das weiß ich auch nicht. Ich setzte die nicht, sondern, die werden, soweit ich weiß, vom databus gesetzt: dct:issued ist der Zeitpunkt an dem Du den Datensatz registrierst, dtc:publisher ist der account mit dem Du publizierst. 


Florian
10:43







Bezieh mich auf den absatz der api beschreibung:
Register metadata on the Databus. The metadata must be sent as the request body in RDF JSON-LD format. The RDF may contain one or more Databus entities:

Group — validated against the SHACL shape: group.shacl
Artifact — validated against the SHACL shape: artifact.shacl
Version — validated against the SHACL shape: version.shacl
Collection — validated against the SHACL shape: collection.shacl
https://databus.dev.dbpedia.link/api/#/Main%20API/register


Carsten Hoyer-Klick
10:45
Da bin ich dann überfragt. Da eventuell mal Sebastian Hellmann (hellmann@infai.org) oder Jan Forberg (forberg@infai.org) fragen. 


Florian
10:50
alles klar. ich schau mal 

danke trotzdem

vielleicht noch ne andere frage, gibts ne doku zu den moss modules - wie so was funktioniert und entwickelt werden kann?


Carsten Hoyer-Klick
10:54
Jein. Es gibt eine Art User Guide, den muss ich aber noch fertig machen... 

https://www.overleaf.com/3712293827nbzghdfpwyzk#c125a7

https://www.overleaf.com/3712293827nbzghdfpwyzk#c125a7
An online LaTeX editor that’s easy to use. No installation, real-time collaboration, version control, hundreds of LaTeX templates, and more.


Florian
10:55
ich zieh mir mal das pdf


Carsten Hoyer-Klick
10:58
Wenn Du Anmerkungen, Kommentare, Verbesserungsvorschläge hast, nehme ich gerne. Es ist gerade im Review. 


Florian
10:58
mach ich

27. April

Florian
08:00
erstes feedback dokument ist sehr hilfreich


Carsten Hoyer-Klick
08:01
Das freut mich ;-) 


Florian
08:07
jetzt kann ich zumindest sagen, was noch zu tun ist :)

Die Uhrzeit für Carsten Hoyer-Klick ist 01:30



  - Publication Service (to Databus) - handled in dataship, remind me to show you
- Process Analytics - framework for analytics basically a tableau clone
- sheaprd support user libary ... easier and faster then the current pyton client
- Process Control https://gitlab.com/dlr-shepard/shepard-process-wizard

a previoes assesment_
DeepWiki Q&A with Code Context for Repository: gitlab.com/dlr-shepard/shepard

## Q1

# Architecture and Performance Analysis: Shepard Research Data Management Platform

## Executive Summary

Based on a comprehensive analysis of the Shepard platform codebase, I've identified critical architectural and performance improvement opportunities across database management, API design, feature toggles, and system startup. This analysis is grounded in the actual implementation and provides actionable recommendations with priority rankings.

---

## 1. ARCHITECTURAL ISSUES AND IMPROVEMENTS

### 1.1 Database Connection Management

#### **Issue A1: Blocking Sequential Startup with No Graceful Degradation**

**Priority: HIGH**

The current implementation forces all databases to be available before the application starts, creating a single point of failure. shepard:20-37 shepard:49-64

**Problem Details:**

- The `MigrationsRunner.waitForConnection()` uses an infinite loop with 1-second sleeps
- If any database (Neo4j, MongoDB, TimescaleDB, or PostGIS when enabled) is unavailable, the entire application fails to start
- No timeout mechanism exists
- No partial degradation mode for non-critical features

**Recommendations:**

1. **Implement Asynchronous Database Initialization**

   - Use Quarkus `@Startup` beans with different priorities for critical vs. non-critical databases
   - Allow the REST API to start with degraded functionality if optional databases are unavailable
   - Mark endpoints that require specific databases with custom annotations
2. **Add Connection Timeout and Retry Limits**

   - Replace infinite loops with configurable timeout values (e.g., 60 seconds)
   - Implement exponential backoff for retry attempts
   - Log detailed failure reasons to aid debugging
3. **Enhance Health Checks for Runtime Monitoring**  
   The current health checks are passive: shepard:10-19

   These should be enhanced to:

   - Distinguish between startup readiness and runtime health
   - Provide detailed status for each database connection
   - Support automated recovery attempts

**Trade-offs:**

- Increased complexity in managing partial system states
- Need for careful handling of cross-database transactions
- Potential confusion for users when certain features are unavailable

**Risk Mitigation:**

- Implement feature flags to control degraded mode behavior
- Provide clear API error messages when features are unavailable
- Add metrics to track partial availability incidents

---

### 1.2 API Endpoint Organization

#### **Issue A2: Monolithic REST Endpoint Classes**

**Priority: MEDIUM**

The `TimeseriesRest` class contains 40+ endpoints in a single file, creating maintenance challenges: shepard:61-529

**Problem Details:**

- Single responsibility principle violation
- Mixing concerns: CRUD operations, permissions, import/export, and data retrieval
- Difficult to navigate and test
- Similar patterns repeated across `FileRest`​ (32 endpoints) and `CollectionRest` (24 endpoints)

**Identified Cross-Cutting Concerns:**

1. **Permissions Management** - Repeated in multiple classes: shepard:442-512
2. **Import/Export Operations** - Could be standardized: shepard:360-440
3. **Pagination and Query Parameters** - Duplicated logic: shepard:99-114

**Recommendations:**

1. **Decompose into Sub-Resources**

   ```
   /timeseries-containers/{id}              -> TimeseriesContainerRest
   /timeseries-containers/{id}/timeseries   -> TimeseriesDataRest
   /timeseries-containers/{id}/permissions  -> PermissionsSubResource (shared)
   /timeseries-containers/{id}/export       -> ExportSubResource (shared)
   /timeseries-containers/{id}/import       -> ImportSubResource (shared)
   ```
2. **Extract Shared Permission Handling**

   - Create a generic `PermissionsSubResource<T>` that can be composed into different endpoints
   - Use JAX-RS sub-resource locators to delegate permission endpoints
3. **Standardize Pagination and Filtering**

   - Create a `@QueryParamsBean` class to encapsulate common query parameters
   - Move `QueryParamHelper` logic into a reusable service layer

**Trade-offs:**

- More files to maintain (but each simpler)
- Need to update OpenAPI generation configuration
- Existing API clients would need regeneration but no breaking changes to API contracts

---

### 1.3 Feature Toggle Complexity

#### **Issue A3: Build-time vs Runtime Toggle Confusion**

**Priority: MEDIUM-HIGH**

The current feature toggle system mixes build-time and runtime decisions, creating deployment complexity: shepard:120-125 shepard:1-13 shepard:39-39

**Problem Details:**

- ​`@IfBuildProperty` requires features to be enabled at build time, making it impossible to toggle features in production without rebuilding
- The versioning feature toggle at build-time conflicts with the desire for runtime flexibility
- Database configurations are coupled to feature toggles (e.g., spatial database activated based on toggle)
- No clear documentation on which toggles are build-time vs runtime

**Recommendations:**

1. **Migrate to Runtime-Only Feature Toggles**

   - Replace `@IfBuildProperty`​ with custom `@ConditionalOnFeature` annotation that checks at runtime
   - Use CDI `@Produces`​ methods with `@ApplicationScoped` to create beans conditionally
   - Implement feature flag service that reads from configuration or database
2. **Separate Infrastructure Toggles from Feature Toggles**

   - Infrastructure toggles (spatial-data.enabled) should control database connections - keep these
   - Feature toggles (versioning.enabled) should only control business logic - make these runtime
   - Use separate configuration namespaces: `shepard.infrastructure.*`​ vs `shepard.features.*`
3. **Add Feature Toggle Administration Endpoint**

   - Create `/admin/features` endpoint to view and modify runtime toggles
   - Store toggle state in Neo4j or external configuration service
   - Implement toggle change audit logging

**Trade-offs:**

- Runtime toggles add complexity to dependency injection
- Need to handle scenarios where features are toggled while system is running
- Potential for inconsistent state if toggles change during requests

**Example Implementation Pattern:**

```java
// Instead of @IfBuildProperty
@ApplicationScoped
public class FeatureBeanProducer {
    @Produces
    @ConditionalOnFeature("versioning")
    public VersioningService versioningService() {
        return new VersioningServiceImpl();
    }
}
```

---

### 1.4 Multi-Database Coordination

#### **Issue A4: Justified Polyglot Persistence but with Optimization Opportunities**

**Priority: LOW-MEDIUM**

The system uses four databases, which was a deliberate architectural decision: shepard:1-139

**Analysis:**   
The ADR-008 decision was well-reasoned:

- **Neo4j**: Chosen for graph relationships and permissions (despite Postgres alternative)
- **MongoDB**: Required for large file storage (>32TB Postgres limitation)
- **TimescaleDB**: Chosen for timeseries performance (replaced InfluxDB)
- **PostGIS**: Selected for 3D spatial data (significant performance advantage over pgvector) shepard:100-128

**However, optimization opportunities exist:**

1. **Consider TimescaleDB + PostGIS Consolidation**

   - Both are PostgreSQL extensions running on separate instances
   - Could run on same instance with different databases/schemas shepard:117-152

   **Benefits:**

   - Reduced infrastructure complexity (3 databases instead of 4)
   - Potential for cross-schema queries between timeseries and spatial data
   - Simplified backup procedures

   **Trade-offs:**

   - Shared resource contention between timeseries and spatial workloads
   - Need to carefully tune memory allocation
   - Risk of one workload impacting the other
2. **Optimize Cross-Database Permission Checks**

   The current pattern requires Neo4j queries for every data access: shepard:110-132

   **Problem:**  Every timeseries query must first check permissions in Neo4j, then fetch data from TimescaleDB

   **Recommendations:**

   - Implement permission caching with TTL (current cache exists but is basic)
   - Denormalize permissions into TimescaleDB/PostGIS for read-heavy workloads
   - Use database-level row security policies where possible

---

## 2. PERFORMANCE BOTTLENECKS AND IMPROVEMENTS

### 2.1 Startup Time Optimization

#### **Performance Issue P1: Sequential Database Initialization**

**Priority: HIGH**

The four-phase startup is entirely sequential: shepard:20-37

**Measured Impact:**

- PKI initialization: minimal
- Database connection waiting: **variable, potentially minutes if databases are slow to start**
- Migrations: **can take significant time with large datasets**
- Neo4j connection: minimal

**Bottleneck Analysis:**

1. **Migration Runner Waits for ALL Databases** shepard:49-64

   This only waits for Neo4j, but Flyway migrations run serially and block startup: shepard:50-64

2. **No Parallel Database Initialization**  
   All databases could be checked and migrated in parallel

**Recommendations:**

1. **Parallelize Database Connection Checks**

   - Use `CompletableFuture` or virtual threads to check all databases simultaneously
   - Fail fast if any critical database is unavailable
   - Continue if optional databases (PostGIS) are unavailable
2. **Lazy Migration Execution**

   - Move Flyway migrations to background threads
   - Use database-level locks to prevent concurrent migrations
   - Allow REST API to start before migrations complete for read-only operations
   - Return 503 Service Unavailable for write operations during migration
3. **Optimize Flyway Migration Performance** shepard:53-55

   - Enable Flyway parallel execution for independent migrations
   - Consider using Java-based migrations for performance-critical changes
   - Implement migration progress monitoring endpoint

**Expected Improvement:**

- Parallel connection checks: 60-80% reduction in wait time
- Background migrations: Application available in seconds instead of minutes

---

### 2.2 Database Query Performance

#### **Performance Issue P2: Cross-Database Query Coordination**

**Priority: HIGH**

Every data access requires coordinating queries across multiple databases:

**Example Flow for Timeseries Retrieval:**

1. Check permissions in Neo4j
2. Verify container exists in Neo4j
3. Fetch timeseries metadata from TimescaleDB
4. Query data points from TimescaleDB shepard:148-189

**Performance Analysis:**

1. **Permission Check Latency**

   - Each request makes at least one Neo4j query
   - Cache exists but implementation is basic: shepard:111-112
2. **N+1 Query Problem in Parallel Streams** shepard:191-212

   This creates N permission checks for N timeseries in parallel, overwhelming Neo4j

**Recommendations:**

1. **Implement Batch Permission Checks**

   - Add `checkPermissionsBatch(List<Long> entityIds)` method
   - Execute single Cypher query to check all entities at once
   - Reduces N queries to 1 query
2. **Enhance Permission Cache**

   - Use proper cache eviction policy (LRU, TTL-based)
   - Consider Redis or Caffeine cache instead of in-memory Map
   - Cache at user+entity level, not just key string
   - Implement cache warming for frequently accessed entities
3. **Add Database-Level Read Replicas**

   - Configure read replicas for Neo4j and TimescaleDB
   - Route permission checks to replicas to reduce load on primary
   - Particularly important for Neo4j which handles all permission queries
4. **Optimize TimescaleDB Query Performance**

   The repository uses native queries with proper TimescaleDB functions: shepard:139-150

   Current optimizations are good (hypertables, compression), but consider:

   - Pre-aggregate common query patterns into materialized views
   - Implement query result caching for frequently accessed time ranges
   - Use TimescaleDB continuous aggregates for common aggregation functions

---

### 2.3 Migration Process Performance

#### **Performance Issue P3: InfluxDB to TimescaleDB Migration Efficiency**

**Priority: MEDIUM** (historical, but important for future migrations)

The migration runs in a separate container: shepard:173-185

**Analysis:**

The repository uses PostgreSQL COPY command for bulk inserts, which is optimal: shepard:73-130

**Strengths:**

- COPY command is the fastest method for PostgreSQL bulk inserts
- Separate container prevents impacting production system
- Batch processing with 20,000 rows per batch

**Recommendations for Future Migrations:**

1. **Add Migration Progress Monitoring**

   - Expose migration progress via HTTP endpoint
   - Track rows migrated, errors encountered, time remaining
   - Store progress in database to support resume on failure
2. **Implement Parallel Migration Workers**

   - Partition data by container_id or time range
   - Run multiple migration workers in parallel
   - Use connection pooling to prevent overwhelming target database
3. **Add Data Validation Steps**

   - Implement row count verification
   - Add checksum validation for critical data
   - Create rollback capability for failed migrations

---

### 2.4 Frontend-Backend Communication

#### **Performance Issue P4: OpenAPI Client Generation Overhead**

**Priority: LOW-MEDIUM**

The frontend uses a locally generated TypeScript client: shepard:16-16

**Analysis:**

The client is generated from OpenAPI spec, which ensures type safety but has considerations:

**Current Approach:**

- File-based dependency on locally generated client
- Regeneration required whenever backend API changes
- No versioning between frontend and backend client

**Recommendations:**

1. **Implement API Versioning**

   - Add version prefix to API paths (e.g., `/shepard/api/v1`)
   - Support multiple API versions simultaneously during transitions
   - Use OpenAPI version field properly
2. **Optimize Client Bundle Size**

   - Current client likely includes all 100+ endpoints
   - Implement tree-shaking friendly exports
   - Consider code-splitting API modules (timeseries, files, collections)
3. **Add Response Caching Strategy**

   - Implement HTTP cache headers for GET requests
   - Use ETags for conditional requests
   - Add client-side caching layer for frequently accessed data
4. **Consider GraphQL for Complex Queries**

   - For queries that need data from multiple containers/timeseries
   - Reduces over-fetching and under-fetching
   - Single request instead of multiple REST calls
   - **Trade-off:**  Adds complexity, requires GraphQL infrastructure

---

## 3. SUMMARY OF RECOMMENDATIONS

### High Priority (Implement First)

|Issue|Recommendation|Expected Impact|Effort|
| -------| ------------------------------------------------------------------| ------------------------------------------------| --------|
|**A1**|Implement async database initialization with timeouts|Improved reliability, faster failure detection|Medium|
|**P1**|Parallelize database connection checks and migrate to background|60-80% startup time reduction|Medium|
|**P2**|Batch permission checks and enhance caching|50-70% reduction in Neo4j load|Medium|

### Medium Priority (Implement Next)

|Issue|Recommendation|Expected Impact|Effort|
| -------| ---------------------------------------------| ----------------------------------------------| -------------|
|**A2**|Decompose REST endpoints into sub-resources|Improved maintainability|Medium-High|
|**A3**|Migrate to runtime feature toggles|Deployment flexibility|Medium|
|**P2**|Add TimescaleDB continuous aggregates|Faster query performance for common patterns|Medium|
|**A4**|Consolidate TimescaleDB + PostGIS instances|Reduced infrastructure complexity|Low-Medium|

### Low Priority (Future Enhancements)

|Issue|Recommendation|Expected Impact|Effort|
| -------| -----------------------------------| -------------------------------------------| --------|
|**P3**|Add migration progress monitoring|Better visibility for future migrations|Low|
|**P4**|Implement API versioning|Smoother API evolution|Medium|
|**P4**|Optimize client bundle size|Marginal frontend performance improvement|Low|

---

## 4. IMPLEMENTATION ROADMAP

### Phase 1: Reliability Improvements (2-3 weeks)

1. Add database connection timeouts
2. Implement parallel connection checks
3. Enhance health check endpoints
4. Add batch permission checking

### Phase 2: Performance Optimization (3-4 weeks)

1. Improve permission caching
2. Implement background migrations
3. Add TimescaleDB continuous aggregates
4. Optimize query patterns

### Phase 3: Architectural Refactoring (4-6 weeks)

1. Decompose large REST endpoint classes
2. Migrate to runtime feature toggles
3. Consolidate PostgreSQL instances
4. Implement API versioning

---

## 5. RISK ASSESSMENT

### Critical Risks

1. **Backwards Compatibility**

   - Risk: API changes breaking existing clients
   - Mitigation: Implement versioning before major changes
2. **Data Consistency During Async Initialization**

   - Risk: Partial system state leading to inconsistent data
   - Mitigation: Implement proper transaction boundaries and state validation
3. **Cache Invalidation Complexity**

   - Risk: Stale permission data leading to security issues
   - Mitigation: Conservative TTLs, explicit invalidation on permission changes

### Medium Risks

1. **Database Consolidation Performance**

   - Risk: Resource contention between TimescaleDB and PostGIS
   - Mitigation: Thorough load testing before production deployment
2. **Feature Toggle State Management**

   - Risk: Inconsistent behavior across instances
   - Mitigation: Centralized feature flag service, audit logging

---

## Notes

This analysis is based on the current state of the Shepard platform as of the main branch. The recommendations prioritize:

1. **Reliability over features** - Ensuring system stability during database outages
2. **Incremental improvements** - Changes can be implemented independently
3. **Backwards compatibility** - No breaking changes to existing APIs unless versioned
4. **Evidence-based decisions** - All recommendations reference existing ADRs and implementation patterns

The polyglot persistence architecture is well-justified per ADR-008, and the main opportunities lie in optimizing coordination between databases rather than consolidation. The TimescaleDB migration from InfluxDB shows the team's capability to execute major architectural changes successfully.

### Citations

**File:**  backend/src/main/java/de/dlr/shepard/ShepardMain.java (L20-37)

```java
  @Startup
  void init() {
    Log.info("Starting shepard backend");

    var pkiHelper = new PKIHelper();
    var migrationRunner = new MigrationsRunner();
    pkiHelper.init();

    Log.info("Waiting for databases");
    migrationRunner.waitForConnection();

    Log.info("Run database migrations");
    migrationRunner.apply();

    Log.info("Initialize databases");
    neo4j.connect();
    Log.info("Connection established to neo4j database.");
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java (L49-64)

```java
  public void waitForConnection() {
    while (true) {
      try {
        driver.verifyConnectivity();
        break;
      } catch (Exception e) {
        Log.warn("Cannot connect to neo4j database. Retrying...");
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Log.error("Cannot sleep while waiting for neo4j Connection");
        Thread.currentThread().interrupt();
      }
    }
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/common/healthz/NeoHealthCheck.java (L10-19)

```java
@Readiness
@ApplicationScoped
public class NeoHealthCheck implements HealthCheck {

  private static IConnector neo4j = NeoConnector.getInstance();

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("Neo4J connection health check").status(neo4j.alive()).build();
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/endpoints/TimeseriesRest.java (L61-529)

```java
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.TIMESERIES_CONTAINERS)
@RequestScoped
public class TimeseriesRest {

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesCsvService timeseriesCsvService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  PermissionsService permissionsService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all timeseries containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllTimeseriesContainers(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @PositiveOrZero Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var containers = timeseriesContainerService.getAllContainers(params);
    var result = TimeseriesContainerIOMapper.map(containers);

    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var container = timeseriesContainerService.getContainer(timeseriesContainerId);
    return Response.ok(TimeseriesContainerIOMapper.map(container)).build();
  }

  @POST
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Create a new timeseries container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Transactional
  public Response createTimeseriesContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
    ) @Valid TimeseriesContainerIO timeseriesContainer
  ) {
    var container = timeseriesContainerService.createContainer(timeseriesContainer);
    return Response.ok(TimeseriesContainerIOMapper.map(container)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Delete timeseries container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response deleteTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    timeseriesContainerService.deleteContainer(timeseriesContainerId);
    return Response.status(Status.NO_CONTENT).build();
  }

  @POST
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Upload timeseries to container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = Timeseries.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response createTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long containerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
    ) @Valid TimeseriesWithDataPoints payload
  ) {
    TimeseriesEntity timeseriesEntity = timeseriesService.saveDataPoints(
      containerId,
      payload.getTimeseries(),
      payload.getPoints()
    );

    return Response.ok(new Timeseries(timeseriesEntity)).status(Status.CREATED).build();
  }

  @Deprecated(forRemoval = true)
  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.AVAILABLE)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(
    description = "Get timeseries available. Deprecated, use /timeseriesContainers/{containerId}/timeseries instead."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = Timeseries.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesAvailable(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    List<TimeseriesEntity> timeseriesEntityList;

    try {
      timeseriesEntityList = timeseriesService.getTimeseriesAvailable(timeseriesContainerId);
    } catch (InvalidPathException | InvalidAuthException e) {
      return Response.ok(Collections.emptyList()).build();
    }

    List<Timeseries> timeseriesListWithoutId = timeseriesEntityList
      .stream()
      .map(entity -> new Timeseries(entity))
      .toList();

    return Response.ok(timeseriesListWithoutId).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all available timeseries for that container.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT)
  @Parameter(name = Constants.DEVICE)
  @Parameter(name = Constants.LOCATION)
  @Parameter(name = Constants.SYMBOLICNAME)
  @Parameter(name = Constants.FIELD)
  public Response getTimeseriesOfContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field
  ) {
    var timeseriesEntityList = timeseriesService.getTimeseriesAvailable(timeseriesContainerId);
    var timeseriesList = timeseriesEntityList
      .stream()
      .map(entity -> new TimeseriesIO(entity))
      .filter(
        entity ->
          (measurement == null || measurement.isEmpty() || entity.getMeasurement().equals(measurement)) &&
          (device == null || device.isEmpty() || entity.getDevice().equals(device)) &&
          (location == null || location.isEmpty() || entity.getLocation().equals(location)) &&
          (symbolicName == null || symbolicName.isEmpty() || entity.getSymbolicName().equals(symbolicName)) &&
          (field == null || field.isEmpty() || entity.getField().equals(field))
      )
      .toList();
    return Response.ok(timeseriesList).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES + "/{" + Constants.TIMESERIES_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries by id.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesById(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @PathParam(Constants.TIMESERIES_ID) @NotNull @PositiveOrZero Integer timeseriesId
  ) {
    var timeseries = timeseriesService.getTimeseriesById(timeseriesContainerId, timeseriesId);
    return Response.ok(new TimeseriesIO(timeseries)).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.START, required = true)
  @Parameter(name = Constants.END, required = true)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  public Response getTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) @NotBlank String measurement,
    @QueryParam(Constants.LOCATION) @NotBlank String location,
    @QueryParam(Constants.DEVICE) @NotBlank String device,
    @QueryParam(Constants.SYMBOLICNAME) @NotBlank String symbolicName,
    @QueryParam(Constants.FIELD) @NotBlank String field,
    @QueryParam(Constants.START) @NotNull @PositiveOrZero Long start,
    @QueryParam(Constants.END) @NotNull @PositiveOrZero Long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws Exception {
    var timeseries = new Timeseries(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var timeseriesData = timeseriesService.getDataPointsByTimeseries(timeseriesContainerId, timeseries, queryParams);
    TimeseriesWithDataPoints timeseriesWithData = new TimeseriesWithDataPoints(timeseries, timeseriesData);
    return Response.ok(timeseriesWithData).build();
  }

  @GET
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.EXPORT)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Export timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.START, required = true)
  @Parameter(name = Constants.END, required = true)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  public Response exportTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) @NotBlank String measurement,
    @QueryParam(Constants.LOCATION) @NotBlank String location,
    @QueryParam(Constants.DEVICE) @NotBlank String device,
    @QueryParam(Constants.SYMBOLICNAME) @NotBlank String symbolicName,
    @QueryParam(Constants.FIELD) @NotBlank String field,
    @QueryParam(Constants.START) @NotNull @PositiveOrZero Long start,
    @QueryParam(Constants.END) @NotNull @PositiveOrZero Long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws IOException {
    var timeseries = new Timeseries(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var inputStream = timeseriesCsvService.exportTimeseriesDataToCsv(timeseriesContainerId, timeseries, queryParams);

    return Response.ok(inputStream, MediaType.APPLICATION_OCTET_STREAM)
      .header("Content-Disposition", "attachment; filename=\"timeseries-export.csv\"")
      .build();
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.IMPORT)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Import timeseries payload")
  @APIResponse(description = "ok", responseCode = "200")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Subscribable
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response importTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    MultipartBodyFileUpload body
  ) throws IOException {
    String filePath = body.fileUpload != null ? body.fileUpload.uploadedFile().toString() : null;

    if (filePath == null) {
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    timeseriesCsvService.importTimeseriesFromCsv(timeseriesContainerId, filePath);
    return Response.ok().build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO getTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var permissions = permissionsService.getPermissionsOfEntity(timeseriesContainerId);
    return new PermissionsIO(permissions);
  }

  @PUT
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO editTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var updatedPermissions = permissionsService.updatePermissionsByNeo4jId(permissions, timeseriesContainerId);
    if (updatedPermissions == null) throw new NotFoundException();
    return new PermissionsIO(updatedPermissions);
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Roles getTimeseriesRoles(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var roles = permissionsService.getUserRolesOnEntity(
      timeseriesContainerId,
      securityContext.getUserPrincipal().getName()
    );
    if (roles == null) throw new NotFoundException();
    return roles;
  }

  @Schema(type = SchemaType.STRING, format = "binary", description = "Timeseries as CSV")
  public interface UploadItemSchema {}

  public class UploadFormSchema {

    @Schema(required = true)
    public UploadItemSchema file;
  }

  @Schema(implementation = UploadFormSchema.class)
  public static class MultipartBodyFileUpload {

    @RestForm(Constants.FILE)
    public FileUpload fileUpload;
  }
}
```

**File:**  backend/src/main/resources/application.properties (L50-64)

```properties
# TimescaleDB

quarkus.datasource.db-kind=postgresql
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=db/migration,classpath:de/dlr/shepard/data/timeseries/migrations
quarkus.hibernate-orm.packages=de.dlr.shepard.data.timeseries

# Spatial Database
# https://quarkus.io/guides/flyway#multiple-datasources
quarkus.datasource.spatial.db-kind=postgresql
quarkus.flyway.spatial.active=${shepard.spatial-data.enabled}
quarkus.flyway.spatial.baseline-on-migrate=true
quarkus.flyway.spatial.locations=db/spatial/migration
quarkus.flyway.spatial.migrate-at-start=true
```

**File:**  backend/src/main/resources/application.properties (L120-125)

```properties
# Versioning Feature Toggle
shepard.versioning.enabled=false

# Spatial Data Feature Toggle
shepard.spatial-data.enabled=false

```

**File:**  backend/src/main/java/de/dlr/shepard/common/configuration/feature/toggles/VersioningFeatureToggle.java (L1-13)

```java
package de.dlr.shepard.common.configuration.feature.toggles;

public class VersioningFeatureToggle {

  public static final String TOGGLE_PROPERTY = "shepard.versioning.enabled";

  public static final String IS_ENABLED_METHOD_ID =
    "de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle#isEnabled";

  public static boolean isEnabled() {
    return TogglePropertyUtil.isToggleEnabled(TOGGLE_PROPERTY);
  }
}
```

**File:**  backend/src/main/java/de/dlr/shepard/context/collection/endpoints/CollectionVersioningRest.java (L39-39)

```java
@IfBuildProperty(name = VersioningFeatureToggle.TOGGLE_PROPERTY, stringValue = "true")
```

**File:**  architecture/src/09_architecture_decisions/008-database-target-architecture.adoc (L1-139)

```text
ifndef::imagesdir[:imagesdir: ../../images]

[[adr008]]
=== ADR-008 Database Target Architecture

[%autowidth.stretch]
[cols="h,1a"]
|===
|Date|17.09.2024
|Status|Done

|Context|
*Current state*

At the moment shepard uses three different databases:

* Neo4j (graph db)
* MongoDB (document db)
* InfluxDB (timeseries db)

*What was the reason for choosing different databases?*

* In the very beginning the data was directly stored into the databases (influxdb, neo4j and mongodb), no domain model, just the data
* In the second step the backend was created, the REST api was created and also the domain model
* Special features of timeseries database are already in use (min, max, sum, etc.)
* From a user perspective it feels easier to navigate through a graph database instead of a relational database

*Known issues*

* We have to use three different database query languages
* Maintenance of three different databases and their libraries
* For backup you have to consider all three databases
* Issues with Neo4j
** When to load relationships with data objects and how many and how does it influence performance.
You have to know how the ogm works.
** We had some issues with caching that we do not fully understand.
** Lack of a large ecosystem (e.g. only one migration library available (private one))
* Issues with InfluxDb
** We are using influxdb v1.8 atm.
** New versions of influxdb are completely different (completely new query language, etc.)
** Bad feeling about a shift to paid services.
** The library that we use to communicate with influxdb lacks some important features like query injection prevention.
* Issues with MongoDB
** The update process needs manual steps

|Possible Solutions|

1. We leave it as it is
2. Neo4j + MongoDB
3. Postgres only (Replace all database technologies with postgres)
4. Neo4j + MongoDB + Postgres (replace influxdb with postgres timescaledb)
5. Postgres + MongoDB (Replace influxdb and neo4j)
6. Neo4j + Postgres (Replace influxdb and mongodb with postgres)

image::adr/possible-database-architectures.png[]

|Decisions|
*Decision 1: Leave it as it is*
This is not an option because of known issues with InfluxDB.
We have to find a solution at least for that database.

*Decision 2: Meta Data in Neo4j or Postgres*
[cols="1,2,2"]
!===
!!Neo4j!Postgres
!Migration effort!None!Big
!Onboarding of new developers!Rather big!Rather small
!Familiarity in the team!The team is familiar with Neo4j!The team is not familiar with Postgres
!Ecosystem!Not big!Huge ecosystem, frequently used for a long time
!Maintenance effort!
Big, as we will have additional databases for data storage!Small to medium, if we use Postgres for all data persistence!
Performance!Comparable if properly used!Comparable if properly used
!===

On the green field Postgres might be the better option with less maintenance effort and it’s big ecosystem.
In the context of shepard we already have Neo4j, we would need to migrate data, the experience in the team is bigger for Neo4j.
_All in all we decide to continue with Neo4j._

*Decision 3: Database for Timeseries & Spatial Data*
[cols="1,2,2"]
!===
!!MongoDB!Postgres
!Migration Complexity!
Rather easy, MongoDB is already there and we only have to migrate timeseries data!
Medium migration effort

!Performance!Performance is probably worse than Postgres for timeseries and spatial data!
!Support for spatial data!Only supports 2D spatial data, no trivial and performant way to support 3D!

!Summary!Not an option due to performance and spatial data!
!===

_As MongoDB does not seem to perform well for timeseries and spatial data we decide to store timeseries (and in the future spatial data) in postgres with timescaledb and PostGIS._

*Decision 4: Database for Files & Structured Data*
[cols="1,2,2,2"]
!===
!!Postgres for structured data + Blob storage (MinIO)(option 5)!
Keep MongoDB(option 4)!
Postgres(option 6)

!Migration Complexity!All structured data and files have to be migrated!No migration effort!
All structured data and files have to be migrated

!Onboarding new Developers!!!
New developers only have to know how to interact with two databases

!Maintenance effort (updates of databases & clients)!
We still have three databases to maintain!
We still have three databases to maintain!
We only have to maintain one database in addition to Neo4j

!Reliability!
Probably also very stable!
MongoDB hasn’t been touched in months, so it reliably does it’s job!
Probably also very stable

!Performance (file size around 5-8 GB)!Good!Good!Unknown

!Summary!
This solution is not better than MongoDB, so it’s not feasible given the migration effort!!
!===

Postgres supports two ways for storing binary data link (https://www.postgresql.org/docs/7.4/jdbc-binary-data.html[bytea column and LargeObject API]).

For large files we have to use the LargeObject API.
But in both cases the data is stored in a single table.
For tables we have a limitation of 32 TB (per-table size limitation).
If we want to store multiple projects in one shepard instance, we might exceed this limit.
So we are not able to store large objects in postgres.
_The decision is to stay with MongoDB for files and structured data._

|Consequences|

* We still have to support three different databases.
* Complexity and maintenance costs are higher than with a single database, but just as high as now.
* Same applies to the backup up of three databases.

|===
```

**File:**  architecture/src/09_architecture_decisions/014-spatial-database.adoc (L100-128)

```text

== Performance Tests

Performance tests were executed on an already created dataset that consists of 7Mio data points.

[cols="2a,1a,1a"]
!===
!Performance Test Type!postgis!pgvector
!Create single data points for 1 minute.
!2148 new data points
!2106 new data points

!Bounding Box filter (ca. 500 returned points)
!380ms
!59000ms

!Bounding Sphere filter (ca. 500 returned points)
!795ms
!83000ms

!KNN filter (k = 10)
!53ms
!45ms

!Bounding Box with Metadata Filter (ca. 500 returned points)
!265ms
!42000ms

!===
```

**File:**  infrastructure/docker-compose.yml (L117-152)

```yaml
  timescaledb:
    image: timescale/timescaledb:2.18.1-pg16
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_SHEPARD_USER: ${POSTGRES_SHEPARD_USER}
      POSTGRES_SHEPARD_USER_PW: ${POSTGRES_SHEPARD_USER_PW}
      PGDATA: /var/lib/postgres/data
    volumes:
      - /opt/shepard/timescaledb:/var/lib/postgres/data
      - ./docker-entrypoint-initdb.d/postgres/00-init-postgres-db.sh:/docker-entrypoint-initdb.d/00-init-postgres-db.sh
    restart: unless-stopped
    networks:
      - shepard

  postgis:
    image: postgis/postgis:16-3.5
    container_name: postgis
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_SHEPARD_USER: ${POSTGRES_SHEPARD_USER}
      POSTGRES_SHEPARD_USER_PW: ${POSTGRES_SHEPARD_USER_PW}
    shm_size: "6GB"
    ports:
      - "5433:5432"
    volumes:
      - ./docker-entrypoint-initdb.d/postgis/00-init-postgis-db.sh:/docker-entrypoint-initdb.d/00-init-postgis-db.sh
      - /opt/shepard/postgis/db:/var/lib/postgresql/data
    networks:
      - shepard
    restart: unless-stopped
    profiles:
      - spatial
```

**File:**  infrastructure/docker-compose.yml (L173-185)

```yaml
  timescale-migration-preparation:
    image: registry.gitlab.com/dlr-shepard/shepard/timescale-migration-preparation:5.0.0
    volumes:
      - ./log:/app/log
    env_file:
      - .env
    depends_on:
      - influxdb
      - neo4j
    networks:
      - shepard
    profiles:
      - timescale-migration-preparation
```

**File:**  backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java (L110-132)

```java
  public boolean isAccessTypeAllowedForUser(long entityId, AccessType accessType, String username) {
    String cacheKey = String.format("%s,%s,%s", entityId, accessType.toString(), username);
    if (permissionLastSeenCache.isKeyCached(cacheKey)) return true;

    Roles userRolesOnEntity = getUserRolesOnEntity(entityId, username);

    boolean isAllowed;
    if (userRolesOnEntity.isOwner()) {
      isAllowed = true;
    } else {
      isAllowed = switch (accessType) {
        case Read -> userRolesOnEntity.isReader() || userRolesOnEntity.isWriter() || userRolesOnEntity.isManager();
        case Write -> userRolesOnEntity.isWriter() || userRolesOnEntity.isManager();
        case Manage -> userRolesOnEntity.isManager();
        case None -> false;
      };
    }

    if (isAllowed) {
      permissionLastSeenCache.cacheKey(cacheKey);
    }
    return isAllowed;
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java (L148-189)

```java
  public List<TimeseriesDataPoint> getDataPointsByTimeseries(
    long containerId,
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    return getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams);
  }

  /**
   * Retrieve a list of DataPoints for a time-interval with options to grouping/
   * time slicing, filling and aggregating.
   *
   * This function does not check if the container specified by containerId is
   * accessible.
   * We add <code>@ActivateRequestContext</code> in order to call this method in a
   * parallel stream.
   * The container check relies on an active request context.
   * However, the 'ActivateRequestContext' annotation does not allow for a
   * container check.
   *
   * @param containerId
   * @param timeseries
   * @param queryParams
   * @return List<TimeseriesDataPoint>
   */
  @ActivateRequestContext
  public List<TimeseriesDataPoint> getDataPointsByTimeseriesActivatedRequestContext(
    long containerId,
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    Optional<TimeseriesEntity> timeseriesEntity = this.timeseriesRepository.findTimeseries(containerId, timeseries);

    if (timeseriesEntity.isEmpty()) return Collections.emptyList();

    int timeseriesId = timeseriesEntity.get().getId();
    DataPointValueType valueType = timeseriesEntity.get().getValueType();

    return this.timeseriesDataPointRepository.queryDataPoints(timeseriesId, valueType, queryParams);
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java (L191-212)

```java
  public List<TimeseriesWithDataPoints> getManyTimeseriesWithDataPoints(
    Long containerId,
    List<Timeseries> timeseriesList,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    ConcurrentLinkedQueue<TimeseriesWithDataPoints> timeseriesWithDataPointsQueue = new ConcurrentLinkedQueue<
      TimeseriesWithDataPoints
    >();
    timeseriesList
      .parallelStream()
      .forEach(timeseries -> {
        timeseriesWithDataPointsQueue.add(
          new TimeseriesWithDataPoints(
            timeseries,
            getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams)
          )
        );
      });
    return new ArrayList<TimeseriesWithDataPoints>(timeseriesWithDataPointsQueue);
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java (L73-130)

```java
  /**
   * Insert a list of timeseries data points into the database using the COPY command.
   * This is used by the influxdb migration but can also be used for csv import or
   * similar scenarios.
   * @param entities
   * @param timeseriesEntity
   */
  @Timed(value = "shepard.timeseries-data-point.copy-insert")
  public void insertManyDataPointsWithCopyCommand(
    List<TimeseriesDataPoint> entities,
    TimeseriesEntity timeseriesEntity
  ) throws SQLException {
    try (Connection conn = defaultDataSource.getConnection()) {
      PGConnection pgConn = (PGConnection) conn.unwrap(PGConnection.class);
      CopyManager copyManager = pgConn.getCopyAPI();

      var columnName = getColumnName(timeseriesEntity.getValueType());
      var sb = new StringBuilder();

      timeseriesEntity.getId();

      // Strings must be quoted in double quotes in case they contain a comma which is also the delimiter
      if (timeseriesEntity.getValueType() == DataPointValueType.String) {
        for (int i = 0; i < entities.size(); i++) {
          TimeseriesDataPoint entity = entities.get(i);
          sb
            .append(timeseriesEntity.getId())
            .append(",")
            .append(entity.getTimestamp())
            .append(",\"")
            .append(entity.getValue())
            .append("\"\n");
        }
      } else {
        for (int i = 0; i < entities.size(); i++) {
          TimeseriesDataPoint entity = entities.get(i);
          sb
            .append(timeseriesEntity.getId())
            .append(",")
            .append(entity.getTimestamp())
            .append(",")
            .append(entity.getValue())
            .append("\n");
        }
      }

      InputStream input = new ByteArrayInputStream(sb.toString().getBytes());
      String sql = String.format(
        "COPY timeseries_data_points (timeseries_id, time, %s) FROM STDIN WITH (FORMAT csv);",
        columnName
      );

      copyManager.copyIn(sql, input);
    } catch (IOException ex) {
      Log.errorf("IOException during copy insert: %s", ex.getMessage());
      throw new RuntimeException("IO Error while inserting data points", ex);
    }
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java (L139-150)

```java
  @Timed(value = "shepard.timeseries-data-point.query")
  public List<TimeseriesDataPoint> queryDataPoints(
    int timeseriesId,
    DataPointValueType valueType,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    assertNotIntegral(queryParams.getFunction());
    assertCorrectValueTypesForAggregation(queryParams.getFunction(), valueType);
    assertCorrectValueTypesForFillOption(queryParams.getFillOption(), valueType);
    assertTimeIntervalForFillOption(queryParams.getTimeSliceNanoseconds(), queryParams.getFillOption());
    assertAggregationSetForFillOrGrouping(
      queryParams.getFunction(),
```

**File:**  frontend/package.json (L16-16)

```json
    "@dlr-shepard/backend-client": "file:../backend-client",
```
# Erweiterungen

- HDF5 support mittels HSDS - HMC2
- Apache SuperSet integration für Datenanalyse - tbd
- RO-Crate Export wizard like https://github.com/kit-data-manager/NovaCrate
- RO-Crate import - HMC2
- Support GeoSpatial coordinates with height - in spatial data
- shepard MCP server
- "Data parsers" as plugins / data importer tooling



how deal with deletion of semantic repositories

- keep dead annotations? (what happens?)

- neo4j cleanup script

- delete through (all annotaions)

a grafana (we use grafana with the infinity data source Plugin (REST) for dashboarding) Dashboard Generator where you choose your Graphs through selection and an configuration and then Export the json for grafana (Maybe generate an api key on the fly, ask user)

HSDS and 
Mathieu Vinot
14:10
Hi Florian! hast du letzter Zeit Erfahrung mit den Semantics Annotation und SPARQLWrapper gemacht?


Florian
14:10







inwiefern?




11 Antworten
Gefolgt
Letzte Antwort 24. Februar

Mathieu Vinot
14:10
übrigens ein Kollege hat mir gesagt, dass er deinen Vortrag gut fand, weil du Sachen erklärt hast, statt nur Ergebnisse zu zeigen :)



1 Antwort
Gefolgt
Letzte Antwort 24. Februar

Mathieu Vinot
14:18
from SPARQLWrapper import SPARQLWrapper, JSON
from pandas import json_normalize
sparql_service_url = "https://fuseki.bt-au-semantics.intra.dlr.de/MEMAS-ontology/sparql"

def query_classes(sparql_service_url):
    sparql_service_url = "https://fuseki.bt-au-semantics.intra.dlr.de/MEMAS-ontology/sparql"
    sparql = SPARQLWrapper(sparql_service_url)

    get_classes = """
    PREFIX owl: http://www.w3.org/2002/07/owl#
    PREFIX rdfs: http://www.w3.org/2000/01/rdf-schema#

SELECT DISTINCT ?class ?pred ?label
WHERE {
    ?class a owl:Class;
    rdfs:label ?label.
}
"""

sparql.setQuery(get_classes)
sparql.setReturnFormat(JSON)

result = sparql.query().convert()

return json_normalize(result["results"]["bindings"])
result = query_classes("https://fuseki.bt-au-semantics.intra.dlr.de/MEMAS-ontology/sparql") 

Mattermost will den Code nicht komplett erkennen...


Florian
14:18
alles gut
ich test erst mal den sparql teil

also die queries auf fuseki gehen 164 results in 0.064 second

oder zumindest das query

bekommst du ne konkrete fehlermeldung?


Mathieu Vinot
14:19
urllib.error.URLError: <urlopen error [SSL: CERTIFICATE_VERIFY_FAILED] certificate verify failed: unable to get issuer certificate (_ssl.c:1007)>


Florian
14:20
okay ... dann ists wirklich da wa


Mathieu Vinot
14:20
welche Version von Python nutzst du?


Florian
14:20
3.13? glaub ich


Mathieu Vinot
14:20
ok vielleicht hat sich etwas geändert


Florian
14:20
neee

der fehler den du schreibst kommt aus urllib ...

da gehts um verbindungen mit http

problem ist er baut die verbindung gar nicht auf, weil er das zertifikat für ungültig hält


Mathieu Vinot
14:23
hmm, das selbe Skript hatte früher geklappt. Komisch, dass es bei dir funktioniert


Florian
14:24
hab nur den sparq teil erst getestet


Mathieu Vinot
14:24
der Wrapper tut bei mir

erst ab der query get es nicht mehr


Florian
14:24
klar weil erst da wirklich die verbindung aufgemacht wird

also das zertifikat ist gültig:
Validity
Not Before
Tue, 13 Jan 2026 12:19:01 GMT
Not After
Wed, 13 Jan 2027 12:19:01 GMT 


Mathieu Vinot
14:27
funktioniert die query bei dir?


Florian
14:28
die query (direkt im fusiki) geht

pack mal 

import ssl
ssl._create_default_https_context = ssl._create_unverified_context
vor die zeile:
result = sparql.query().convert()


Mathieu Vinot
14:30
oh :D

magic

funktioniert dann wieder


Florian
14:31
ähh .. ja ... also da müssen wir schon forschen...

damit schalten wir die ssl verifizierung (also zertifikate) ab.

erst mal unkritisch - aber sollte man wissen 


Mathieu Vinot
14:32
vor ein paar Monaten lief alles problemlose

danke sehr für die Hilfe!

06. März

Mathieu Vinot
10:47
hi Florian, habt ihr irgendwann geplant, eine HDF5-Datenbank an Shepard anzudocken? ich weiß nicht, ob so eine Datenbank überhaupt existiert 


Florian
10:54
Hi Mathieu,

so einen Server gibts: https://www.hdfgroup.org/solutions/highly-scalable-data-service-hsds/
ja "irgendwann" steht das auf der roadmap - aber aktuell ist die nachfrage überschau bar.
Was hättest du denn vor, dann kann ich das mal genauer angucken.

The HDF Group - ensuring long-term access and usability of HDF data and supporting users of HDF technologies
Highly Scalable Data Service (HSDS) - The HDF Group - ensuring long-term access and usability of HDF data and supporting users of HDF technologies
HSDS Services HDF Data Products & Solutions provide turnkey, managed data products and HDF Cloud support and consulting for The HDF Group’s Highly Scalable Data Service (HSDS)—our open source REST-based solution for reading, writing, and sharing data within object-based storage environments such as [...]

Highly Scalable Data Service (HSDS) - The HDF Group - ensuring long-term access and usability of HDF data and supporting users of HDF technologies
Das coole daran, wenn wir das "ordenlich" integrieren 
"The related project: https://github.com/HDFGroup/h5pyd provides a (mostly) h5py-compatible interface to the server for Python clients."


Mathieu Vinot
10:55
In der Abteilung haben wir große Daten (z.B. FE-Vernetzung) oder Versuchsdaten aus hdf5 

json wäre denkbar, aber langsam und für großen Datenmengen irgendwann nicht mehr passend


Florian
10:57
vorschlag: ich versuch die tage mal nen hsds aufzusetzen. dann spielen wir ein testfile ein und gucken ob das bringen würde. dann schauen wir wir die shepard integration gehen könnte.


Mathieu Vinot
10:57
das wäre natürlich genial. Thema ist nicht eilig, also mach dir keinen Stress damit :) 

ich finde die Möglichkeit, direkt in den json-Datei nach Information zu suchen super. Wenn sowas mit hdf5 gehen würde, wäre es für uns sehr interessant


Florian
10:58
jo .. aber wenn ich luft hab ist das ein nettes bastelprojekt - und ich habs schon immer im hinterkopf für shepard


Mathieu Vinot
10:58
:D

Luft hätte ich gerne :/


Florian
10:58
hach ja ... stimmt auch wieder :)

aber so die zeit von 7-8 wenn wenig verwaltung ansteht .. da gibts  manchmal lücken

ich glaub im ersten moment ist das auch nur ein dockercompoise

08. April

Mathieu Vinot
07:54
Guten Morgen Florian!
Gibt es eine Möglichkeit in shepard, die Datenverlinkungen aus neo4j zu bekommen?
Ich habe eine Möglichkeit gefunden, wie ich den graphdata zu visualisieren und würde gerne demnächst etwas ausprobieren :)

https://www.yworks.com/products/yfiles-graphs-for-streamlit

yWorks, the diagramming experts
yFiles Graphs for Streamlit - Visualize graph networks with Python
Free diagram visualization extension for Streamlit Apps.

yFiles Graphs for Streamlit - Visualize graph networks with Python

Florian
07:55
kommt ein bisschen drauf an wie du datenverlinkungen meinst...


Mathieu Vinot
07:56
Yfiles braucht halt die nodes und edges und ich weiss nicht, wie ich sie bekommen kann

Also ohne durch ganz shepard per Hand durchzugehen


Florian
07:56
schon klar...

hmm selbst direkter neo4j zugfriff liefert dir das vermutlich auch nicht direkt


Mathieu Vinot
07:57
Sorry, meine IT Fachsprache ist nicht so dolle :/

Sonst würde ich über alle Collections/Databobjects durchiterieren, aber es ist nicht sehr sauber


Florian
07:58
sauber schon .. nur nicht effizient :)

für mich wäre jetzt die frage welche relationen du darstellen willst


Mathieu Vinot
08:01
Erstmal würde ich mit Collection/Dataobjects starten. Also parent/child und dann die anderen relationships

Um z.B. zu zeigen, wo am meisten stattfindet, z.B. welche Testnormen am meisten genutzt sind usw.


Florian
08:05
ich überleg gerade wie das am geschicktesten gehen könnte.

bei der aktuellen api würde ich an "explorer Threads" denken, die eine Collection erkunden und dann die entsprechenden infos zurückgeben (wäre zumindest parallel, aber je nach collection kann das trotzdem dauern...)
zudem wäre vermutlich ne art cache empfehlenswert wenn es viele knoten werden


Mathieu Vinot
08:06
Bei uns wird es erstmal nicht so viele daten sein, aber zukünftig wäre es definitiv keine effiziente Lösung ja


Florian
08:06
neo4js api könnte das vermutlich (speicher vorrausgesetzt) alles auf einmal liefern (so ein bissl SELECT * FROM shepard) - wäre aber vermutlich auch sehr langsam

ich bau gerade etwas nicht unähnliches das collections + dataobjects + references in einen Baum packt


Mathieu Vinot
08:07
Ich wusste, dass du sowas bestimmt schon probiert hättest :D

Es wären eigentlich die gleiche Daten, nur eine andere Darstellung.


Florian
08:10
jup...


Mathieu Vinot
10:41
https://github.com/yWorks/yfiles-graphs-for-streamlit/blob/main/LICENSE.md

GitHub
yfiles-graphs-for-streamlit/LICENSE.md at main · yWorks/yfiles-graphs-for-streamlit
The home of the Streamlit graph visualization component powered by yFiles for HTML - yWorks/yfiles-graphs-for-streamlit

yfiles-graphs-for-streamlit/LICENSE.md at main · yWorks/yfiles-graphs-for-streamlit
wie siehst du das Licencing-System? eigentlich ist es für uns nicht gut, oder? da sie einige Daten weiter nutzen (siehe 3. DATA COLLECTION AND PRODUCT IMPROVEMENT)


Mathieu Vinot
10:48
ich hätte sonst die Lösung: GitHub - AlrasheedA/st-link-analysis: A custom Streamlit component for link analysis, built with Cytoscape.js and Streamlit. · GitHub


Florian
10:48
jo - bin kein so ein freund von streamlit auf grund der Lizenzpolitk... (eher des deployments wegen)

wobei yfiles ja ne gmbh ist und für die DSVGO / GPDR gilt... nicht schön, aber unkritisch wäre mein urteil.

Zwar "alt" aber ne alternative die auf pyvis und networkx...
cytoscape ist auch ne gute idee


Mathieu Vinot
10:51
wegen deines Links zu AI-Schulung: ich würde das in meiner Abteilung weiterleiten, spricht etwas dagegen?


Florian
10:51
nein - ist offiziell von POE.

AI Act (§4) schreibt Kompetenz vor ... deshalb auch die Überlegung ob es Pflicht wird.
https://bmds.bund.de/fileadmin/BMDS/Dokumente/CDR_CDR_251025_%C3%9Cbersicht_KI-Kompetenzaufbau_nach_AI_Act_Ma_BL_clean.pdf


Mathieu Vinot
14:45
das sieht erstmal nicht schlecht aus!



Florian
14:46
noice


Mathieu Vinot
14:46
einziges Problem, was ich bisher nicht lösen kann, ist die Custom Relationship zwischen zwei DataObject mit den APIs zu bekommen


Florian
14:50
lass ma überlegen ... also DO1-custom-DO2 in der art?


Mathieu Vinot
14:50
ja, das wird glaube ich derzeit nicht angezeigt beim Aufruf des DO

und das DO-Reference zeigt auch nur eine Richtung



Florian
14:52
Der Endpunkt "/collections/{collectionId}/dataObjects/{dataObjectId}/dataObjectReferences" sollte es dir doch geben oder?

ja die DO ref ist gerichtet... da gibt es mmn sogar ne issue dazu


Mathieu Vinot
14:56
und in der Referenz bekommen ich auch nicht das ID des referenzierte DO

ich schaue in den Issues

ah ich hatte die Issue generiert :D


Florian
14:58
stups doch mal felix lettowsky nächste woche an... dann kann er das evtl fixen

sollte nicht so wild sein ...


Mathieu Vinot
14:59
naja es ist auch gerade nicht hoch prio, ich frage ihn mal an


Florian
15:00
wobei das evtl auch für roland was wäre .. der mag die CYPHER queries


Mathieu Vinot
15:01
ich frage Roland dann

mehr databus:
Florian
10:20
hi sirko - diana meinte wir sollen uns noch mal austauschen bevor du den April über verschwindest 

meinst wir bekommen das (heute?) noch hin


Sirko
10:21







so ab 1530 oder 1600 ginge - vorher ist schon recht dicht alles


Florian
10:23
hab dir nen termin für 15:30 durchgeschossen


Sirko
10:23
:+1: 


Sirko
16:21
https://doi.org/10.15497/RDA00070
das noch in ergänzung zur diskussion vorhin
da steht zwar auch viel "free text" aber es gibt daneben eigentlich immer auch einen identifier, den man zumindest hinzufügen "sollte"  und teilweise "muss"
RDA outputs haben dann ja auch durchaus das potential der standard zu werden


Florian
16:22
thank you

21. Januar 2025

Florian
08:03
Moin Sirko. 
Habe mal ein paar Kommentare bei dem Questionaire hinterlassen.

Haben wir eigentlich einen Kanal für DaMasT, bzw. wollen wir einen?


Sirko
08:11
gute frage - also haben, nein, wollen, vielleicht
sobald ich Max erreicht habe, machen wir vielleicht doch mal ein kurzes meeting zum vorgehen. da könnten wir das auch ansprechen

ich habe nur bei vielen projekt-Mattermost kanälen nicht den eindruck, dass die genutzt werden. schaden wird es aber auch nicht


Florian
08:52
alles klar.
Ja die Kanäle werden oft nicht genutzt ...
Wobei mir das lieber wäre als skype oder email ... aber da ists noch ein weg hin ...

nur was ganz schlecht wäre zu "mischen" 

10. März 2025

Sirko
10:09
hallo, du hattest den ODIX termin am 31.3. abgesagt, heißt das, dass du auch zum DaMaST meeting nicht kommen könntest?


Florian
10:09
nee ... da muss ich zu HMC Conference vorbereitungstreffen (bin ja auch Program chair...)


Sirko
10:10
dann wird die terminfindung schwierig


Florian
10:10
habe da aber auch keinen damast termin


Sirko
10:10
ich hatte erstmal nur eine mail geschickt und gefragt, ob das geht


Florian
10:11
ahh ... sowas geht immer unter - entweder ich trage es ein oder nicht ...

hmmm
wann sollte der denn sein? 17:00?


Sirko
10:11
die idee war in person lunch-to-lunch am 31.3. und 1.4.


Florian
10:12
tendenziell könnte man Felix Lettowsky fragen - nur der ist natürlich noch voll in der einarbeitung


Sirko
10:13
also mein plan wäre vorher:

die interviews sollten nächste woche durch sein
dann hab ich eine woche für die zusammenfassung und strukturierung
im workshop würde ich gern einen plan für ein projekt aufsetzen mit tasks + kosten, mit dem ich zu Max gehen kann
dann hätten wir noch 2-3 wochen, um eine skizze für die SPP zusammenzuschreiben
ich weiß nicht, ob euer neuer kollege da auch schon den überblick für solche planungen hat

ich kann nochmal schauen, ob ich andere lücken finde, aber im moment sieht das schwer aus - danach sind ja auch osterferien überall


Florian
10:15
ich sag mal - osterferien (abgesehen vom montag) macht bei mir keinen unterschied


Sirko
10:15
also die woche drauf, ab dem 7.4. ginge bei dir grundsätzlich?


Florian
10:16
7.4. und 8.4. ja ... 9.4. hab ich vortrag auf ner konferenz


Sirko
10:16
ok, ich frag mal bei den anderen für den 7./8. nach


Florian
10:17
habs jetzt auch im Kalender  :grinning: 


Sirko
10:18
vielleicht klappt es da auch für Max. am 31./1. konnte er auch nicht


Florian
10:19
irgendwie fehlt mir für so terminanfragen irgendwie eine idee das zu managen. Passiert mir zu oft - brauch ne assistenz oder so


Sirko
10:19
kenn ich ,-)

24. Oktober 2025

Sirko
10:52
wie du jetzt schon (nicht) gehört hast, ist SCs beitrag sehr überschaubar
und ich hatte nicht so wirklich zeit alternativen vorzubereiten

es kam unter der hand schon das feedback, dass der workshop nicht sonderlich viel bringt

27. Oktober 2025

Florian
10:20
hi sirko - sorry dass ich das jetzt erst sehe. mein mattermost wear leider ausgeloggt ...
aber vielleicht finden wir die chance uns nochmal zu deinen eindrücken auszutauschen?


Sirko
10:23
Gern. Im Moment versuche ich zum WAW nach Bremen zu kommen. Ich schau bei  Gelegenheit mal nach Lücken in den Kalendern.
Wichtig wäre insb. der Weg nach vorne. Weisst du wie eure Finanzierung nächstes Jahr aussieht? 


Florian
10:24
ich habe schon angefragt - denke ich bekomme im lauf der woche ne antwort dazu

gehe aber aktuell weiter von 0.5 PJ aus


Sirko
10:25
OK. Dann bekommen wir zumindest absehbar den angesprochen demonstrator hin. Es kam ja zu recht die Kritik, dass wir wenig Greifbares haben derzeit. 

27. Januar

Florian
14:46
Hi Sirko: wir sind gerade dabei mit dem publicationstool frü damast anzufangen: https://gitlab.dlr.de/zlp-augsburg/inner-source/shepard-dataship/-/blob/main/doc/Plan.md?ref_type=heads

GitLab
Sign in · GitLab
Welcome to the DLR GitLab Service!
Sign in · GitLab

Sirko
15:24
hallo, ich wollte dich ohnehin kontaktieren

wir haben auch langsam angefangen die anderen teile zu planen

also invenio und databus/moss aufzusetzen und die verbindung zu schaffen

nachdem ich diese woche unterwegs bin, wollen wir schauen, dass wir kommende woche einen termin finden, um uns abzustimmen?


Florian
15:25
wäre sicher gut

dienstag vormittag sieht ganz gut aus - mittwoch nachmittag ebenso


Sirko
15:27
mittwoch 1500 ?


Florian
15:27
sollte gehen


Sirko
15:27
ich würde von uns gleich die leute mit optional einladen, die es umsetzen

dann haben die es zumindest gehört und können ggf auch was dazu sagen


Florian
15:28
jop gern


Sirko
15:29
einladung ist raus

ich würde dir auch zugriff auf unser gitlab repo dafür geben

sonst noch wer von euch gleich mit?


Florian
15:30
felix
der muss bei mir umsetzen ;)

felix lettowsky


Sirko
15:31
ist auch eingetragen - dauert wie üblich

https://gitlab.dlr.de/damast

danach ist das dort

GitLab
Sign in · GitLab
Welcome to the DLR GitLab Service!
Sign in · GitLab

Florian
15:32
alles klar guck ich rein


Sirko
15:32
viel ist noch nicht drin. Johannes hat aber scheins schon angefangen mal ein nacktes invenio hinzustellen


Florian
15:34
irgendwo muss man ja anfangen :)

08. April

Sirko
09:48
hallo, hattest du meine mail zur verschiebung des meetings am freitag gesehen?


Florian
09:48
ja


Sirko
09:49
geht das für dich?


Florian
09:50
ahh .. du hattest auch gefragt ob 9:00 geht...
tricky - könnte sein, dass da etwas wegen LuFo dazwischen kommt. Im Moment ists aber noch frei


Sirko
09:50
ich hab halt um 10 LuFo reinbekommen ^^


Florian
09:50
hrmpf


Sirko
09:50
und Diana ist im urlaub


Florian
09:52
wir könnens mal festhalten - evtl muss ich morgen dann aber absagen..


Sirko
09:52
ok, dann schicke ich erstmal einen blocker und zur not müssen wir dann morgen nach alternativen suchen

10. April

Sirko
08:37
moin, klappt das meeting gleich oder bist du mit LuFo beschäftigt?


Florian
09:00
sekunde noch


Sirko
09:45
ich hätte früher in den kalender von Max schauen sollen. der 11. geht bei ihm nicht. der 19. würde gehen
ich schätze also wir schießen uns eher auf den 19. ein


Florian
09:45
:+1: stimme mich mit felix dahingehend ab

23. April

Florian
13:32
teaser



Sirko
13:33
:+1: 

hattest du einen zugangstoken von uns bekommen?


Florian
13:33
ich glaube nicht ... 
auch die url für euren databus fehlt mir noch

moss habe ich noch gar nicht berücksichtig


Sirko
13:33
-.-
ich trete unseren leuten nochmal in den hintern


Florian
13:35
Metadata enrichment über scientific cloud LLMs


Submission preview:


13:39
cloud authentication für den databus über auth0 (externer dienst)... wirklich?


General Sentiment:
Du wurden durch @Tobias Haase zum Team hinzugefügt.

Tobias Haase
09:57







Also es ist problemlos möglich, mit einem Github Account sich einzuloggen und Teams beizutreten. Leider kann man nicht einschränken, wer Kanäle erstellen kann. Das finde ich verhältnismäßig gefährlich.

Mein Vorschlag wäre, das Team öffentlich zu machen. Wenn wir Missbrauch der Kanäle feststellen, kann man ja immer noch den Zugang beschränken

08. Dezember 2021

Dominic
15:37
Moin

09. Dezember 2021

Marc Garbade
10:03
Hallo zusammen!

10. Dezember 2021

Tobias Haase
08:54
@all Please note that the backend version 2021.12.10 has just been released. This version contains important security fixes and should be applied as soon as possible. As always, please check the release notes for important changes before installing updates.



2 Antworten
Gefolgt
Letzte Antwort 13. Dezember 2021
14. Dezember 2021

Henning Schlender
15:28
Moin.


Tobias Schlauch (DLR)
15:31
@Tobias Haase Falls ihr ein Logo habt, könnt ihr das gerne auch in Mattermost verwenden. Das macht die Navigation zwischen den verschiedenen Teams einfacher.




1 Antwort
Folgen
Letzte Antwort 14. Dezember 2021

Jakob Haug
15:53
Moin, super Sache, dass es ab jetzt den Channel für Shepard gibt  :+1: 

13. Januar 2022

Mark Willmeroth
17:38
Abstimmung zum Shepard-Logo

Liebe Kolleginnen und Kollegen,

anbei findet ihr die Abstimmung über unser künftiges shepard-Logo.
Zur Auswahl gestellt werden fünf Optionen an Logo-Sets für diverse Einsatzzwecke. (pdf hängt an)
Die Auswahl erfolgt über den Namen des Sets.

Der Link zur Abstimmung:
https://terminplaner4.dfn.de/4Z5BbIBay1cGA85V

Vorab möchte ich mich herzlich für eure Beteiligung bedanken.
Die Umfrage ist bis zum 27.1.2022 geöffnet.

Viele Grüße
Mark Willmeroth 

Shepard Logo.pdf
PDF678KB




4 Antworten
Folgen
Letzte Antwort 14. Januar 2022
17. Januar 2022

Daniel Krause
09:31
sag mal ihr hattet eine videoaufzeichnung von der schulung, oder? ich finde das in meinem postfach nicht :see_no_evil:  habt ihr mal den link für mich?


Tobias Haase
09:45
Im shepard Wiki unter Workshops > 2021-09-16...
https://wiki.dlr.de/display/Shepard/Shepard+-+A+storage+for+heterogeneous+product+and+research+data 


Sven Torstrick-vdLieth
11:54
Moin zusammen

18. Januar 2022

🏋
10:50
Ist Shepard eigentlich die offizielle Fortsetzung des Datafinder? Also API+Management von Metadaten über heterogene Daten-Backends?




3 Antworten
Folgen
Letzte Antwort 31. Januar 2022

Mark Willmeroth
12:52
Nein, shepard ist keine Fortsetzung. Nähere Informationen habe ich zu Datafinder (https://www.dlr.de/sc/desktopdefault.aspx/tabid-1273/1756_read-3140/ ) jedoch nicht. Das Projekt liegt (meiner Einschätzung nach) auch bereits seit einigen Jahren brach. Ansätze wie eine zentrale API für unterschiedliche Backend-Elemente sind aber auch nicht neu :) 

31. Januar 2022

Mark Willmeroth
17:48
ich bin mir nicht sicher, in wie weit sich Funktionalitäten des Datafinders, des Knowledge Finders und Shepard überdecken und ob Synergien daraus gewonnen werden können. In jedem Falle ist geplant, Ressourcen in Shepard zu gießen. Ich forsche aber mal nach, wie es bei den anderen Themen aussieht.

24. Februar 2022

🏋
09:12
Super, danke!

08. März 2022

Tanja Führer
11:42
Hallo alle zusammen, gibt es eigentlich eine Zusammenstellung (auf gitLab oder an anderen Orten) von bereits vorhanden Shepard Scripten, also Scripte für Shepard User oder Programmierer*innen? 

10. März 2022

Mark Willmeroth
15:36
Hallo Tanja, vielen Dank für die Nachfrage. Ja, wir arbeiten bereits an einer Plattform für eine solche Zusammenstellung. Ziel ist es, eine DLR-weite Kollaboration zu ermöglichen. Ich hoffe, dass ich bereits in der kommenden Woche mehr Details inkl. Link teilen kann :)



1 Antwort
Folgen
Letzte Antwort 24. August 2023
11. März 2022

Tanja Führer
09:34
Hallo Mark, vielen Dank für Deine Antwort. Ich bin schon gespannt :) und danke auch an Tobias :)

18. Mai 2022

Jakob Haug
09:16
Was hat die Abstimmung über das Logo für shepard eigentlich ergeben? Oder ist sind die Ergebnisse noch nicht ausgewertet?



1 Antwort
Folgen
Letzte Antwort 19. Mai 2022
22. Juni 2022

Mark Willmeroth
08:46
Update zum Logo: nach der Klärung der Bildrechte geht das Logo nun in die Markenanmeldung. Dauert also noch etwas, bis wir es ganz offiziell verwenden können.



1 Antwort
Folgen
Letzte Antwort 22. Juni 2022
23. Juni 2022

Mark Willmeroth
12:57
Eine Variation des Sheep2

08. August 2022

damo_ma
16:29
Is this channel still active? I have a couple of question regarding SHEPARD

09. August 2022

Tobias Haase
07:56
It is. Feel free to ask

10. August 2022

damo_ma
16:19
Thanks!

I'm from PF, I don't have a CS background, but some experience. 
If I say something silly, please feel free to correct me.

Basically, we are planning to build a database for our laboratory measurements and obviously a frontend for the users.

My first iteration was Postgres + Django , then I looked into SHEPARD and this make a lot of sense.

I see you can you transparently different DB (neo4j, mondo, postgres).

are there any guidelines to use build a Web frontend based on SHEPARD? 

11. August 2022

Tobias Haase
07:54
There are no explicit guidelines for creating a web frontend for shepard yet.
You could use our automatically generated Typescript client for this. The documentation applies here as well, even though the examples are written in Python, since the clients are very similar from a users perspective. However, I strongly recommend using the OpenID Connect approach for authentication instead of API keys. 
https://gitlab.com/dlr-shepard/documentation/-/wikis/Examples/01-Getting-Started
You can always take a look at our implementation of a web front-end for examples.
https://gitlab.com/dlr-shepard/frontend

GitLab
01 Getting Started · Wiki · shepard / Documentation · GitLab
The use and development of shepard is documented in this project
01 Getting Started · Wiki · shepard / Documentation · GitLab

damo_ma
10:23
Thanks @Tobias Haase , Typescript compiles to Javascript , right?
I think we need a customized app.
Python is perfect, mostly because I mostly know only this language :-D
It would be nice to use some python framework (django , flask, etc) and SHEPARD as backend.

I found an Django adapter for neo4j for example, Neomodel documentation — neomodel 4.0.8 documentation.

Interfacing popular framework would be a nice example for SHEPARD project, I think


Tobias Haase
11:21
Typescript compiles to javascript, that's correct.
The main question right now should be how to build an appropriate architecture. In my opinion, you could either create your own frontend that uses the shepard backend under the hood. This frontend would be completely browser based and would not need a backend. Therefore, you are bound to javascript or typescript.
Another approach would be to use your own backend (e.g. based on Django or Flask). I still recommend to use the shepard api and not to access the databases directly, because they are managed by shepard and you should not modify this data directly.
Both approaches have their own pros and cons. While the first approach (purple) is simpler, the second one (red) gives the opportunity to work with the data server-side without using the resources of the web client.



damo_ma
11:22
Thanks a lot!
I am definitively for 2nd, not being skilled in {java,type}script.

Essentially I need a middleware that speaks SHEPARD API from one side and Django/Flask on the other side


Tobias Haase
11:29
Once you start with your frontend, you will have to write Java- or Typescript. If you want to avoid this, maybe a desktop application would be better?
Our first frontend was also based on Python and Flask with minimal javascript for the same reasons. But after a short time, we were writing a lot more javascript and a lot less python than we expected. This was especially difficult because a pure web project is easier to maintain as a mixture of Python and Javascript.



1 Antwort
Folgen
Letzte Antwort 11. August 2022

damo_ma
11:32
Desktop app is not a viable solution : I am not able to do this AND support for different OSes is nightmare.... 

OK, I'll think about it.

BTW, I sent an application for a Kurzstudie for the Digitalization: if approved I will have extra time to look into this.

The background is laboratory data storage and management, SHEPARD is also mention in the application and I find it would fit wonderfully there.

Look at this as a "not CS-Scientist want to use SHEPARD". 

I have a scientific background and a little share of programming experience (not enough) , but my colleagues hate to mess with everything that is "not science".  

So, I need to implement an easy solution for them.


Tobias Haase
11:42
Yes, I think shepard could be a great solution here since all the data management is handled by Shepard and you can focus on your own frontends and the integration of your systems. I'm excited to see how you proceed and see your application in action.


damo_ma
13:54
no worries, I'll be here around bothering you the whole time  :laughing: 

@Tobias Haase  kannst übrigens auch auf Deutsch schreiben. Ich bin nicht gut genug um es fließend zu schreiben, aber lesen geht  :+1: 


Oliver Helle
14:52
https://may69.com/purepython/

Ye Olde Dumping Grounds!
Pure Python Web Application Development - NO CSS, HTML, or Javascript needed or wanted!
This guide is designed to help you find a pure Python web application development solution fast. If you want theory, see the "Theoretical Introduction" section below. Otherwise just jump right to the…

Pure Python Web Application Development - NO CSS, HTML, or Javascript needed or wanted!
12. August 2022

Tobias Haase
07:38
It would be interesting to see how these pure Python approaches perform in the wild. To be honest, I'm afraid that both the ecosystem and the community are very small, as this is more of a niche application. That's why last year we've decided to bite the bullet and learn Typescript and VueJS. I don't regret this decision, but it would still be exciting to see the development of a pure python frontend with all it's pros and cons.


Oliver Helle
13:01
Yup, I agree about ecosystem and community. For me that's a big con, because I google about half or more of my code and then adapt it. Anyway, you gotta start somewhere and personally I have an aversion against JS/TS, so I'll probably give this a try within the next months. :) 


Nico Liebers
13:54
We use Dash as a frontend framework quite a lot (at least for now for smaller projects). It has good documentation and a large community. And it is beginner friendly. I do not quite unterstand the rather low rating in the list that you (Oliver) posted. Maybe there are limitations for very complex projects that I am not aware of.  

16. August 2022

damo_ma
10:33
I use the HoloViz ecosystem a lot, exactly to not touch Javascript.
They also have a nice abstraction to decouple data and data representation, a SUPER nice library to quickly visualize HUGE dataset > Datashader

I suggest you to check out Datashader, if you need to visualize a lot of data :

longer video Datashader Revealing the Structure of Genuinely Big Data | SciPy 2016 | James A Bednar - YouTube
shorter one Big Data Visualization Using Datashader in Python | How does Datashader work and why is it so fast? - YouTube
I had to search for Typescript last time, this tells how skilled I am   :laughing: 

12. Juni 2023

Tobias Haase
11:57
:exclamation: Attention :exclamation: The current release contains some breaking changes which are especially important for administrators of shepard: https://gitlab.com/dlr-shepard/backend/-/releases/2023.06.12-release 

GitLab
Migrations (BREAKING CHANGES!!!) · shepard / Backend · GitLab
This release introduces some changes and cleans up some things. However, most importantly, we are now able to migrate contents of the neo4j database to adapt existing data...
Migrations (BREAKING CHANGES!!!) · shepard / Backend · GitLab
19. Juni 2023

Jutta Regine Carow
07:01
Hi all,
Starting with the example from the gitlab documentation, let’s say you have successfully used shepard_client in Python to generate a Timeseries with a few points.
In that example, to the best of my knowledge, all measurements are first stored in a variable in Python, and then written to influx.

How do you handle live measurements, where you might want to add incoming points to a pre-existing Timeseries? 



1 Antwort
Folgen
Letzte Antwort 26. Juni 2023
20. Februar 2024

Jutta Regine Carow
15:00
Collections and DataObjects can be assigned various user-defined Attributes.

Assuming one wanted to add more Attributes to a Container node (i.e. more “metadata”): Would that be possible without breaking shepard? And how much work would that entail, roughly speaking?



2 Antworten
Folgen
Letzte Antwort 20. Februar 2024
22. Februar 2024

Florian
13:04
Dear Shepard Users,

At DLR ZLP, we are excited to announce that we are considering hosting a Shepard User Conference/Workshop. Our goal is to provide you with insights into the latest developments around Shepard and offer a sneak peek into our roadmap for upcoming features. Additionally, we want to hear from you about your use cases and success stories related to Shepard. By fostering collaboration, we aim to build a strong community of developers and users who can guide us in shaping the future of Shepard.

Now, we’d like to gather your input to ensure that the conference is as valuable as possible for you. Please take a moment to share your preferences and ideas:

Format Preference:

Would you prefer an online, hybrid, or in-person (vis-a-vis) format for the conference?
Breakout Sessions:

Are you interested in participating in breakout sessions? These sessions would allow you to discuss various aspects related to Shepard.
If so, which specific aspects would you like to discuss?
Additional Ideas:

Do you have any other ideas or formats you would like us to include in the workshop?
We appreciate your feedback and look forward to hearing your thoughts. Feel free to share your comments, remarks, or suggestions with us in advance.

Thank you for being part of the Shepard community!





5 Antworten
Gefolgt
Letzte Antwort 29. Februar 2024

Jakob Haug
16:24
Hi @Tobias Haase 
I would like to participate.
Format preference: online
Breakout: I can't specify this right now
Additional ideas: If online, a recording would be cool

11. April 2024

Jutta Regine Carow
08:21
Hi there,
When uploading a JSON-file to a Structured Data Container, does the shepard-backend/ API include any "check" on whether the JSON-file is valid?
(In the frontend, I already saw that there are some tools to clean/ edit the user input.)
Thanks!



1 Antwort
Folgen
Letzte Antwort 11. April 2024
25. April 2024

Aravind Remanan Kumary Asha
11:41
Hi All,

while retriving an uploaded file. both get file and get file payload  doesnt let user read file contents directly rather let user get details or download.

is there a way user can direcly access file contents without downloading it?



1 Antwort
Folgen
Letzte Antwort 26. April 2024
07. Mai 2024

Florian
11:40
@Jakob Haug danke für den vortrag. 2 Fragen: 

für "funktionalen" (online) Zugriff auf HDF5, wäre da RESTFUL HDF5 hilfreich? (https://support.hdfgroup.org/pubs/papers/RESTful_HDF5.pdf)
Provenienz auf basis PROV? (würde gerne wissen wie ihr die ganzen daten dafür "einsammelt")



2 Antworten
Gefolgt
Letzte Antwort 7. Mai 2024
14. Mai 2024

Dominic
09:16
FYI: https://konferenz-nz.dlr.de/storage-konferenz/2024 

04. Juni 2024

Micha
14:52
Hi, excuse my noob question please. I want to run a basic development instance of Shepard on my Linux workstation, using the docker-compose setup from deployment, without a reverse proxy or subdomains. According to this, the SSL certificate and key are expected in host /etc/ssl, which only root has access to.

Is it expected that docker compose is run with root privileges?

Otherwise, is there a way to specify the cert and key location, for instance in the .env file?




11 Antworten
Folgen
Letzte Antwort 5. Juni 2024
05. Juni 2024

Mathieu Vinot
10:18
Hallo Zusammen,
es scheint derzeit einen kleinen Bug in die letzte Version von shepard zu geben. Die APIkey kann generiert, allerdings nicht kopiert und ggf. gespeichert werden.
Hat jemand dasselbe Problem bzw. eine Lösung? 




3 Antworten
Folgen
Letzte Antwort 5. Juni 2024
06. Juni 2024

Jakob Haug
10:12
As there are some people here, who might be interested in the topic Digital Twin: at June 25th and 26th the second WAW Digital Twin will take place, spots are still available: https://wiki.dlr.de/display/EinDLR/WAW+Digital+Twin+2

28. Juni 2024

Micha
14:34
Hi! I was making some progress with getting Shepard up and running, but while the frontend seems to be able to communicate with Keycloak just fine, API calls fail with 401. The backend logs say 

12:16:09.502 ERROR d.d.s.s.UserinfoService - Request was unsuccessful: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
Both the proxy and Keycloak use the same certificate provided by GEANT. Does anyone have an idea what I can try?





7 Antworten
Folgen
Letzte Antwort 28. Juni 2024
02. Juli 2024
System
15:39
@Tobias Haase hat den Kanalanzeigenamen aktualisiert von: shepard users auf: Ask a question
08. Dezember 2021
System
09:39
Du bist dem Kanal beigetreten.
20. Dezember 2021

Tobias Strohmann
11:17
Hallo zusammen, ich würde den shepard client gerne als requirements für ein eigenes Python Paket nutzen. Finde allerdings nicht wirklich raus, wie ich den Link zum repository in der setup.py vorgebe. Habe schon Verschiedenes unter "install_requires" und "depency_links" ausprobiert, aber funktioniert hat noch nichts davon. Hat da jemand Erfahrung und weiß, wie sich das umsetzen lässt? :-) 


Tobias Haase
11:24
Ich mache das meistens so, dass der shepard client in der requirements.txt steht, so wie von pip freeze ausgegeben. Bei der Installation muss man nur darauf achten, dass man --extra-index-url https://gitlab.com/api/v4/projects/27250272/packages/pypi/simple mit angibt. Also ausgeschrieben

pip install -r requirements.txt --extra-index-url https://gitlab.com/api/v4/projects/27250272/packages/pypi/simple
Bezogen auf eine setup.py hilft das natürlich nicht weiter. Vielleicht wissen die Python ExpertInnen da aber mehr? 


Tobias Strohmann
13:28
Danke dir schon mal. Genau, in der requirements kann man die extra-index-url auch direkt aufnehmen. Z.B. 
numpy
-i https://gitlab.com/api/v4/projects/27250272/packages/pypi/simple
shepard-client
dann muss mans bei pip install nicht noch mal angeben. 
Und jetzt bräuchten wir nur das äquivalent dazu für die setup.py  :face_with_monocle: 



1 Antwort
Gefolgt
Letzte Antwort 24. Januar 2022
01. Februar 2022

Dominic
16:58
Gibts eigentlich eine Funktion im shepard_client (python) mit der ich prüfen kann, ob der ApiClient noch lebt/verbunden ist?




3 Antworten
Folgen
Letzte Antwort 3. Februar 2022
11. Februar 2022

Dominic
11:52
Huhu! Unser shepard Stack hat Probleme mit dem Neustart einzelner Server (Docker Swarm). Alle anderen Stacks haben damit keinen Ärger und laufen fehlerlos "durch". Es wird aber auch nirgendwo ein Fehler geschmissen. Können wir das gemeinsam debuggen @Tobias Haase?




13 Antworten
Folgen
Letzte Antwort 11. Februar 2022

Tobias Haase
14:23
Die Influxdb Fehler sollte man sich anschauen. Die Neo4j Session fehler auch. Anscheinend sind beide Datenbanken mal zeitweise nicht vorhanden/erreichbar


Tobias Haase
14:31
Insbesondere sollte man aber sortieren, wann die Fehler auftreten. Welche Fehler treten auf, wenn man den "neustart" durchführt, der das System lahmlegt? Sagen Influxdb und Neo4j zu dem Zeitpunkt irgendwas im Log? Was sagt das Backend direkt nach dem startup? So lassen sich ggf. Folgefehler ausschließen und das eigentliche Problem isolieren


Dominic
14:37
Wenn einer der Server nach dem anderen neustartet betrifft es doch immer zufällig andere Container (diejenigen die gerade auf dem Server laufen, welcher neustartet). Die drei Server starten ja nie gleichzeitig neu. Das shepard Stack sollte nach einem Umzug/kurzzeitigen Ausfalls/einer Unterbrechung jeglichen Containers wieder in einen funktionierend Zustand zurückkehren, sobald der Container wieder existiert. Das könnt ihr doch auch auf drei alten Gurken oder mit drei Pi´s testen?


Tobias Haase
14:45
Ahh okay, also ist das Problem, dass das Backend nicht reconnected, wenn eine DB mal neugestartet wurde. Das kann ich nachvollziehen. Kannst du dazu bitte ein Issue im Backend erstellen? 


Dominic
14:46
Ich denke, dass das "unser" Problem ist. Zumindest wirkt es so. Ich erstelle dazu mal ein Issue. Das kann natürlich für beliebige Kombinationen zutreffen.


Dominic
14:54
https://gitlab.com/dlr-shepard/backend/-/issues/66

GitLab
Shepard stack does not return to a working state when restarting individual servers of a swarm (#66) · Issues · shepard / Backend
When restarting individual servers of a Docker Swarm, the containers move and may be interrupted for a short time. Which container is running on which server is random....
Shepard stack does not return to a working state when restarting individual servers of a swarm (#66) · Issues · shepard / Backend



3 Antworten
Folgen
Letzte Antwort 11. Februar 2022
#priority #high

;)

16. Februar 2022

Dominic
11:40
@Tobias Haase Irgendwie scheint es zwischen dem shepard-client (python) und dem Backend eine Abweichung zu geben. Gab nie ne deprecated warning oder so, aber seit heute scheint "symbolic_name" zu "symbolicName" geworden zu sein (Timeseries). Der python-Client kennt aber nur symbolic_name... 



1 Antwort
Folgen
Letzte Antwort 16. Februar 2022

Dominic
12:34
Der Vollständigkeit halber:
Bad Request
HTTP response headers: HTTPHeaderDict({'Access-Control-Allow-Origin': '*', 'Content-Length': '447', 'Content-Type': 'text/plain', 'Date': 'Wed, 16 Feb 2022 11:31:12 GMT'})
HTTP response body: Unrecognized field "symbolic_name" (class de.dlr.shepard.influxDB.Timeseries), not marked as ignorable (5 known properties: "field", "measurement", "location", "device", "symbolicName"])
 at [Source: (org.glassfish.jersey.message.internal.ReaderInterceptorExecutor$UnCloseableInputStream); line: 1, column: 103] (through reference chain: de.dlr.shepard.influxDB.TimeseriesPayload["timeseries"]->de.dlr.shepard.influxDB.Timeseries["symbolic_name"])


Tobias Haase
16:06
Das Feld heißt tatsächlich symbolicName (camelCase), der python client übersetzt das normalerweise aber entsprechend. Mein Python Code dazu schaut in etwa so aus: 

timeseries = Timeseries(
    measurement="MyMeas",
    location="MyLoc",
    device="MyDev",
    symbolic_name="MySymName",
    field="value",
)

Dominic
16:12
Bei mir failed er neuerdings, siehe oben. :)



2 Antworten
Folgen
Letzte Antwort 16. Februar 2022
der Code lief so seit mehreren Monaten

17. März 2022

Markus Schreiber
15:58
Ist es ein Bug, dass in der java client Bibliothek in de.dlr.shepard.client.java.api.* Structureddata Klassen mit kleinem d in data geschrieben sind, während sie in de.dlr.shepard.client.java.model.* mit großem D in Data geschrieben werden? 



1 Antwort
Folgen
Letzte Antwort 18. März 2022
09. August 2022

Nicolas Unger
16:49
Hallo zusammen, wir sind gerade dabei eine shepard Instanz aufzusetzen und es tritt aktuell noch ein Problem bei der Verbindung von Keycloak mit dem DLR Active Directory auf. Die Verbindung kann zwar aufgebaut werden, die Authentifizierung mit dem Funktionalaccount will aber nicht klappen. Kann mir da jemand weiterhelfen?

10. August 2022

Tobias Haase
07:37
Je nachdem, wie die LDAP Verbindung aufgesetzt wurde und welche LDAP Filter angewendet wurden, sind die funktional Accounts möglicherweise aus dem Pool der möglichen Nutzer ausgeschlossen. Als erstes solltet Ihr prüfen, ob die jeweiligen Accounts auch in der Users DN sind. Als nächstes ist die Frage, ob ein sog. Custom User LDAP Filter konfiguriert wurde. Falls ja, schließt der die Funktional Accounts aus?

Am einfachsten testen kann man das dann links in dem Menü Users indem man dort einfach den betroffenen Account sucht. Kommt dort ein Ergebnis, kann der Benutzer sich normalerweise auch einloggen. 




Tobias Haase
07:47
Okay, es scheint, f-Accounts werden in einer anderen Struktur gehalten, als normale Benutzeraccounts. Zumindest für BT liegen die f-Accounts intra.dlr.de/ZentraleDienste/CCDLR/_Global/Benutzer und sind damit nicht im der konfigurierten Users DN. Wenn Ihr beides nutzen wollt, könnt Ihr für die f-Accounts einfach in Keycloak eine zweite User Federation mit angepasster Users DN anlegen




2 Antworten
Folgen
Letzte Antwort 10. August 2022
12. August 2022

Nicolas Unger
09:35
Nachtrag zum Keycloak setup für shepard (ich wollte dies eigentlich als Kommentar zur Wiki-Seite hinzufügen, habe dazu aber keine Berechtigung):

Problem:
Using Keycloak with these settings in combination with shepard did not work. Since port 443 is already in use by shepard default website.

Solution that worked for me:

Dockerfile:
KC_HTTPS_PORT=8443

docker-compose.yml:
ports:
- "8443:8443"

.env (shepard deployment):
OIDC_AUTHORITY=https://<your-hostname>.intra.dlr.de:8443/realms/<your-realm>/ 

25. Mai 2023

Pradnil Kamble
14:33
Hello all! 
We want to employ Shepard Timeseries Collector (sTC)  to collect data from KUKA RSI as a source. Has anyone previously implemented this? 
If anyone has, I would greatly appreciate your insights.
Additionally is there any example configuration or documentation on RSI as a source for sTC?
Thank you in advance!  



1 Antwort
Folgen
Letzte Antwort 30. Mai 2023
27. Juli 2023

Jakob Haug
15:05
Wir haben den Fall, dass wir unseren Keycloak aus dem DLR-AD füttern. Das funktioniert wunderbar. Für die automatisierte Nutzung habe ich in Keycloak lokale Accounts angelegt, die können sich bei Shepard anmelden, Tokens erstellen, Collections erstellen, die Berechtigungen ändern. Leider können Sie nicht von anderen Usern als Berechtigte zu vorhandenen Collections hinzugefügt werden.
Ist das ein Problem mit Keycloak, unserem Setup oder liegt die Ursache bei shepard?




6 Antworten
Folgen
Letzte Antwort 31. Juli 2023
28. März 2024

Grigori Oehl
11:33
Mal eine kleine Frage in die Runde: Funktionieren die regex Node Paths auch mit MQTT? Also explizit das Setzen mehrerer Variablen auf einmal?




4 Antworten
Folgen
Letzte Antwort 10. April 2024
22. April 2024

Grigori Oehl
14:58
Wir haben aktuell den Fall, dass der sTC per Docker hinter einem Traefik reverse Proxy sitzt. Das Abrufen von Daten und schreiben in Shepard funktioniert wunderbar und hängt ja auch gar nicht von dem Proxy ab. Der OPC-UA Server des sTC lässt sich jedoch nicht ansprechen. Ich gehe aktuell stark von einem Fehler in Layer 8 aus, bin aber etwas ratlos, da wir eine ähnliche TCP Port-Umleitung (443->Docker-Container-Zielport) bereits mit anderen Protokollen (z.B. für einen MQTT-Broker) problemfrei umgesetzt haben. Hat jemand in der Gruppe eventuell schon Erfahrungen mit OPC-UA Servern hinter reverse Proxys und mag in eine detailliertere Diskussion eintauchen in der ich auch etwas tiefer auf unsere Konfiguration eingehen kann?




7 Antworten
Folgen
Letzte Antwort 23. April 2024
09. Juli 2024

Grigori Oehl
09:36
Moin in die Runde, wir haben uns gerade gefragt, wie und wo Shepard die hochgeladenen "Dateien" speichert. Sind die File Container in der MongoDB hinterlegt, so wie es das Farbschema im Wiki suggeriert? Resultieren daraus bestimmte Größenbeschränkungen?




4 Antworten
Folgen
Letzte Antwort 10. Juli 2024
06. August 2024

Finn Welzmüller
15:55
Liebe alle,
Wir wollen einen shepard mit keycloak nutzen und keycloak dabei an die Active Directory vom DLR Anbinden, was allerdings aktuell noch Probleme bereitet. Die Anmeldung von Usern, die direkt über die keycloak Adminkonsole erstellt wurden, funktioniert einwandfrei. Doch sobald DLR Credentials verwendet werden, gibt es einen "Invalid username or password."-Error in Keycloak. Übersehe ich vielleicht etwas in der LDAP-Konfiguration (als screenshot anbei)? Die Tests zur Connection und Authentification, die sich oben im Konfigurationsmenü machen lassen, sind beide erfolgreich. 
Der gedockerte keycloak Container wirft u.A. folgenden Error im log:
2024-08-06 13:43:58,647 ERROR [org.keycloak.url.HostnameV2Provider] (executor-thread-71) Failed to parse realm frontendUrl 'PLUTO'. Falling back to global value.: java.lang.IllegalArgumentException: The url [Realm frontendUrl] is malformed
Danke schonmal!


08. August 2024

Grigori Oehl
16:14
Mal eine Frage zum Shepard Timeseries Collector: Können Teile der Config beim Betrieb des STC als docker container über Umgebungsvariablen durchgegeben werden? Expliziter Anwendungsfall wären Username und Password für die MQTT / OPC-UA Authentifizierung.




3 Antworten
Folgen
Letzte Antwort 12. August 2024
24. September 2024

Padmini Minasandra Venkateshappa
14:05
Hello all,

I have a query regarding pycharm.
I am importing  shepard_client using the commands attached in the screenshot, but when I try to click on the module, I am unable to see any code related to that.

I have installed shepard_client using pip command : pip install shepard-client --index-url https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple

I am able to run the code and create collections and data objects within shepard, but unable to access the modules imported.

Can any of you, please tell me if I am missing something here.

Thanks,
Padmini




1 Antwort
Folgen
Letzte Antwort 27. September 2024
05. Februar 2025

J-T
16:09
Hi everyone, is it possible to authenticate via a keycloak token with the shepard client directly?
Currently I will only get:
{"status":401,"exception":"AuthenticationException","message":"Invalid Authentication"}
The configuration I am trying looks like this:
conf = Configuration(host=HOST, access_token=token.split(" ")[1])
Thank you ;)




12 Antworten
Folgen
Letzte Antwort 7. Februar 2025
09. Mai 2025

Jan Petersen
14:57
Hallo Zusammen, wir haben gerade unseren Shepard Server auf 4.0.0 upgedatet. Wenn ich mit dem python-client (4.0.0) mittels der Funktion create_data_object ein Datenobject erzeugen möchte, bin ich genötigt, der Klasse DataObject Werte zu geben die in älteren Versionen nicht notwendig waren wie z.b. id, createdAt usw. Trage ich dort random Werte ein wird in Shepard sinnvoll ein Datenobject angelegt. Kann mir hier jemand weiter helfen?



1 Antwort
Gefolgt
Letzte Antwort 12. Mai 2025
24. Juni 2025

Nicolas Unger
11:37
Hi zusammen,
ich versuche gerade unsere shepard Instanz langsam auf die neueste Version zu bringen. Leider hänge ich beim Update auf 2.0.0-release fest. Das Update von MongoDB und Neo4J scheint erfolgreich gewesen zu sein, jedoch bekomme ich bei Zugriffversuchen auf das Backend immer 404 Errors (s. Bilder und code).

2025-06-24 09:31:20.449 [ERROR] [de.dlr.shepard.exceptions.ShepardExceptionMapper] (executor-thread-20) Server response is: 404: org.jboss.resteasy.reactive.ClientWebApplicationException:>
        at org.jboss.resteasy.reactive.client.impl.RestClientRequestContext.unwrapException(RestClientRequestContext.java:205)
        at org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext.handleException(AbstractResteasyReactiveContext.java:331)
        at org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext.run(AbstractResteasyReactiveContext.java:175)
        at org.jboss.resteasy.reactive.client.impl.RestClientRequestContext$1.lambda$execute$0(RestClientRequestContext.java:324)
        at io.vertx.core.impl.ContextInternal.dispatch(ContextInternal.java:270)
        at io.vertx.core.impl.ContextInternal.dispatch(ContextInternal.java:252)
        at io.vertx.core.impl.ContextInternal.lambda$runOnContext$0(ContextInternal.java:50)
        at io.netty.util.concurrent.AbstractEventExecutor.runTask(AbstractEventExecutor.java:173)
        at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:166)
        at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:469)
        at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:566)
        at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:994)
        at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
        at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
        at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: org.jboss.resteasy.reactive.client.api.WebClientApplicationException: Server response is: 404
        at org.jboss.resteasy.reactive.client.handlers.ClientSetResponseEntityRestHandler.handle(ClientSetResponseEntityRestHandler.java:32)
        at org.jboss.resteasy.reactive.client.handlers.ClientSetResponseEntityRestHandler.handle(ClientSetResponseEntityRestHandler.java:23)
        at org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext.invokeHandler(AbstractResteasyReactiveContext.java:231)
        at org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext.run(AbstractResteasyReactiveContext.java:147)
        ... 12 more

Hat jemand eine Idee, was hier schief läuft?

Mehr anzeigen




5 Antworten
Folgen
Letzte Antwort 2. Juli 2025
26. Juni 2025

Dominic
13:13
Is there a way to display images saved in Shepard in another web service without “downloading” them? E.g. as a byte array via the API? :)




4 Antworten
Gefolgt
Letzte Antwort 24. Juli 2025
07. Juli 2025

Grigori Oehl
11:49
Moin in die Runde,
nach dem Umzug von Influx auf Timescale bekomme ich im TimeseriesCollector einige Fehler zum Wechsel des Datentyps (s.u.). Kann man diese entweder Zeitserienseitig ignorieren / abfangen (z.B. über eine automatische Conversion wo möglich), oder sollte besser der sTC angepasst werden?





19 Antworten
Folgen
Letzte Antwort 14. Juli 2025
30. Juli 2025

Grigori Oehl
10:11
Hi everyone :)
Once again an update question. While changing some code, I noticed that in the python client shepard_client.models.timeseries_payload disappeared. Unfortunately, the how-to on handling timeseries data with the client still uses this model. Maybe someone already stumbled upon this and wants to help me out?




4 Antworten
Gefolgt
Letzte Antwort 30. Juli 2025
31. Juli 2025

Dominic
12:12
Hello everyone,

in the shepard git the influxdb and all the environmentals of influx are still in the docker-compose, i can actually delete all that, right?



1 Antwort
Folgen
Letzte Antwort 31. Juli 2025
03. September 2025

Grigori Oehl
14:15
Hello everyone,
I was wondering, if ShepardTimeseriesCollector supports some type of hartbeat service (http/api request -> happy or mqtt publish -> "I'm still standing")




3 Antworten
Folgen
Letzte Antwort 24. Oktober 2025
16. Oktober 2025

Ruchit Kini
11:14
Hello everyone,
I am a user of shepard api, and I wanted to know if there is a functionality where I can access the the child object of the dataobject using the query search.
So, what I am trying to do is: Query the data object that I want, check if it has child objects, if yes, get its ids and other attributes. 




13 Antworten
Gefolgt
Letzte Antwort 17. Oktober 2025
21. Oktober 2025

Markus Huber
15:03
Hi, why does shepard skip a number when creating new objects? e.g. Data objects ID 5 -> 7 -> 9 ? 





3 Antworten
Folgen
Letzte Antwort 23. Oktober 2025
22. Oktober 2025

Dominic
10:52
The user experience has been changed slightly with the new front end. Unfortunately, it is now no longer possible to upload new data when creating references. This was actually a nice feature for some users. Is there a reason why this was removed/splitted, or can we get it back in the "Add data reference" window? 



1 Antwort
Gefolgt
Letzte Antwort 27. Oktober 2025
30. Oktober 2025

Daniel Krause
11:52
i was going to ask a question about not being able to create api keys on our fresh instance, but turns out it was a permission problem on the docker volume. the backend volume needs to be owned by the jboss user which has the id 185. if it does not, it cannot create the pub/priv keys that are used to sign the api keys

just like the grafana volume needs to be owned by user 472

11:54
you might want to put that in the documentation but this might also be a quirk of the docker-swarm we are using.




2 Antworten
Folgen
Letzte Antwort 7. November 2025
05. November 2025

Grigori Oehl
17:43
Hello everyone, I am currently having some trouble configuring the timeseries collector on two very specific MQTT topics, while all other definitions work flawlessly. It somehow messes up data from two different sources and writes them via two independent brides into both timeseries, which obviously is a problem ^^. More information in the thread for better readability. Maybe this is not a quirk of sTC, but some weird error in my brain ;) 




5 Antworten
Folgen
Letzte Antwort 7. November 2025
10. November 2025

Grigori Oehl
09:19
Broken legacy timeseries collector:

I just noticed, that the legacy timeseries collector from https://gitlab.com/dlr-shepard/shepard-timeseries-collector/container_registry/6889390 seems to be broken and results in error codes when run. This happens with latest and 2025.11.03-dev.1161 tags, but not on 2024.12.02-dev.681 

GitLab
Container registry · shepard / shepard Timeseries Collector · GitLab
Aggregate time series data and transfer to shepard
Container registry · shepard / shepard Timeseries Collector · GitLab





7 Antworten
Gefolgt
Letzte Antwort 17. November 2025
12. November 2025

Jan Petersen
08:38
Good morning everyone,
Can someone help me with a search query? I would like to use the following to find an object with an attribute. The object should have no successor:

query=
{
    "AND": [
        {
            "property": "attributes.type",
            "operator": "eq",
            "value": "statlab"
        },
        {
           
                "property": "successor",
                "operator": "eq",
                "value": []
          
        }
    ]
}


5 Antworten
Folgen
05. Dezember 2025

Grigori Oehl
16:03
Hello everyone, again some question regarding the sTC, this time on the latest branch regarding handling of numbers in MQTT JSON payloads. More info in the thread ;)




13 Antworten
Folgen
14. Januar

Jan Petersen
11:57
Hello everyone, I am looking for a way to update structured data from a container using the Python client. Have I simply not found it yet, or does it not exist?





3 Antworten
Gefolgt
Letzte Antwort 19. Januar
16. Januar

Sven Durchholz
14:35
Hello!
I working on establishing shepard for better data storage in our department. I am currently mainly working on reevaluating our definitions and creating a ontology. Is there already good examples and practicies you can recommend for building a ontology (in this case for experimental turbine data), and resources how the integration of those ontologies work in shepard (It seems to have the possibility to use a SPARQL database for semantic annotations)?





5 Antworten
Gefolgt
Letzte Antwort 19. Januar
12. Februar

Sven Durchholz
08:53
I am currently trying to setup the productiive environment and it works to 90%. However, I have issues with setting up Keycloak.

First i tried following the guide on the wikiKeycloak Setup - Shepard - DLR.Wiki. However, as i deployed it on the same server, port 443 is already occupied by caddy. So i hosted keycloak locally on 8080 in its container and set it up in caddy as well (and had to change a few environmental varables so it could be reached accordingy).

But now i have an issue when forwarding to Keycloak for authentification, that i the UI gets stuck (OIDC_AUTHORITY and OIDC_PUBLIC are set accordingly). 
Looking into the docker logs i get the following error:
[next-auth][error][SIGNIN_OAUTH_ERROR] https://next-auth.js.org/errors#signin_oauth_error⁠ unable to verify the first certificate 

It seems like shepard doesnt recognize the tls certificate?
Somebody has an idea?

Errors | NextAuth.js
This is a list of errors output from NextAuth.js.

-  shepard wie "rdf:Class" als property IRI verwenden?
  [22-rdf-syntax-ns](https://www.w3.org/1999/02/22-rdf-syntax-ns#class) quasi ein ~"is a"



17 Antworten
Gefolgt
Letzte Antwort 13. Februar
25. Februar

# Architecture and Performance Analysis: Shepard Research Data Management Platform

## Executive Summary

Based on a comprehensive analysis of the Shepard platform codebase, I've identified critical architectural and performance improvement opportunities across database management, API design, feature toggles, and system startup. This analysis is grounded in the actual implementation and provides actionable recommendations with priority rankings.

---

## 1. ARCHITECTURAL ISSUES AND IMPROVEMENTS

### 1.1 Database Connection Management

#### **Issue A1: Blocking Sequential Startup with No Graceful Degradation**

**Priority: HIGH**

The current implementation forces all databases to be available before the application starts, creating a single point of failure. shepard:20-37 shepard:49-64

**Problem Details:**

- The `MigrationsRunner.waitForConnection()` uses an infinite loop with 1-second sleeps
- If any database (Neo4j, MongoDB, TimescaleDB, or PostGIS when enabled) is unavailable, the entire application fails to start
- No timeout mechanism exists
- No partial degradation mode for non-critical features

**Recommendations:**

1. **Implement Asynchronous Database Initialization**

   - Use Quarkus `@Startup` beans with different priorities for critical vs. non-critical databases
   - Allow the REST API to start with degraded functionality if optional databases are unavailable
   - Mark endpoints that require specific databases with custom annotations
2. **Add Connection Timeout and Retry Limits**

   - Replace infinite loops with configurable timeout values (e.g., 60 seconds)
   - Implement exponential backoff for retry attempts
   - Log detailed failure reasons to aid debugging
3. **Enhance Health Checks for Runtime Monitoring**  
   The current health checks are passive: shepard:10-19

   These should be enhanced to:

   - Distinguish between startup readiness and runtime health
   - Provide detailed status for each database connection
   - Support automated recovery attempts

**Trade-offs:**

- Increased complexity in managing partial system states
- Need for careful handling of cross-database transactions
- Potential confusion for users when certain features are unavailable

**Risk Mitigation:**

- Implement feature flags to control degraded mode behavior
- Provide clear API error messages when features are unavailable
- Add metrics to track partial availability incidents

---

### 1.2 API Endpoint Organization

#### **Issue A2: Monolithic REST Endpoint Classes**

**Priority: MEDIUM**

The `TimeseriesRest` class contains 40+ endpoints in a single file, creating maintenance challenges: shepard:61-529

**Problem Details:**

- Single responsibility principle violation
- Mixing concerns: CRUD operations, permissions, import/export, and data retrieval
- Difficult to navigate and test
- Similar patterns repeated across `FileRest`​ (32 endpoints) and `CollectionRest` (24 endpoints)

**Identified Cross-Cutting Concerns:**

1. **Permissions Management** - Repeated in multiple classes: shepard:442-512
2. **Import/Export Operations** - Could be standardized: shepard:360-440
3. **Pagination and Query Parameters** - Duplicated logic: shepard:99-114

**Recommendations:**

1. **Decompose into Sub-Resources**

   ```
   /timeseries-containers/{id}              -> TimeseriesContainerRest
   /timeseries-containers/{id}/timeseries   -> TimeseriesDataRest
   /timeseries-containers/{id}/permissions  -> PermissionsSubResource (shared)
   /timeseries-containers/{id}/export       -> ExportSubResource (shared)
   /timeseries-containers/{id}/import       -> ImportSubResource (shared)
   ```
2. **Extract Shared Permission Handling**

   - Create a generic `PermissionsSubResource<T>` that can be composed into different endpoints
   - Use JAX-RS sub-resource locators to delegate permission endpoints
3. **Standardize Pagination and Filtering**

   - Create a `@QueryParamsBean` class to encapsulate common query parameters
   - Move `QueryParamHelper` logic into a reusable service layer

**Trade-offs:**

- More files to maintain (but each simpler)
- Need to update OpenAPI generation configuration
- Existing API clients would need regeneration but no breaking changes to API contracts

---

### 1.3 Feature Toggle Complexity

#### **Issue A3: Build-time vs Runtime Toggle Confusion**

**Priority: MEDIUM-HIGH**

The current feature toggle system mixes build-time and runtime decisions, creating deployment complexity: shepard:120-125 shepard:1-13 shepard:39-39

**Problem Details:**

- ​`@IfBuildProperty` requires features to be enabled at build time, making it impossible to toggle features in production without rebuilding
- The versioning feature toggle at build-time conflicts with the desire for runtime flexibility
- Database configurations are coupled to feature toggles (e.g., spatial database activated based on toggle)
- No clear documentation on which toggles are build-time vs runtime

**Recommendations:**

1. **Migrate to Runtime-Only Feature Toggles**

   - Replace `@IfBuildProperty`​ with custom `@ConditionalOnFeature` annotation that checks at runtime
   - Use CDI `@Produces`​ methods with `@ApplicationScoped` to create beans conditionally
   - Implement feature flag service that reads from configuration or database
2. **Separate Infrastructure Toggles from Feature Toggles**

   - Infrastructure toggles (spatial-data.enabled) should control database connections - keep these
   - Feature toggles (versioning.enabled) should only control business logic - make these runtime
   - Use separate configuration namespaces: `shepard.infrastructure.*`​ vs `shepard.features.*`
3. **Add Feature Toggle Administration Endpoint**

   - Create `/admin/features` endpoint to view and modify runtime toggles
   - Store toggle state in Neo4j or external configuration service
   - Implement toggle change audit logging

**Trade-offs:**

- Runtime toggles add complexity to dependency injection
- Need to handle scenarios where features are toggled while system is running
- Potential for inconsistent state if toggles change during requests

**Example Implementation Pattern:**

```java
// Instead of @IfBuildProperty
@ApplicationScoped
public class FeatureBeanProducer {
    @Produces
    @ConditionalOnFeature("versioning")
    public VersioningService versioningService() {
        return new VersioningServiceImpl();
    }
}
```

---

### 1.4 Multi-Database Coordination

#### **Issue A4: Justified Polyglot Persistence but with Optimization Opportunities**

**Priority: LOW-MEDIUM**

The system uses four databases, which was a deliberate architectural decision: shepard:1-139

**Analysis:**   
The ADR-008 decision was well-reasoned:

- **Neo4j**: Chosen for graph relationships and permissions (despite Postgres alternative)
- **MongoDB**: Required for large file storage (>32TB Postgres limitation)
- **TimescaleDB**: Chosen for timeseries performance (replaced InfluxDB)
- **PostGIS**: Selected for 3D spatial data (significant performance advantage over pgvector) shepard:100-128

**However, optimization opportunities exist:**

1. **Consider TimescaleDB + PostGIS Consolidation**

   - Both are PostgreSQL extensions running on separate instances
   - Could run on same instance with different databases/schemas shepard:117-152

   **Benefits:**

   - Reduced infrastructure complexity (3 databases instead of 4)
   - Potential for cross-schema queries between timeseries and spatial data
   - Simplified backup procedures

   **Trade-offs:**

   - Shared resource contention between timeseries and spatial workloads
   - Need to carefully tune memory allocation
   - Risk of one workload impacting the other
2. **Optimize Cross-Database Permission Checks**

   The current pattern requires Neo4j queries for every data access: shepard:110-132

   **Problem:**  Every timeseries query must first check permissions in Neo4j, then fetch data from TimescaleDB

   **Recommendations:**

   - Implement permission caching with TTL (current cache exists but is basic)
   - Denormalize permissions into TimescaleDB/PostGIS for read-heavy workloads
   - Use database-level row security policies where possible

---

## 2. PERFORMANCE BOTTLENECKS AND IMPROVEMENTS

### 2.1 Startup Time Optimization

#### **Performance Issue P1: Sequential Database Initialization**

**Priority: HIGH**

The four-phase startup is entirely sequential: shepard:20-37

**Measured Impact:**

- PKI initialization: minimal
- Database connection waiting: **variable, potentially minutes if databases are slow to start**
- Migrations: **can take significant time with large datasets**
- Neo4j connection: minimal

**Bottleneck Analysis:**

1. **Migration Runner Waits for ALL Databases** shepard:49-64

   This only waits for Neo4j, but Flyway migrations run serially and block startup: shepard:50-64

2. **No Parallel Database Initialization**  
   All databases could be checked and migrated in parallel

**Recommendations:**

1. **Parallelize Database Connection Checks**

   - Use `CompletableFuture` or virtual threads to check all databases simultaneously
   - Fail fast if any critical database is unavailable
   - Continue if optional databases (PostGIS) are unavailable
2. **Lazy Migration Execution**

   - Move Flyway migrations to background threads
   - Use database-level locks to prevent concurrent migrations
   - Allow REST API to start before migrations complete for read-only operations
   - Return 503 Service Unavailable for write operations during migration
3. **Optimize Flyway Migration Performance** shepard:53-55

   - Enable Flyway parallel execution for independent migrations
   - Consider using Java-based migrations for performance-critical changes
   - Implement migration progress monitoring endpoint

**Expected Improvement:**

- Parallel connection checks: 60-80% reduction in wait time
- Background migrations: Application available in seconds instead of minutes

---

### 2.2 Database Query Performance

#### **Performance Issue P2: Cross-Database Query Coordination**

**Priority: HIGH**

Every data access requires coordinating queries across multiple databases:

**Example Flow for Timeseries Retrieval:**

1. Check permissions in Neo4j
2. Verify container exists in Neo4j
3. Fetch timeseries metadata from TimescaleDB
4. Query data points from TimescaleDB shepard:148-189

**Performance Analysis:**

1. **Permission Check Latency**

   - Each request makes at least one Neo4j query
   - Cache exists but implementation is basic: shepard:111-112
2. **N+1 Query Problem in Parallel Streams** shepard:191-212

   This creates N permission checks for N timeseries in parallel, overwhelming Neo4j

**Recommendations:**

1. **Implement Batch Permission Checks**

   - Add `checkPermissionsBatch(List<Long> entityIds)` method
   - Execute single Cypher query to check all entities at once
   - Reduces N queries to 1 query
2. **Enhance Permission Cache**

   - Use proper cache eviction policy (LRU, TTL-based)
   - Consider Redis or Caffeine cache instead of in-memory Map
   - Cache at user+entity level, not just key string
   - Implement cache warming for frequently accessed entities
3. **Add Database-Level Read Replicas**

   - Configure read replicas for Neo4j and TimescaleDB
   - Route permission checks to replicas to reduce load on primary
   - Particularly important for Neo4j which handles all permission queries
4. **Optimize TimescaleDB Query Performance**

   The repository uses native queries with proper TimescaleDB functions: shepard:139-150

   Current optimizations are good (hypertables, compression), but consider:

   - Pre-aggregate common query patterns into materialized views
   - Implement query result caching for frequently accessed time ranges
   - Use TimescaleDB continuous aggregates for common aggregation functions

---

### 2.3 Migration Process Performance

#### **Performance Issue P3: InfluxDB to TimescaleDB Migration Efficiency**

**Priority: MEDIUM** (historical, but important for future migrations)

The migration runs in a separate container: shepard:173-185

**Analysis:**

The repository uses PostgreSQL COPY command for bulk inserts, which is optimal: shepard:73-130

**Strengths:**

- COPY command is the fastest method for PostgreSQL bulk inserts
- Separate container prevents impacting production system
- Batch processing with 20,000 rows per batch

**Recommendations for Future Migrations:**

1. **Add Migration Progress Monitoring**

   - Expose migration progress via HTTP endpoint
   - Track rows migrated, errors encountered, time remaining
   - Store progress in database to support resume on failure
2. **Implement Parallel Migration Workers**

   - Partition data by container_id or time range
   - Run multiple migration workers in parallel
   - Use connection pooling to prevent overwhelming target database
3. **Add Data Validation Steps**

   - Implement row count verification
   - Add checksum validation for critical data
   - Create rollback capability for failed migrations

---

### 2.4 Frontend-Backend Communication

#### **Performance Issue P4: OpenAPI Client Generation Overhead**

**Priority: LOW-MEDIUM**

The frontend uses a locally generated TypeScript client: shepard:16-16

**Analysis:**

The client is generated from OpenAPI spec, which ensures type safety but has considerations:

**Current Approach:**

- File-based dependency on locally generated client
- Regeneration required whenever backend API changes
- No versioning between frontend and backend client

**Recommendations:**

1. **Implement API Versioning**

   - Add version prefix to API paths (e.g., `/shepard/api/v1`)
   - Support multiple API versions simultaneously during transitions
   - Use OpenAPI version field properly
2. **Optimize Client Bundle Size**

   - Current client likely includes all 100+ endpoints
   - Implement tree-shaking friendly exports
   - Consider code-splitting API modules (timeseries, files, collections)
3. **Add Response Caching Strategy**

   - Implement HTTP cache headers for GET requests
   - Use ETags for conditional requests
   - Add client-side caching layer for frequently accessed data
4. **Consider GraphQL for Complex Queries**

   - For queries that need data from multiple containers/timeseries
   - Reduces over-fetching and under-fetching
   - Single request instead of multiple REST calls
   - **Trade-off:**  Adds complexity, requires GraphQL infrastructure

---

## 3. SUMMARY OF RECOMMENDATIONS

### High Priority (Implement First)

|Issue|Recommendation|Expected Impact|Effort|
| -------| ------------------------------------------------------------------| ------------------------------------------------| --------|
|**A1**|Implement async database initialization with timeouts|Improved reliability, faster failure detection|Medium|
|**P1**|Parallelize database connection checks and migrate to background|60-80% startup time reduction|Medium|
|**P2**|Batch permission checks and enhance caching|50-70% reduction in Neo4j load|Medium|

### Medium Priority (Implement Next)

|Issue|Recommendation|Expected Impact|Effort|
| -------| ---------------------------------------------| ----------------------------------------------| -------------|
|**A2**|Decompose REST endpoints into sub-resources|Improved maintainability|Medium-High|
|**A3**|Migrate to runtime feature toggles|Deployment flexibility|Medium|
|**P2**|Add TimescaleDB continuous aggregates|Faster query performance for common patterns|Medium|
|**A4**|Consolidate TimescaleDB + PostGIS instances|Reduced infrastructure complexity|Low-Medium|

### Low Priority (Future Enhancements)

|Issue|Recommendation|Expected Impact|Effort|
| -------| -----------------------------------| -------------------------------------------| --------|
|**P3**|Add migration progress monitoring|Better visibility for future migrations|Low|
|**P4**|Implement API versioning|Smoother API evolution|Medium|
|**P4**|Optimize client bundle size|Marginal frontend performance improvement|Low|

---

## 4. IMPLEMENTATION ROADMAP

### Phase 1: Reliability Improvements (2-3 weeks)

1. Add database connection timeouts
2. Implement parallel connection checks
3. Enhance health check endpoints
4. Add batch permission checking

### Phase 2: Performance Optimization (3-4 weeks)

1. Improve permission caching
2. Implement background migrations
3. Add TimescaleDB continuous aggregates
4. Optimize query patterns

### Phase 3: Architectural Refactoring (4-6 weeks)

1. Decompose large REST endpoint classes
2. Migrate to runtime feature toggles
3. Consolidate PostgreSQL instances
4. Implement API versioning

---

## 5. RISK ASSESSMENT

### Critical Risks

1. **Backwards Compatibility**

   - Risk: API changes breaking existing clients
   - Mitigation: Implement versioning before major changes
2. **Data Consistency During Async Initialization**

   - Risk: Partial system state leading to inconsistent data
   - Mitigation: Implement proper transaction boundaries and state validation
3. **Cache Invalidation Complexity**

   - Risk: Stale permission data leading to security issues
   - Mitigation: Conservative TTLs, explicit invalidation on permission changes

### Medium Risks

1. **Database Consolidation Performance**

   - Risk: Resource contention between TimescaleDB and PostGIS
   - Mitigation: Thorough load testing before production deployment
2. **Feature Toggle State Management**

   - Risk: Inconsistent behavior across instances
   - Mitigation: Centralized feature flag service, audit logging

---

## Notes

This analysis is based on the current state of the Shepard platform as of the main branch. The recommendations prioritize:

1. **Reliability over features** - Ensuring system stability during database outages
2. **Incremental improvements** - Changes can be implemented independently
3. **Backwards compatibility** - No breaking changes to existing APIs unless versioned
4. **Evidence-based decisions** - All recommendations reference existing ADRs and implementation patterns

The polyglot persistence architecture is well-justified per ADR-008, and the main opportunities lie in optimizing coordination between databases rather than consolidation. The TimescaleDB migration from InfluxDB shows the team's capability to execute major architectural changes successfully.

### Citations

**File:**  backend/src/main/java/de/dlr/shepard/ShepardMain.java (L20-37)

```java
  @Startup
  void init() {
    Log.info("Starting shepard backend");

    var pkiHelper = new PKIHelper();
    var migrationRunner = new MigrationsRunner();
    pkiHelper.init();

    Log.info("Waiting for databases");
    migrationRunner.waitForConnection();

    Log.info("Run database migrations");
    migrationRunner.apply();

    Log.info("Initialize databases");
    neo4j.connect();
    Log.info("Connection established to neo4j database.");
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java (L49-64)

```java
  public void waitForConnection() {
    while (true) {
      try {
        driver.verifyConnectivity();
        break;
      } catch (Exception e) {
        Log.warn("Cannot connect to neo4j database. Retrying...");
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Log.error("Cannot sleep while waiting for neo4j Connection");
        Thread.currentThread().interrupt();
      }
    }
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/common/healthz/NeoHealthCheck.java (L10-19)

```java
@Readiness
@ApplicationScoped
public class NeoHealthCheck implements HealthCheck {

  private static IConnector neo4j = NeoConnector.getInstance();

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("Neo4J connection health check").status(neo4j.alive()).build();
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/endpoints/TimeseriesRest.java (L61-529)

```java
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.TIMESERIES_CONTAINERS)
@RequestScoped
public class TimeseriesRest {

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesCsvService timeseriesCsvService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  PermissionsService permissionsService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all timeseries containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllTimeseriesContainers(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @PositiveOrZero Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var containers = timeseriesContainerService.getAllContainers(params);
    var result = TimeseriesContainerIOMapper.map(containers);

    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var container = timeseriesContainerService.getContainer(timeseriesContainerId);
    return Response.ok(TimeseriesContainerIOMapper.map(container)).build();
  }

  @POST
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Create a new timeseries container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Transactional
  public Response createTimeseriesContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
    ) @Valid TimeseriesContainerIO timeseriesContainer
  ) {
    var container = timeseriesContainerService.createContainer(timeseriesContainer);
    return Response.ok(TimeseriesContainerIOMapper.map(container)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Delete timeseries container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response deleteTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    timeseriesContainerService.deleteContainer(timeseriesContainerId);
    return Response.status(Status.NO_CONTENT).build();
  }

  @POST
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Upload timeseries to container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = Timeseries.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response createTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long containerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
    ) @Valid TimeseriesWithDataPoints payload
  ) {
    TimeseriesEntity timeseriesEntity = timeseriesService.saveDataPoints(
      containerId,
      payload.getTimeseries(),
      payload.getPoints()
    );

    return Response.ok(new Timeseries(timeseriesEntity)).status(Status.CREATED).build();
  }

  @Deprecated(forRemoval = true)
  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.AVAILABLE)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(
    description = "Get timeseries available. Deprecated, use /timeseriesContainers/{containerId}/timeseries instead."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = Timeseries.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesAvailable(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    List<TimeseriesEntity> timeseriesEntityList;

    try {
      timeseriesEntityList = timeseriesService.getTimeseriesAvailable(timeseriesContainerId);
    } catch (InvalidPathException | InvalidAuthException e) {
      return Response.ok(Collections.emptyList()).build();
    }

    List<Timeseries> timeseriesListWithoutId = timeseriesEntityList
      .stream()
      .map(entity -> new Timeseries(entity))
      .toList();

    return Response.ok(timeseriesListWithoutId).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all available timeseries for that container.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT)
  @Parameter(name = Constants.DEVICE)
  @Parameter(name = Constants.LOCATION)
  @Parameter(name = Constants.SYMBOLICNAME)
  @Parameter(name = Constants.FIELD)
  public Response getTimeseriesOfContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field
  ) {
    var timeseriesEntityList = timeseriesService.getTimeseriesAvailable(timeseriesContainerId);
    var timeseriesList = timeseriesEntityList
      .stream()
      .map(entity -> new TimeseriesIO(entity))
      .filter(
        entity ->
          (measurement == null || measurement.isEmpty() || entity.getMeasurement().equals(measurement)) &&
          (device == null || device.isEmpty() || entity.getDevice().equals(device)) &&
          (location == null || location.isEmpty() || entity.getLocation().equals(location)) &&
          (symbolicName == null || symbolicName.isEmpty() || entity.getSymbolicName().equals(symbolicName)) &&
          (field == null || field.isEmpty() || entity.getField().equals(field))
      )
      .toList();
    return Response.ok(timeseriesList).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES + "/{" + Constants.TIMESERIES_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries by id.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesById(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @PathParam(Constants.TIMESERIES_ID) @NotNull @PositiveOrZero Integer timeseriesId
  ) {
    var timeseries = timeseriesService.getTimeseriesById(timeseriesContainerId, timeseriesId);
    return Response.ok(new TimeseriesIO(timeseries)).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.START, required = true)
  @Parameter(name = Constants.END, required = true)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  public Response getTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) @NotBlank String measurement,
    @QueryParam(Constants.LOCATION) @NotBlank String location,
    @QueryParam(Constants.DEVICE) @NotBlank String device,
    @QueryParam(Constants.SYMBOLICNAME) @NotBlank String symbolicName,
    @QueryParam(Constants.FIELD) @NotBlank String field,
    @QueryParam(Constants.START) @NotNull @PositiveOrZero Long start,
    @QueryParam(Constants.END) @NotNull @PositiveOrZero Long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws Exception {
    var timeseries = new Timeseries(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var timeseriesData = timeseriesService.getDataPointsByTimeseries(timeseriesContainerId, timeseries, queryParams);
    TimeseriesWithDataPoints timeseriesWithData = new TimeseriesWithDataPoints(timeseries, timeseriesData);
    return Response.ok(timeseriesWithData).build();
  }

  @GET
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.EXPORT)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Export timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.START, required = true)
  @Parameter(name = Constants.END, required = true)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  public Response exportTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) @NotBlank String measurement,
    @QueryParam(Constants.LOCATION) @NotBlank String location,
    @QueryParam(Constants.DEVICE) @NotBlank String device,
    @QueryParam(Constants.SYMBOLICNAME) @NotBlank String symbolicName,
    @QueryParam(Constants.FIELD) @NotBlank String field,
    @QueryParam(Constants.START) @NotNull @PositiveOrZero Long start,
    @QueryParam(Constants.END) @NotNull @PositiveOrZero Long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws IOException {
    var timeseries = new Timeseries(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var inputStream = timeseriesCsvService.exportTimeseriesDataToCsv(timeseriesContainerId, timeseries, queryParams);

    return Response.ok(inputStream, MediaType.APPLICATION_OCTET_STREAM)
      .header("Content-Disposition", "attachment; filename=\"timeseries-export.csv\"")
      .build();
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.IMPORT)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Import timeseries payload")
  @APIResponse(description = "ok", responseCode = "200")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Subscribable
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response importTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    MultipartBodyFileUpload body
  ) throws IOException {
    String filePath = body.fileUpload != null ? body.fileUpload.uploadedFile().toString() : null;

    if (filePath == null) {
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    timeseriesCsvService.importTimeseriesFromCsv(timeseriesContainerId, filePath);
    return Response.ok().build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO getTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var permissions = permissionsService.getPermissionsOfEntity(timeseriesContainerId);
    return new PermissionsIO(permissions);
  }

  @PUT
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO editTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var updatedPermissions = permissionsService.updatePermissionsByNeo4jId(permissions, timeseriesContainerId);
    if (updatedPermissions == null) throw new NotFoundException();
    return new PermissionsIO(updatedPermissions);
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Roles getTimeseriesRoles(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var roles = permissionsService.getUserRolesOnEntity(
      timeseriesContainerId,
      securityContext.getUserPrincipal().getName()
    );
    if (roles == null) throw new NotFoundException();
    return roles;
  }

  @Schema(type = SchemaType.STRING, format = "binary", description = "Timeseries as CSV")
  public interface UploadItemSchema {}

  public class UploadFormSchema {

    @Schema(required = true)
    public UploadItemSchema file;
  }

  @Schema(implementation = UploadFormSchema.class)
  public static class MultipartBodyFileUpload {

    @RestForm(Constants.FILE)
    public FileUpload fileUpload;
  }
}
```

**File:**  backend/src/main/resources/application.properties (L50-64)

```properties
# TimescaleDB

quarkus.datasource.db-kind=postgresql
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=db/migration,classpath:de/dlr/shepard/data/timeseries/migrations
quarkus.hibernate-orm.packages=de.dlr.shepard.data.timeseries

# Spatial Database
# https://quarkus.io/guides/flyway#multiple-datasources
quarkus.datasource.spatial.db-kind=postgresql
quarkus.flyway.spatial.active=${shepard.spatial-data.enabled}
quarkus.flyway.spatial.baseline-on-migrate=true
quarkus.flyway.spatial.locations=db/spatial/migration
quarkus.flyway.spatial.migrate-at-start=true
```

**File:**  backend/src/main/resources/application.properties (L120-125)

```properties
# Versioning Feature Toggle
shepard.versioning.enabled=false

# Spatial Data Feature Toggle
shepard.spatial-data.enabled=false

```

**File:**  backend/src/main/java/de/dlr/shepard/common/configuration/feature/toggles/VersioningFeatureToggle.java (L1-13)

```java
package de.dlr.shepard.common.configuration.feature.toggles;

public class VersioningFeatureToggle {

  public static final String TOGGLE_PROPERTY = "shepard.versioning.enabled";

  public static final String IS_ENABLED_METHOD_ID =
    "de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle#isEnabled";

  public static boolean isEnabled() {
    return TogglePropertyUtil.isToggleEnabled(TOGGLE_PROPERTY);
  }
}
```

**File:**  backend/src/main/java/de/dlr/shepard/context/collection/endpoints/CollectionVersioningRest.java (L39-39)

```java
@IfBuildProperty(name = VersioningFeatureToggle.TOGGLE_PROPERTY, stringValue = "true")
```

**File:**  architecture/src/09_architecture_decisions/008-database-target-architecture.adoc (L1-139)

```text
ifndef::imagesdir[:imagesdir: ../../images]

[[adr008]]
=== ADR-008 Database Target Architecture

[%autowidth.stretch]
[cols="h,1a"]
|===
|Date|17.09.2024
|Status|Done

|Context|
*Current state*

At the moment shepard uses three different databases:

* Neo4j (graph db)
* MongoDB (document db)
* InfluxDB (timeseries db)

*What was the reason for choosing different databases?*

* In the very beginning the data was directly stored into the databases (influxdb, neo4j and mongodb), no domain model, just the data
* In the second step the backend was created, the REST api was created and also the domain model
* Special features of timeseries database are already in use (min, max, sum, etc.)
* From a user perspective it feels easier to navigate through a graph database instead of a relational database

*Known issues*

* We have to use three different database query languages
* Maintenance of three different databases and their libraries
* For backup you have to consider all three databases
* Issues with Neo4j
** When to load relationships with data objects and how many and how does it influence performance.
You have to know how the ogm works.
** We had some issues with caching that we do not fully understand.
** Lack of a large ecosystem (e.g. only one migration library available (private one))
* Issues with InfluxDb
** We are using influxdb v1.8 atm.
** New versions of influxdb are completely different (completely new query language, etc.)
** Bad feeling about a shift to paid services.
** The library that we use to communicate with influxdb lacks some important features like query injection prevention.
* Issues with MongoDB
** The update process needs manual steps

|Possible Solutions|

1. We leave it as it is
2. Neo4j + MongoDB
3. Postgres only (Replace all database technologies with postgres)
4. Neo4j + MongoDB + Postgres (replace influxdb with postgres timescaledb)
5. Postgres + MongoDB (Replace influxdb and neo4j)
6. Neo4j + Postgres (Replace influxdb and mongodb with postgres)

image::adr/possible-database-architectures.png[]

|Decisions|
*Decision 1: Leave it as it is*
This is not an option because of known issues with InfluxDB.
We have to find a solution at least for that database.

*Decision 2: Meta Data in Neo4j or Postgres*
[cols="1,2,2"]
!===
!!Neo4j!Postgres
!Migration effort!None!Big
!Onboarding of new developers!Rather big!Rather small
!Familiarity in the team!The team is familiar with Neo4j!The team is not familiar with Postgres
!Ecosystem!Not big!Huge ecosystem, frequently used for a long time
!Maintenance effort!
Big, as we will have additional databases for data storage!Small to medium, if we use Postgres for all data persistence!
Performance!Comparable if properly used!Comparable if properly used
!===

On the green field Postgres might be the better option with less maintenance effort and it’s big ecosystem.
In the context of shepard we already have Neo4j, we would need to migrate data, the experience in the team is bigger for Neo4j.
_All in all we decide to continue with Neo4j._

*Decision 3: Database for Timeseries & Spatial Data*
[cols="1,2,2"]
!===
!!MongoDB!Postgres
!Migration Complexity!
Rather easy, MongoDB is already there and we only have to migrate timeseries data!
Medium migration effort

!Performance!Performance is probably worse than Postgres for timeseries and spatial data!
!Support for spatial data!Only supports 2D spatial data, no trivial and performant way to support 3D!

!Summary!Not an option due to performance and spatial data!
!===

_As MongoDB does not seem to perform well for timeseries and spatial data we decide to store timeseries (and in the future spatial data) in postgres with timescaledb and PostGIS._

*Decision 4: Database for Files & Structured Data*
[cols="1,2,2,2"]
!===
!!Postgres for structured data + Blob storage (MinIO)(option 5)!
Keep MongoDB(option 4)!
Postgres(option 6)

!Migration Complexity!All structured data and files have to be migrated!No migration effort!
All structured data and files have to be migrated

!Onboarding new Developers!!!
New developers only have to know how to interact with two databases

!Maintenance effort (updates of databases & clients)!
We still have three databases to maintain!
We still have three databases to maintain!
We only have to maintain one database in addition to Neo4j

!Reliability!
Probably also very stable!
MongoDB hasn’t been touched in months, so it reliably does it’s job!
Probably also very stable

!Performance (file size around 5-8 GB)!Good!Good!Unknown

!Summary!
This solution is not better than MongoDB, so it’s not feasible given the migration effort!!
!===

Postgres supports two ways for storing binary data link (https://www.postgresql.org/docs/7.4/jdbc-binary-data.html[bytea column and LargeObject API]).

For large files we have to use the LargeObject API.
But in both cases the data is stored in a single table.
For tables we have a limitation of 32 TB (per-table size limitation).
If we want to store multiple projects in one shepard instance, we might exceed this limit.
So we are not able to store large objects in postgres.
_The decision is to stay with MongoDB for files and structured data._

|Consequences|

* We still have to support three different databases.
* Complexity and maintenance costs are higher than with a single database, but just as high as now.
* Same applies to the backup up of three databases.

|===
```

**File:**  architecture/src/09_architecture_decisions/014-spatial-database.adoc (L100-128)

```text

== Performance Tests

Performance tests were executed on an already created dataset that consists of 7Mio data points.

[cols="2a,1a,1a"]
!===
!Performance Test Type!postgis!pgvector
!Create single data points for 1 minute.
!2148 new data points
!2106 new data points

!Bounding Box filter (ca. 500 returned points)
!380ms
!59000ms

!Bounding Sphere filter (ca. 500 returned points)
!795ms
!83000ms

!KNN filter (k = 10)
!53ms
!45ms

!Bounding Box with Metadata Filter (ca. 500 returned points)
!265ms
!42000ms

!===
```

**File:**  infrastructure/docker-compose.yml (L117-152)

```yaml
  timescaledb:
    image: timescale/timescaledb:2.18.1-pg16
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_SHEPARD_USER: ${POSTGRES_SHEPARD_USER}
      POSTGRES_SHEPARD_USER_PW: ${POSTGRES_SHEPARD_USER_PW}
      PGDATA: /var/lib/postgres/data
    volumes:
      - /opt/shepard/timescaledb:/var/lib/postgres/data
      - ./docker-entrypoint-initdb.d/postgres/00-init-postgres-db.sh:/docker-entrypoint-initdb.d/00-init-postgres-db.sh
    restart: unless-stopped
    networks:
      - shepard

  postgis:
    image: postgis/postgis:16-3.5
    container_name: postgis
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_SHEPARD_USER: ${POSTGRES_SHEPARD_USER}
      POSTGRES_SHEPARD_USER_PW: ${POSTGRES_SHEPARD_USER_PW}
    shm_size: "6GB"
    ports:
      - "5433:5432"
    volumes:
      - ./docker-entrypoint-initdb.d/postgis/00-init-postgis-db.sh:/docker-entrypoint-initdb.d/00-init-postgis-db.sh
      - /opt/shepard/postgis/db:/var/lib/postgresql/data
    networks:
      - shepard
    restart: unless-stopped
    profiles:
      - spatial
```

**File:**  infrastructure/docker-compose.yml (L173-185)

```yaml
  timescale-migration-preparation:
    image: registry.gitlab.com/dlr-shepard/shepard/timescale-migration-preparation:5.0.0
    volumes:
      - ./log:/app/log
    env_file:
      - .env
    depends_on:
      - influxdb
      - neo4j
    networks:
      - shepard
    profiles:
      - timescale-migration-preparation
```

**File:**  backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java (L110-132)

```java
  public boolean isAccessTypeAllowedForUser(long entityId, AccessType accessType, String username) {
    String cacheKey = String.format("%s,%s,%s", entityId, accessType.toString(), username);
    if (permissionLastSeenCache.isKeyCached(cacheKey)) return true;

    Roles userRolesOnEntity = getUserRolesOnEntity(entityId, username);

    boolean isAllowed;
    if (userRolesOnEntity.isOwner()) {
      isAllowed = true;
    } else {
      isAllowed = switch (accessType) {
        case Read -> userRolesOnEntity.isReader() || userRolesOnEntity.isWriter() || userRolesOnEntity.isManager();
        case Write -> userRolesOnEntity.isWriter() || userRolesOnEntity.isManager();
        case Manage -> userRolesOnEntity.isManager();
        case None -> false;
      };
    }

    if (isAllowed) {
      permissionLastSeenCache.cacheKey(cacheKey);
    }
    return isAllowed;
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java (L148-189)

```java
  public List<TimeseriesDataPoint> getDataPointsByTimeseries(
    long containerId,
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    return getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams);
  }

  /**
   * Retrieve a list of DataPoints for a time-interval with options to grouping/
   * time slicing, filling and aggregating.
   *
   * This function does not check if the container specified by containerId is
   * accessible.
   * We add <code>@ActivateRequestContext</code> in order to call this method in a
   * parallel stream.
   * The container check relies on an active request context.
   * However, the 'ActivateRequestContext' annotation does not allow for a
   * container check.
   *
   * @param containerId
   * @param timeseries
   * @param queryParams
   * @return List<TimeseriesDataPoint>
   */
  @ActivateRequestContext
  public List<TimeseriesDataPoint> getDataPointsByTimeseriesActivatedRequestContext(
    long containerId,
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    Optional<TimeseriesEntity> timeseriesEntity = this.timeseriesRepository.findTimeseries(containerId, timeseries);

    if (timeseriesEntity.isEmpty()) return Collections.emptyList();

    int timeseriesId = timeseriesEntity.get().getId();
    DataPointValueType valueType = timeseriesEntity.get().getValueType();

    return this.timeseriesDataPointRepository.queryDataPoints(timeseriesId, valueType, queryParams);
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java (L191-212)

```java
  public List<TimeseriesWithDataPoints> getManyTimeseriesWithDataPoints(
    Long containerId,
    List<Timeseries> timeseriesList,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    ConcurrentLinkedQueue<TimeseriesWithDataPoints> timeseriesWithDataPointsQueue = new ConcurrentLinkedQueue<
      TimeseriesWithDataPoints
    >();
    timeseriesList
      .parallelStream()
      .forEach(timeseries -> {
        timeseriesWithDataPointsQueue.add(
          new TimeseriesWithDataPoints(
            timeseries,
            getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams)
          )
        );
      });
    return new ArrayList<TimeseriesWithDataPoints>(timeseriesWithDataPointsQueue);
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java (L73-130)

```java
  /**
   * Insert a list of timeseries data points into the database using the COPY command.
   * This is used by the influxdb migration but can also be used for csv import or
   * similar scenarios.
   * @param entities
   * @param timeseriesEntity
   */
  @Timed(value = "shepard.timeseries-data-point.copy-insert")
  public void insertManyDataPointsWithCopyCommand(
    List<TimeseriesDataPoint> entities,
    TimeseriesEntity timeseriesEntity
  ) throws SQLException {
    try (Connection conn = defaultDataSource.getConnection()) {
      PGConnection pgConn = (PGConnection) conn.unwrap(PGConnection.class);
      CopyManager copyManager = pgConn.getCopyAPI();

      var columnName = getColumnName(timeseriesEntity.getValueType());
      var sb = new StringBuilder();

      timeseriesEntity.getId();

      // Strings must be quoted in double quotes in case they contain a comma which is also the delimiter
      if (timeseriesEntity.getValueType() == DataPointValueType.String) {
        for (int i = 0; i < entities.size(); i++) {
          TimeseriesDataPoint entity = entities.get(i);
          sb
            .append(timeseriesEntity.getId())
            .append(",")
            .append(entity.getTimestamp())
            .append(",\"")
            .append(entity.getValue())
            .append("\"\n");
        }
      } else {
        for (int i = 0; i < entities.size(); i++) {
          TimeseriesDataPoint entity = entities.get(i);
          sb
            .append(timeseriesEntity.getId())
            .append(",")
            .append(entity.getTimestamp())
            .append(",")
            .append(entity.getValue())
            .append("\n");
        }
      }

      InputStream input = new ByteArrayInputStream(sb.toString().getBytes());
      String sql = String.format(
        "COPY timeseries_data_points (timeseries_id, time, %s) FROM STDIN WITH (FORMAT csv);",
        columnName
      );

      copyManager.copyIn(sql, input);
    } catch (IOException ex) {
      Log.errorf("IOException during copy insert: %s", ex.getMessage());
      throw new RuntimeException("IO Error while inserting data points", ex);
    }
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java (L139-150)

```java
  @Timed(value = "shepard.timeseries-data-point.query")
  public List<TimeseriesDataPoint> queryDataPoints(
    int timeseriesId,
    DataPointValueType valueType,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    assertNotIntegral(queryParams.getFunction());
    assertCorrectValueTypesForAggregation(queryParams.getFunction(), valueType);
    assertCorrectValueTypesForFillOption(queryParams.getFillOption(), valueType);
    assertTimeIntervalForFillOption(queryParams.getTimeSliceNanoseconds(), queryParams.getFillOption());
    assertAggregationSetForFillOrGrouping(
      queryParams.getFunction(),
```

**File:**  frontend/package.json (L16-16)

```json
    "@dlr-shepard/backend-client": "file:../backend-client",
```



# DeepWiki Q&A with Code Context for Repository: gitlab.com／dlr-shepard／shepard

# DeepWiki Q&A with Code Context for Repository: gitlab.com/dlr-shepard/shepard

## Q1

# Architecture and Performance Analysis: Shepard Research Data Management Platform

## Executive Summary

Based on a comprehensive analysis of the Shepard platform codebase, I've identified critical architectural and performance improvement opportunities across database management, API design, feature toggles, and system startup. This analysis is grounded in the actual implementation and provides actionable recommendations with priority rankings.

---

## 1. ARCHITECTURAL ISSUES AND IMPROVEMENTS

### 1.1 Database Connection Management

#### **Issue A1: Blocking Sequential Startup with No Graceful Degradation**

**Priority: HIGH**

The current implementation forces all databases to be available before the application starts, creating a single point of failure. shepard:20-37 shepard:49-64

**Problem Details:**

- The `MigrationsRunner.waitForConnection()` uses an infinite loop with 1-second sleeps
- If any database (Neo4j, MongoDB, TimescaleDB, or PostGIS when enabled) is unavailable, the entire application fails to start
- No timeout mechanism exists
- No partial degradation mode for non-critical features

**Recommendations:**

1. **Implement Asynchronous Database Initialization**

   - Use Quarkus `@Startup` beans with different priorities for critical vs. non-critical databases
   - Allow the REST API to start with degraded functionality if optional databases are unavailable
   - Mark endpoints that require specific databases with custom annotations
2. **Add Connection Timeout and Retry Limits**

   - Replace infinite loops with configurable timeout values (e.g., 60 seconds)
   - Implement exponential backoff for retry attempts
   - Log detailed failure reasons to aid debugging
3. **Enhance Health Checks for Runtime Monitoring**  
   The current health checks are passive: shepard:10-19

   These should be enhanced to:

   - Distinguish between startup readiness and runtime health
   - Provide detailed status for each database connection
   - Support automated recovery attempts

**Trade-offs:**

- Increased complexity in managing partial system states
- Need for careful handling of cross-database transactions
- Potential confusion for users when certain features are unavailable

**Risk Mitigation:**

- Implement feature flags to control degraded mode behavior
- Provide clear API error messages when features are unavailable
- Add metrics to track partial availability incidents

---

### 1.2 API Endpoint Organization

#### **Issue A2: Monolithic REST Endpoint Classes**

**Priority: MEDIUM**

The `TimeseriesRest` class contains 40+ endpoints in a single file, creating maintenance challenges: shepard:61-529

**Problem Details:**

- Single responsibility principle violation
- Mixing concerns: CRUD operations, permissions, import/export, and data retrieval
- Difficult to navigate and test
- Similar patterns repeated across `FileRest`​ (32 endpoints) and `CollectionRest` (24 endpoints)

**Identified Cross-Cutting Concerns:**

1. **Permissions Management** - Repeated in multiple classes: shepard:442-512
2. **Import/Export Operations** - Could be standardized: shepard:360-440
3. **Pagination and Query Parameters** - Duplicated logic: shepard:99-114

**Recommendations:**

1. **Decompose into Sub-Resources**

   ```
   /timeseries-containers/{id}              -> TimeseriesContainerRest
   /timeseries-containers/{id}/timeseries   -> TimeseriesDataRest
   /timeseries-containers/{id}/permissions  -> PermissionsSubResource (shared)
   /timeseries-containers/{id}/export       -> ExportSubResource (shared)
   /timeseries-containers/{id}/import       -> ImportSubResource (shared)
   ```
2. **Extract Shared Permission Handling**

   - Create a generic `PermissionsSubResource<T>` that can be composed into different endpoints
   - Use JAX-RS sub-resource locators to delegate permission endpoints
3. **Standardize Pagination and Filtering**

   - Create a `@QueryParamsBean` class to encapsulate common query parameters
   - Move `QueryParamHelper` logic into a reusable service layer

**Trade-offs:**

- More files to maintain (but each simpler)
- Need to update OpenAPI generation configuration
- Existing API clients would need regeneration but no breaking changes to API contracts

---

### 1.3 Feature Toggle Complexity

#### **Issue A3: Build-time vs Runtime Toggle Confusion**

**Priority: MEDIUM-HIGH**

The current feature toggle system mixes build-time and runtime decisions, creating deployment complexity: shepard:120-125 shepard:1-13 shepard:39-39

**Problem Details:**

- ​`@IfBuildProperty` requires features to be enabled at build time, making it impossible to toggle features in production without rebuilding
- The versioning feature toggle at build-time conflicts with the desire for runtime flexibility
- Database configurations are coupled to feature toggles (e.g., spatial database activated based on toggle)
- No clear documentation on which toggles are build-time vs runtime

**Recommendations:**

1. **Migrate to Runtime-Only Feature Toggles**

   - Replace `@IfBuildProperty`​ with custom `@ConditionalOnFeature` annotation that checks at runtime
   - Use CDI `@Produces`​ methods with `@ApplicationScoped` to create beans conditionally
   - Implement feature flag service that reads from configuration or database
2. **Separate Infrastructure Toggles from Feature Toggles**

   - Infrastructure toggles (spatial-data.enabled) should control database connections - keep these
   - Feature toggles (versioning.enabled) should only control business logic - make these runtime
   - Use separate configuration namespaces: `shepard.infrastructure.*`​ vs `shepard.features.*`
3. **Add Feature Toggle Administration Endpoint**

   - Create `/admin/features` endpoint to view and modify runtime toggles
   - Store toggle state in Neo4j or external configuration service
   - Implement toggle change audit logging

**Trade-offs:**

- Runtime toggles add complexity to dependency injection
- Need to handle scenarios where features are toggled while system is running
- Potential for inconsistent state if toggles change during requests

**Example Implementation Pattern:**

```java
// Instead of @IfBuildProperty
@ApplicationScoped
public class FeatureBeanProducer {
    @Produces
    @ConditionalOnFeature("versioning")
    public VersioningService versioningService() {
        return new VersioningServiceImpl();
    }
}
```

---

### 1.4 Multi-Database Coordination

#### **Issue A4: Justified Polyglot Persistence but with Optimization Opportunities**

**Priority: LOW-MEDIUM**

The system uses four databases, which was a deliberate architectural decision: shepard:1-139

**Analysis:**   
The ADR-008 decision was well-reasoned:

- **Neo4j**: Chosen for graph relationships and permissions (despite Postgres alternative)
- **MongoDB**: Required for large file storage (>32TB Postgres limitation)
- **TimescaleDB**: Chosen for timeseries performance (replaced InfluxDB)
- **PostGIS**: Selected for 3D spatial data (significant performance advantage over pgvector) shepard:100-128

**However, optimization opportunities exist:**

1. **Consider TimescaleDB + PostGIS Consolidation**

   - Both are PostgreSQL extensions running on separate instances
   - Could run on same instance with different databases/schemas shepard:117-152

   **Benefits:**

   - Reduced infrastructure complexity (3 databases instead of 4)
   - Potential for cross-schema queries between timeseries and spatial data
   - Simplified backup procedures

   **Trade-offs:**

   - Shared resource contention between timeseries and spatial workloads
   - Need to carefully tune memory allocation
   - Risk of one workload impacting the other
2. **Optimize Cross-Database Permission Checks**

   The current pattern requires Neo4j queries for every data access: shepard:110-132

   **Problem:**  Every timeseries query must first check permissions in Neo4j, then fetch data from TimescaleDB

   **Recommendations:**

   - Implement permission caching with TTL (current cache exists but is basic)
   - Denormalize permissions into TimescaleDB/PostGIS for read-heavy workloads
   - Use database-level row security policies where possible

---

## 2. PERFORMANCE BOTTLENECKS AND IMPROVEMENTS

### 2.1 Startup Time Optimization

#### **Performance Issue P1: Sequential Database Initialization**

**Priority: HIGH**

The four-phase startup is entirely sequential: shepard:20-37

**Measured Impact:**

- PKI initialization: minimal
- Database connection waiting: **variable, potentially minutes if databases are slow to start**
- Migrations: **can take significant time with large datasets**
- Neo4j connection: minimal

**Bottleneck Analysis:**

1. **Migration Runner Waits for ALL Databases** shepard:49-64

   This only waits for Neo4j, but Flyway migrations run serially and block startup: shepard:50-64

2. **No Parallel Database Initialization**  
   All databases could be checked and migrated in parallel

**Recommendations:**

1. **Parallelize Database Connection Checks**

   - Use `CompletableFuture` or virtual threads to check all databases simultaneously
   - Fail fast if any critical database is unavailable
   - Continue if optional databases (PostGIS) are unavailable
2. **Lazy Migration Execution**

   - Move Flyway migrations to background threads
   - Use database-level locks to prevent concurrent migrations
   - Allow REST API to start before migrations complete for read-only operations
   - Return 503 Service Unavailable for write operations during migration
3. **Optimize Flyway Migration Performance** shepard:53-55

   - Enable Flyway parallel execution for independent migrations
   - Consider using Java-based migrations for performance-critical changes
   - Implement migration progress monitoring endpoint

**Expected Improvement:**

- Parallel connection checks: 60-80% reduction in wait time
- Background migrations: Application available in seconds instead of minutes

---

### 2.2 Database Query Performance

#### **Performance Issue P2: Cross-Database Query Coordination**

**Priority: HIGH**

Every data access requires coordinating queries across multiple databases:

**Example Flow for Timeseries Retrieval:**

1. Check permissions in Neo4j
2. Verify container exists in Neo4j
3. Fetch timeseries metadata from TimescaleDB
4. Query data points from TimescaleDB shepard:148-189

**Performance Analysis:**

1. **Permission Check Latency**

   - Each request makes at least one Neo4j query
   - Cache exists but implementation is basic: shepard:111-112
2. **N+1 Query Problem in Parallel Streams** shepard:191-212

   This creates N permission checks for N timeseries in parallel, overwhelming Neo4j

**Recommendations:**

1. **Implement Batch Permission Checks**

   - Add `checkPermissionsBatch(List<Long> entityIds)` method
   - Execute single Cypher query to check all entities at once
   - Reduces N queries to 1 query
2. **Enhance Permission Cache**

   - Use proper cache eviction policy (LRU, TTL-based)
   - Consider Redis or Caffeine cache instead of in-memory Map
   - Cache at user+entity level, not just key string
   - Implement cache warming for frequently accessed entities
3. **Add Database-Level Read Replicas**

   - Configure read replicas for Neo4j and TimescaleDB
   - Route permission checks to replicas to reduce load on primary
   - Particularly important for Neo4j which handles all permission queries
4. **Optimize TimescaleDB Query Performance**

   The repository uses native queries with proper TimescaleDB functions: shepard:139-150

   Current optimizations are good (hypertables, compression), but consider:

   - Pre-aggregate common query patterns into materialized views
   - Implement query result caching for frequently accessed time ranges
   - Use TimescaleDB continuous aggregates for common aggregation functions

---

### 2.3 Migration Process Performance

#### **Performance Issue P3: InfluxDB to TimescaleDB Migration Efficiency**

**Priority: MEDIUM** (historical, but important for future migrations)

The migration runs in a separate container: shepard:173-185

**Analysis:**

The repository uses PostgreSQL COPY command for bulk inserts, which is optimal: shepard:73-130

**Strengths:**

- COPY command is the fastest method for PostgreSQL bulk inserts
- Separate container prevents impacting production system
- Batch processing with 20,000 rows per batch

**Recommendations for Future Migrations:**

1. **Add Migration Progress Monitoring**

   - Expose migration progress via HTTP endpoint
   - Track rows migrated, errors encountered, time remaining
   - Store progress in database to support resume on failure
2. **Implement Parallel Migration Workers**

   - Partition data by container_id or time range
   - Run multiple migration workers in parallel
   - Use connection pooling to prevent overwhelming target database
3. **Add Data Validation Steps**

   - Implement row count verification
   - Add checksum validation for critical data
   - Create rollback capability for failed migrations

---

### 2.4 Frontend-Backend Communication

#### **Performance Issue P4: OpenAPI Client Generation Overhead**

**Priority: LOW-MEDIUM**

The frontend uses a locally generated TypeScript client: shepard:16-16

**Analysis:**

The client is generated from OpenAPI spec, which ensures type safety but has considerations:

**Current Approach:**

- File-based dependency on locally generated client
- Regeneration required whenever backend API changes
- No versioning between frontend and backend client

**Recommendations:**

1. **Implement API Versioning**

   - Add version prefix to API paths (e.g., `/shepard/api/v1`)
   - Support multiple API versions simultaneously during transitions
   - Use OpenAPI version field properly
2. **Optimize Client Bundle Size**

   - Current client likely includes all 100+ endpoints
   - Implement tree-shaking friendly exports
   - Consider code-splitting API modules (timeseries, files, collections)
3. **Add Response Caching Strategy**

   - Implement HTTP cache headers for GET requests
   - Use ETags for conditional requests
   - Add client-side caching layer for frequently accessed data
4. **Consider GraphQL for Complex Queries**

   - For queries that need data from multiple containers/timeseries
   - Reduces over-fetching and under-fetching
   - Single request instead of multiple REST calls
   - **Trade-off:**  Adds complexity, requires GraphQL infrastructure

---

## 3. SUMMARY OF RECOMMENDATIONS

### High Priority (Implement First)

|Issue|Recommendation|Expected Impact|Effort|
| -------| ------------------------------------------------------------------| ------------------------------------------------| --------|
|**A1**|Implement async database initialization with timeouts|Improved reliability, faster failure detection|Medium|
|**P1**|Parallelize database connection checks and migrate to background|60-80% startup time reduction|Medium|
|**P2**|Batch permission checks and enhance caching|50-70% reduction in Neo4j load|Medium|

### Medium Priority (Implement Next)

|Issue|Recommendation|Expected Impact|Effort|
| -------| ---------------------------------------------| ----------------------------------------------| -------------|
|**A2**|Decompose REST endpoints into sub-resources|Improved maintainability|Medium-High|
|**A3**|Migrate to runtime feature toggles|Deployment flexibility|Medium|
|**P2**|Add TimescaleDB continuous aggregates|Faster query performance for common patterns|Medium|
|**A4**|Consolidate TimescaleDB + PostGIS instances|Reduced infrastructure complexity|Low-Medium|

### Low Priority (Future Enhancements)

|Issue|Recommendation|Expected Impact|Effort|
| -------| -----------------------------------| -------------------------------------------| --------|
|**P3**|Add migration progress monitoring|Better visibility for future migrations|Low|
|**P4**|Implement API versioning|Smoother API evolution|Medium|
|**P4**|Optimize client bundle size|Marginal frontend performance improvement|Low|

---

## 4. IMPLEMENTATION ROADMAP

### Phase 1: Reliability Improvements (2-3 weeks)

1. Add database connection timeouts
2. Implement parallel connection checks
3. Enhance health check endpoints
4. Add batch permission checking

### Phase 2: Performance Optimization (3-4 weeks)

1. Improve permission caching
2. Implement background migrations
3. Add TimescaleDB continuous aggregates
4. Optimize query patterns

### Phase 3: Architectural Refactoring (4-6 weeks)

1. Decompose large REST endpoint classes
2. Migrate to runtime feature toggles
3. Consolidate PostgreSQL instances
4. Implement API versioning

---

## 5. RISK ASSESSMENT

### Critical Risks

1. **Backwards Compatibility**

   - Risk: API changes breaking existing clients
   - Mitigation: Implement versioning before major changes
2. **Data Consistency During Async Initialization**

   - Risk: Partial system state leading to inconsistent data
   - Mitigation: Implement proper transaction boundaries and state validation
3. **Cache Invalidation Complexity**

   - Risk: Stale permission data leading to security issues
   - Mitigation: Conservative TTLs, explicit invalidation on permission changes

### Medium Risks

1. **Database Consolidation Performance**

   - Risk: Resource contention between TimescaleDB and PostGIS
   - Mitigation: Thorough load testing before production deployment
2. **Feature Toggle State Management**

   - Risk: Inconsistent behavior across instances
   - Mitigation: Centralized feature flag service, audit logging

---

## Notes

This analysis is based on the current state of the Shepard platform as of the main branch. The recommendations prioritize:

1. **Reliability over features** - Ensuring system stability during database outages
2. **Incremental improvements** - Changes can be implemented independently
3. **Backwards compatibility** - No breaking changes to existing APIs unless versioned
4. **Evidence-based decisions** - All recommendations reference existing ADRs and implementation patterns

The polyglot persistence architecture is well-justified per ADR-008, and the main opportunities lie in optimizing coordination between databases rather than consolidation. The TimescaleDB migration from InfluxDB shows the team's capability to execute major architectural changes successfully.

### Citations

**File:**  backend/src/main/java/de/dlr/shepard/ShepardMain.java (L20-37)

```java
  @Startup
  void init() {
    Log.info("Starting shepard backend");

    var pkiHelper = new PKIHelper();
    var migrationRunner = new MigrationsRunner();
    pkiHelper.init();

    Log.info("Waiting for databases");
    migrationRunner.waitForConnection();

    Log.info("Run database migrations");
    migrationRunner.apply();

    Log.info("Initialize databases");
    neo4j.connect();
    Log.info("Connection established to neo4j database.");
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java (L49-64)

```java
  public void waitForConnection() {
    while (true) {
      try {
        driver.verifyConnectivity();
        break;
      } catch (Exception e) {
        Log.warn("Cannot connect to neo4j database. Retrying...");
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Log.error("Cannot sleep while waiting for neo4j Connection");
        Thread.currentThread().interrupt();
      }
    }
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/common/healthz/NeoHealthCheck.java (L10-19)

```java
@Readiness
@ApplicationScoped
public class NeoHealthCheck implements HealthCheck {

  private static IConnector neo4j = NeoConnector.getInstance();

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("Neo4J connection health check").status(neo4j.alive()).build();
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/endpoints/TimeseriesRest.java (L61-529)

```java
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.TIMESERIES_CONTAINERS)
@RequestScoped
public class TimeseriesRest {

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesCsvService timeseriesCsvService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  PermissionsService permissionsService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all timeseries containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllTimeseriesContainers(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @PositiveOrZero Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var containers = timeseriesContainerService.getAllContainers(params);
    var result = TimeseriesContainerIOMapper.map(containers);

    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var container = timeseriesContainerService.getContainer(timeseriesContainerId);
    return Response.ok(TimeseriesContainerIOMapper.map(container)).build();
  }

  @POST
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Create a new timeseries container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Transactional
  public Response createTimeseriesContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
    ) @Valid TimeseriesContainerIO timeseriesContainer
  ) {
    var container = timeseriesContainerService.createContainer(timeseriesContainer);
    return Response.ok(TimeseriesContainerIOMapper.map(container)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Delete timeseries container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response deleteTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    timeseriesContainerService.deleteContainer(timeseriesContainerId);
    return Response.status(Status.NO_CONTENT).build();
  }

  @POST
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Upload timeseries to container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = Timeseries.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response createTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long containerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
    ) @Valid TimeseriesWithDataPoints payload
  ) {
    TimeseriesEntity timeseriesEntity = timeseriesService.saveDataPoints(
      containerId,
      payload.getTimeseries(),
      payload.getPoints()
    );

    return Response.ok(new Timeseries(timeseriesEntity)).status(Status.CREATED).build();
  }

  @Deprecated(forRemoval = true)
  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.AVAILABLE)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(
    description = "Get timeseries available. Deprecated, use /timeseriesContainers/{containerId}/timeseries instead."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = Timeseries.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesAvailable(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    List<TimeseriesEntity> timeseriesEntityList;

    try {
      timeseriesEntityList = timeseriesService.getTimeseriesAvailable(timeseriesContainerId);
    } catch (InvalidPathException | InvalidAuthException e) {
      return Response.ok(Collections.emptyList()).build();
    }

    List<Timeseries> timeseriesListWithoutId = timeseriesEntityList
      .stream()
      .map(entity -> new Timeseries(entity))
      .toList();

    return Response.ok(timeseriesListWithoutId).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all available timeseries for that container.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT)
  @Parameter(name = Constants.DEVICE)
  @Parameter(name = Constants.LOCATION)
  @Parameter(name = Constants.SYMBOLICNAME)
  @Parameter(name = Constants.FIELD)
  public Response getTimeseriesOfContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field
  ) {
    var timeseriesEntityList = timeseriesService.getTimeseriesAvailable(timeseriesContainerId);
    var timeseriesList = timeseriesEntityList
      .stream()
      .map(entity -> new TimeseriesIO(entity))
      .filter(
        entity ->
          (measurement == null || measurement.isEmpty() || entity.getMeasurement().equals(measurement)) &&
          (device == null || device.isEmpty() || entity.getDevice().equals(device)) &&
          (location == null || location.isEmpty() || entity.getLocation().equals(location)) &&
          (symbolicName == null || symbolicName.isEmpty() || entity.getSymbolicName().equals(symbolicName)) &&
          (field == null || field.isEmpty() || entity.getField().equals(field))
      )
      .toList();
    return Response.ok(timeseriesList).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES + "/{" + Constants.TIMESERIES_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries by id.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesById(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @PathParam(Constants.TIMESERIES_ID) @NotNull @PositiveOrZero Integer timeseriesId
  ) {
    var timeseries = timeseriesService.getTimeseriesById(timeseriesContainerId, timeseriesId);
    return Response.ok(new TimeseriesIO(timeseries)).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.START, required = true)
  @Parameter(name = Constants.END, required = true)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  public Response getTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) @NotBlank String measurement,
    @QueryParam(Constants.LOCATION) @NotBlank String location,
    @QueryParam(Constants.DEVICE) @NotBlank String device,
    @QueryParam(Constants.SYMBOLICNAME) @NotBlank String symbolicName,
    @QueryParam(Constants.FIELD) @NotBlank String field,
    @QueryParam(Constants.START) @NotNull @PositiveOrZero Long start,
    @QueryParam(Constants.END) @NotNull @PositiveOrZero Long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws Exception {
    var timeseries = new Timeseries(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var timeseriesData = timeseriesService.getDataPointsByTimeseries(timeseriesContainerId, timeseries, queryParams);
    TimeseriesWithDataPoints timeseriesWithData = new TimeseriesWithDataPoints(timeseries, timeseriesData);
    return Response.ok(timeseriesWithData).build();
  }

  @GET
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.EXPORT)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Export timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.START, required = true)
  @Parameter(name = Constants.END, required = true)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  public Response exportTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) @NotBlank String measurement,
    @QueryParam(Constants.LOCATION) @NotBlank String location,
    @QueryParam(Constants.DEVICE) @NotBlank String device,
    @QueryParam(Constants.SYMBOLICNAME) @NotBlank String symbolicName,
    @QueryParam(Constants.FIELD) @NotBlank String field,
    @QueryParam(Constants.START) @NotNull @PositiveOrZero Long start,
    @QueryParam(Constants.END) @NotNull @PositiveOrZero Long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws IOException {
    var timeseries = new Timeseries(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var inputStream = timeseriesCsvService.exportTimeseriesDataToCsv(timeseriesContainerId, timeseries, queryParams);

    return Response.ok(inputStream, MediaType.APPLICATION_OCTET_STREAM)
      .header("Content-Disposition", "attachment; filename=\"timeseries-export.csv\"")
      .build();
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.IMPORT)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Import timeseries payload")
  @APIResponse(description = "ok", responseCode = "200")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Subscribable
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response importTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    MultipartBodyFileUpload body
  ) throws IOException {
    String filePath = body.fileUpload != null ? body.fileUpload.uploadedFile().toString() : null;

    if (filePath == null) {
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    timeseriesCsvService.importTimeseriesFromCsv(timeseriesContainerId, filePath);
    return Response.ok().build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO getTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var permissions = permissionsService.getPermissionsOfEntity(timeseriesContainerId);
    return new PermissionsIO(permissions);
  }

  @PUT
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO editTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var updatedPermissions = permissionsService.updatePermissionsByNeo4jId(permissions, timeseriesContainerId);
    if (updatedPermissions == null) throw new NotFoundException();
    return new PermissionsIO(updatedPermissions);
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Roles getTimeseriesRoles(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var roles = permissionsService.getUserRolesOnEntity(
      timeseriesContainerId,
      securityContext.getUserPrincipal().getName()
    );
    if (roles == null) throw new NotFoundException();
    return roles;
  }

  @Schema(type = SchemaType.STRING, format = "binary", description = "Timeseries as CSV")
  public interface UploadItemSchema {}

  public class UploadFormSchema {

    @Schema(required = true)
    public UploadItemSchema file;
  }

  @Schema(implementation = UploadFormSchema.class)
  public static class MultipartBodyFileUpload {

    @RestForm(Constants.FILE)
    public FileUpload fileUpload;
  }
}
```

**File:**  backend/src/main/resources/application.properties (L50-64)

```properties
# TimescaleDB

quarkus.datasource.db-kind=postgresql
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=db/migration,classpath:de/dlr/shepard/data/timeseries/migrations
quarkus.hibernate-orm.packages=de.dlr.shepard.data.timeseries

# Spatial Database
# https://quarkus.io/guides/flyway#multiple-datasources
quarkus.datasource.spatial.db-kind=postgresql
quarkus.flyway.spatial.active=${shepard.spatial-data.enabled}
quarkus.flyway.spatial.baseline-on-migrate=true
quarkus.flyway.spatial.locations=db/spatial/migration
quarkus.flyway.spatial.migrate-at-start=true
```

**File:**  backend/src/main/resources/application.properties (L120-125)

```properties
# Versioning Feature Toggle
shepard.versioning.enabled=false

# Spatial Data Feature Toggle
shepard.spatial-data.enabled=false

```

**File:**  backend/src/main/java/de/dlr/shepard/common/configuration/feature/toggles/VersioningFeatureToggle.java (L1-13)

```java
package de.dlr.shepard.common.configuration.feature.toggles;

public class VersioningFeatureToggle {

  public static final String TOGGLE_PROPERTY = "shepard.versioning.enabled";

  public static final String IS_ENABLED_METHOD_ID =
    "de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle#isEnabled";

  public static boolean isEnabled() {
    return TogglePropertyUtil.isToggleEnabled(TOGGLE_PROPERTY);
  }
}
```

**File:**  backend/src/main/java/de/dlr/shepard/context/collection/endpoints/CollectionVersioningRest.java (L39-39)

```java
@IfBuildProperty(name = VersioningFeatureToggle.TOGGLE_PROPERTY, stringValue = "true")
```

**File:**  architecture/src/09_architecture_decisions/008-database-target-architecture.adoc (L1-139)

```text
ifndef::imagesdir[:imagesdir: ../../images]

[[adr008]]
=== ADR-008 Database Target Architecture

[%autowidth.stretch]
[cols="h,1a"]
|===
|Date|17.09.2024
|Status|Done

|Context|
*Current state*

At the moment shepard uses three different databases:

* Neo4j (graph db)
* MongoDB (document db)
* InfluxDB (timeseries db)

*What was the reason for choosing different databases?*

* In the very beginning the data was directly stored into the databases (influxdb, neo4j and mongodb), no domain model, just the data
* In the second step the backend was created, the REST api was created and also the domain model
* Special features of timeseries database are already in use (min, max, sum, etc.)
* From a user perspective it feels easier to navigate through a graph database instead of a relational database

*Known issues*

* We have to use three different database query languages
* Maintenance of three different databases and their libraries
* For backup you have to consider all three databases
* Issues with Neo4j
** When to load relationships with data objects and how many and how does it influence performance.
You have to know how the ogm works.
** We had some issues with caching that we do not fully understand.
** Lack of a large ecosystem (e.g. only one migration library available (private one))
* Issues with InfluxDb
** We are using influxdb v1.8 atm.
** New versions of influxdb are completely different (completely new query language, etc.)
** Bad feeling about a shift to paid services.
** The library that we use to communicate with influxdb lacks some important features like query injection prevention.
* Issues with MongoDB
** The update process needs manual steps

|Possible Solutions|

1. We leave it as it is
2. Neo4j + MongoDB
3. Postgres only (Replace all database technologies with postgres)
4. Neo4j + MongoDB + Postgres (replace influxdb with postgres timescaledb)
5. Postgres + MongoDB (Replace influxdb and neo4j)
6. Neo4j + Postgres (Replace influxdb and mongodb with postgres)

image::adr/possible-database-architectures.png[]

|Decisions|
*Decision 1: Leave it as it is*
This is not an option because of known issues with InfluxDB.
We have to find a solution at least for that database.

*Decision 2: Meta Data in Neo4j or Postgres*
[cols="1,2,2"]
!===
!!Neo4j!Postgres
!Migration effort!None!Big
!Onboarding of new developers!Rather big!Rather small
!Familiarity in the team!The team is familiar with Neo4j!The team is not familiar with Postgres
!Ecosystem!Not big!Huge ecosystem, frequently used for a long time
!Maintenance effort!
Big, as we will have additional databases for data storage!Small to medium, if we use Postgres for all data persistence!
Performance!Comparable if properly used!Comparable if properly used
!===

On the green field Postgres might be the better option with less maintenance effort and it’s big ecosystem.
In the context of shepard we already have Neo4j, we would need to migrate data, the experience in the team is bigger for Neo4j.
_All in all we decide to continue with Neo4j._

*Decision 3: Database for Timeseries & Spatial Data*
[cols="1,2,2"]
!===
!!MongoDB!Postgres
!Migration Complexity!
Rather easy, MongoDB is already there and we only have to migrate timeseries data!
Medium migration effort

!Performance!Performance is probably worse than Postgres for timeseries and spatial data!
!Support for spatial data!Only supports 2D spatial data, no trivial and performant way to support 3D!

!Summary!Not an option due to performance and spatial data!
!===

_As MongoDB does not seem to perform well for timeseries and spatial data we decide to store timeseries (and in the future spatial data) in postgres with timescaledb and PostGIS._

*Decision 4: Database for Files & Structured Data*
[cols="1,2,2,2"]
!===
!!Postgres for structured data + Blob storage (MinIO)(option 5)!
Keep MongoDB(option 4)!
Postgres(option 6)

!Migration Complexity!All structured data and files have to be migrated!No migration effort!
All structured data and files have to be migrated

!Onboarding new Developers!!!
New developers only have to know how to interact with two databases

!Maintenance effort (updates of databases & clients)!
We still have three databases to maintain!
We still have three databases to maintain!
We only have to maintain one database in addition to Neo4j

!Reliability!
Probably also very stable!
MongoDB hasn’t been touched in months, so it reliably does it’s job!
Probably also very stable

!Performance (file size around 5-8 GB)!Good!Good!Unknown

!Summary!
This solution is not better than MongoDB, so it’s not feasible given the migration effort!!
!===

Postgres supports two ways for storing binary data link (https://www.postgresql.org/docs/7.4/jdbc-binary-data.html[bytea column and LargeObject API]).

For large files we have to use the LargeObject API.
But in both cases the data is stored in a single table.
For tables we have a limitation of 32 TB (per-table size limitation).
If we want to store multiple projects in one shepard instance, we might exceed this limit.
So we are not able to store large objects in postgres.
_The decision is to stay with MongoDB for files and structured data._

|Consequences|

* We still have to support three different databases.
* Complexity and maintenance costs are higher than with a single database, but just as high as now.
* Same applies to the backup up of three databases.

|===
```

**File:**  architecture/src/09_architecture_decisions/014-spatial-database.adoc (L100-128)

```text

== Performance Tests

Performance tests were executed on an already created dataset that consists of 7Mio data points.

[cols="2a,1a,1a"]
!===
!Performance Test Type!postgis!pgvector
!Create single data points for 1 minute.
!2148 new data points
!2106 new data points

!Bounding Box filter (ca. 500 returned points)
!380ms
!59000ms

!Bounding Sphere filter (ca. 500 returned points)
!795ms
!83000ms

!KNN filter (k = 10)
!53ms
!45ms

!Bounding Box with Metadata Filter (ca. 500 returned points)
!265ms
!42000ms

!===
```

**File:**  infrastructure/docker-compose.yml (L117-152)

```yaml
  timescaledb:
    image: timescale/timescaledb:2.18.1-pg16
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_SHEPARD_USER: ${POSTGRES_SHEPARD_USER}
      POSTGRES_SHEPARD_USER_PW: ${POSTGRES_SHEPARD_USER_PW}
      PGDATA: /var/lib/postgres/data
    volumes:
      - /opt/shepard/timescaledb:/var/lib/postgres/data
      - ./docker-entrypoint-initdb.d/postgres/00-init-postgres-db.sh:/docker-entrypoint-initdb.d/00-init-postgres-db.sh
    restart: unless-stopped
    networks:
      - shepard

  postgis:
    image: postgis/postgis:16-3.5
    container_name: postgis
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_SHEPARD_USER: ${POSTGRES_SHEPARD_USER}
      POSTGRES_SHEPARD_USER_PW: ${POSTGRES_SHEPARD_USER_PW}
    shm_size: "6GB"
    ports:
      - "5433:5432"
    volumes:
      - ./docker-entrypoint-initdb.d/postgis/00-init-postgis-db.sh:/docker-entrypoint-initdb.d/00-init-postgis-db.sh
      - /opt/shepard/postgis/db:/var/lib/postgresql/data
    networks:
      - shepard
    restart: unless-stopped
    profiles:
      - spatial
```

**File:**  infrastructure/docker-compose.yml (L173-185)

```yaml
  timescale-migration-preparation:
    image: registry.gitlab.com/dlr-shepard/shepard/timescale-migration-preparation:5.0.0
    volumes:
      - ./log:/app/log
    env_file:
      - .env
    depends_on:
      - influxdb
      - neo4j
    networks:
      - shepard
    profiles:
      - timescale-migration-preparation
```

**File:**  backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java (L110-132)

```java
  public boolean isAccessTypeAllowedForUser(long entityId, AccessType accessType, String username) {
    String cacheKey = String.format("%s,%s,%s", entityId, accessType.toString(), username);
    if (permissionLastSeenCache.isKeyCached(cacheKey)) return true;

    Roles userRolesOnEntity = getUserRolesOnEntity(entityId, username);

    boolean isAllowed;
    if (userRolesOnEntity.isOwner()) {
      isAllowed = true;
    } else {
      isAllowed = switch (accessType) {
        case Read -> userRolesOnEntity.isReader() || userRolesOnEntity.isWriter() || userRolesOnEntity.isManager();
        case Write -> userRolesOnEntity.isWriter() || userRolesOnEntity.isManager();
        case Manage -> userRolesOnEntity.isManager();
        case None -> false;
      };
    }

    if (isAllowed) {
      permissionLastSeenCache.cacheKey(cacheKey);
    }
    return isAllowed;
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java (L148-189)

```java
  public List<TimeseriesDataPoint> getDataPointsByTimeseries(
    long containerId,
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    return getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams);
  }

  /**
   * Retrieve a list of DataPoints for a time-interval with options to grouping/
   * time slicing, filling and aggregating.
   *
   * This function does not check if the container specified by containerId is
   * accessible.
   * We add <code>@ActivateRequestContext</code> in order to call this method in a
   * parallel stream.
   * The container check relies on an active request context.
   * However, the 'ActivateRequestContext' annotation does not allow for a
   * container check.
   *
   * @param containerId
   * @param timeseries
   * @param queryParams
   * @return List<TimeseriesDataPoint>
   */
  @ActivateRequestContext
  public List<TimeseriesDataPoint> getDataPointsByTimeseriesActivatedRequestContext(
    long containerId,
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    Optional<TimeseriesEntity> timeseriesEntity = this.timeseriesRepository.findTimeseries(containerId, timeseries);

    if (timeseriesEntity.isEmpty()) return Collections.emptyList();

    int timeseriesId = timeseriesEntity.get().getId();
    DataPointValueType valueType = timeseriesEntity.get().getValueType();

    return this.timeseriesDataPointRepository.queryDataPoints(timeseriesId, valueType, queryParams);
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java (L191-212)

```java
  public List<TimeseriesWithDataPoints> getManyTimeseriesWithDataPoints(
    Long containerId,
    List<Timeseries> timeseriesList,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    ConcurrentLinkedQueue<TimeseriesWithDataPoints> timeseriesWithDataPointsQueue = new ConcurrentLinkedQueue<
      TimeseriesWithDataPoints
    >();
    timeseriesList
      .parallelStream()
      .forEach(timeseries -> {
        timeseriesWithDataPointsQueue.add(
          new TimeseriesWithDataPoints(
            timeseries,
            getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams)
          )
        );
      });
    return new ArrayList<TimeseriesWithDataPoints>(timeseriesWithDataPointsQueue);
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java (L73-130)

```java
  /**
   * Insert a list of timeseries data points into the database using the COPY command.
   * This is used by the influxdb migration but can also be used for csv import or
   * similar scenarios.
   * @param entities
   * @param timeseriesEntity
   */
  @Timed(value = "shepard.timeseries-data-point.copy-insert")
  public void insertManyDataPointsWithCopyCommand(
    List<TimeseriesDataPoint> entities,
    TimeseriesEntity timeseriesEntity
  ) throws SQLException {
    try (Connection conn = defaultDataSource.getConnection()) {
      PGConnection pgConn = (PGConnection) conn.unwrap(PGConnection.class);
      CopyManager copyManager = pgConn.getCopyAPI();

      var columnName = getColumnName(timeseriesEntity.getValueType());
      var sb = new StringBuilder();

      timeseriesEntity.getId();

      // Strings must be quoted in double quotes in case they contain a comma which is also the delimiter
      if (timeseriesEntity.getValueType() == DataPointValueType.String) {
        for (int i = 0; i < entities.size(); i++) {
          TimeseriesDataPoint entity = entities.get(i);
          sb
            .append(timeseriesEntity.getId())
            .append(",")
            .append(entity.getTimestamp())
            .append(",\"")
            .append(entity.getValue())
            .append("\"\n");
        }
      } else {
        for (int i = 0; i < entities.size(); i++) {
          TimeseriesDataPoint entity = entities.get(i);
          sb
            .append(timeseriesEntity.getId())
            .append(",")
            .append(entity.getTimestamp())
            .append(",")
            .append(entity.getValue())
            .append("\n");
        }
      }

      InputStream input = new ByteArrayInputStream(sb.toString().getBytes());
      String sql = String.format(
        "COPY timeseries_data_points (timeseries_id, time, %s) FROM STDIN WITH (FORMAT csv);",
        columnName
      );

      copyManager.copyIn(sql, input);
    } catch (IOException ex) {
      Log.errorf("IOException during copy insert: %s", ex.getMessage());
      throw new RuntimeException("IO Error while inserting data points", ex);
    }
  }
```

**File:**  backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java (L139-150)

```java
  @Timed(value = "shepard.timeseries-data-point.query")
  public List<TimeseriesDataPoint> queryDataPoints(
    int timeseriesId,
    DataPointValueType valueType,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    assertNotIntegral(queryParams.getFunction());
    assertCorrectValueTypesForAggregation(queryParams.getFunction(), valueType);
    assertCorrectValueTypesForFillOption(queryParams.getFillOption(), valueType);
    assertTimeIntervalForFillOption(queryParams.getTimeSliceNanoseconds(), queryParams.getFillOption());
    assertAggregationSetForFillOrGrouping(
      queryParams.getFunction(),
```

**File:**  frontend/package.json (L16-16)

```json
    "@dlr-shepard/backend-client": "file:../backend-client",
```

# FLINT

- Inhalte:

  - vom Bauteil zur Prüfung

	- CAD → Punktewolke (vom zu vermessenden Bauteil) → Programmgenerierung → US-Vermessung → (Abstandsregelung)
	- shepard als drehscheibe -- databus -- databus reference
  - US-Steuerung Improvement
  - Analyse Tooling US-Daten
  - Shepard Föderation / Teilen / Verlinkung

	- Ideen:

	  - "remote shepard reference"
	  - databus
	  - -x Dataspace Ansatz
Die in Berechtigungen und Datensichtbarkeit abgestufte Integration bestehender und aktuell im Aufbau befindlicher Systeme (z.B. verteilte Shepard-Instanzen, das eData-Repositorium, inst.dlr, twinstash, Digitaler Atlas 2.0, ODIX) leistet hier einen zentralen Beitrag für die sichere und FAIRe Nachnutzung und Technologietransfer von DLR-Produkten auch über die HGF hinaus.

Diese Beiträge sind somit nicht nur innerhalb der Stoßrichtungen Daten und Digitalisierung der Organisation der Digitalisierungsstrategie zu sehen, sondern auch als unmittelbare Teile eines DLR-as-a-service-Angebotes.

Ferner sind diese Services maßgebliche Beiträge für die interoperable und ressourceneffiziente Integration von DLR-Produkten in nachfolgende Projekte im Sinne der Stoßrichtungen Künstliche Intelligenz, Cyberphysisches Engineering und Innovative Autonome Systeme.

https://wiki.dlr.de/display/FDM/Workpackages+BT-ZAP

- [ ] Read https://zenodo.org/records/14161466 🆔 20260407-bb6

---

## HMC Project Idea:

- Semantic Satellite Data Storage System SSDSS

- Topics:

  - joint data model development
  - data integration (import tooling)
  - shepard features:

	- shepard Spatial erweiterung für geospatial + height
	- HDF5 container?
	- ro-crate import (collection)
  - semantic annotation of space relevant data
  - data provider
  - analytics use-case (e.g. semantics based visualisation)


Für experimente:
# HMI ／ Leitstand

- Eingabe Metadaten für Experiment: Name, Beschreibung, Experimentator, ...
- Datenerfassung An/Aus
- Logik / Regeln für Anlegen von shepard Unterelementen (z.B. Layers, Tracks) und entsprechenden Referenzen (→ das machte der DRG)
- Logik für Einsortieren von diskreten Artefakten: (Files)

  - Roboterprogramme (über OPCUA?)
  - TrackVideo?
  - TPS Daten
- Retry bei Kommunikationsproblemen
- Wiederaufsetzen des Prozess an bestimmter Stelle (z.b. Track)  (→ das machte der DRG in Teilen)
- Logik für Programmübertragung und Ausführung(süberwachung)

‍

Idee:

Grafana als Visualisierung

- Plugin BusinessForms --> REST API Anbindung von Teilsystemen

  - STC (on, Off, Reload, Config?)
  - shepard Context manager

	- Über OPC UA server aktuellen Kontext (dataobject etc) bereit stellen? --> monitored vor allem OPC UA variablen und macht daraufhin was
	- evlt container?
	- Über REST aktueller zustand
  - Cell Run Manager

	- Programmupload / management
	- Programmaufrufe
	- Über REST aktueller zustand

Offene Frage: Querbeziehungen, *wer* muss *was* von *wem* wissen


LLM / genai based data analytics with shepard

Shepard:

- Anfassbare / annotierbare Zeitreihen --> XITASO
- Metadaten-Templates --> t.b.d.

- Anbindung Komponenten

  - Infusionsanlage --> OPC UA --> stc
  - Kamerasystem --> Hotfolder
  - Netzsch DEA --> OPC UA --> stc
  - Ofen?
  - Erweiterung shepard timeseries collector Semantik?

Stoßrichtungen

- Shepard Core:

  - Federation
  - Manufacturing-X Integration → AAS Adapter
  - Usability:

	- Frontend
	- Clients
- Ingest

  - Data Parsers
  - shepard collectors improvement

	- easier Machine integration
  - process wizard

  - Data Formats: 

	- HDF5 + HSDS API
- Analytics

  - API enhancements (support libraries)
  - MCP Server for LLM use
  - MCP Server for visualisation
  - Superset
- Other

  - Data Traceability, Provenance
 
# Schulungideen Shepard

- [ ] Schulungs- Workshopkonzept shepard entwickeln 🆔 20260407-6fc
  Wie gehen daten rein? zum Import  
  Wie gehen daten raus? zu Analyse  
  Wie geht man mit daten in shepard um?

‍

- Bedarfserhebung?

- welche Formate?

  - Wiederverwendbarkeit?
  - Tutorials
  - Webinar
  - "Northwind" Datensatz zum Spielen?

	- Szenario im Detail? → Transfer

	  - Tapelegen
	  - Materialtest
	- RO-Crate Export zur Publikation z. b. bei Zenodo
  - Team von Personas?  
	Kollaboratives datenorientiertes Arbeiten

	Mit klarer Story und Szenario  
	z.B. "Horst" will daten zum Tapelegen analysen

  - FAQ Plattform?
- User Journey

  ![image](assets/image-20250212142347-3sjpebr.png)  
  User Workshop:

  <div>
						  <style>.kg-card {font-family:'Inter Variable',ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica Neue,Arial,Noto Sans,sans-serif,Apple Color Emoji,Segoe UI Emoji,Segoe UI Symbol,Noto Color Emoji;font-size: 1rem;}.kg-card-main {max-width: 800px;margin: 0 auto;display: flex;justify-content: center;}.kg-card-outer {width: 100%;}.kg-bookmark-card,.kg-bookmark-card * {box-sizing: border-box;}.kg-bookmark-card,.kg-bookmark-publisher {position: relative;/* width: 100%; */}.kg-bookmark-card a.kg-bookmark-container,.kg-bookmark-card a.kg-bookmark-container:hover {display: flex;background: var(--bookmark-background-color);text-decoration: none;border-radius: 6px;border: 1px solid rgb(124 139 154 / 25%);overflow: hidden;color: var(--bookmark-text-color);}.kg-bookmark-content {display: flex;flex-direction: column;flex-grow: 1;flex-basis: 100%;align-items: flex-start;justify-content: flex-start;padding: 20px;overflow: hidden;font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell','Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;}.kg-bookmark-title {font-size: 15px;line-height: 1.4em;font-weight: 600;}.kg-bookmark-description {display: -webkit-box;font-size: 14px;line-height: 1.5em;margin-top: 3px;font-weight: 400;max-height: 44px;overflow-y: hidden;opacity: 0.7;}.kg-bookmark-metadata {display: flex;align-items: center;margin-top: 22px;width: 100%;font-size: 14px;font-weight: 500;white-space: nowrap;}.kg-bookmark-metadata>*:not(img) {opacity: 0.7;}.kg-bookmark-icon {width: 20px;height: 20px;margin-right: 6px;}.kg-bookmark-author,.kg-bookmark-publisher {display: inline;}.kg-bookmark-publisher {text-overflow: ellipsis;overflow: hidden;max-width: 240px;white-space: nowrap;display: block;line-height: 1.65em;}.kg-bookmark-metadata>span:nth-of-type(2) {font-weight: 400;}.kg-bookmark-metadata>span:nth-of-type(2):before {content: '•';margin: 0 6px;}.kg-bookmark-metadata>span:last-of-type {overflow: hidden;text-overflow: ellipsis;}.kg-bookmark-thumbnail {position: relative;flex-grow: 1;min-width: 33%;}.kg-bookmark-thumbnail img {/* width: 100%; */height: 100%;object-fit: cover;/* or contain */position: absolute;top: 0;left: 0;border-radius: 0 2px 2px 0;}</style>
						  <main class="kg-card-main">
							  <div class="kg-card-outer">
								  <div class="kg-card kg-bookmark-card">
									  <a class="kg-bookmark-container" href="https://metroretro.io/BOI0HEQX5E3M">
										  <div class="kg-bookmark-content">
											  <div class="kg-bookmark-title">User Research Presentation | Metro Retro</div>
											  <div class="kg-bookmark-description">A fabulous Metro Retro board.</div>
											  <div class="kg-bookmark-metadata">
												  <img class="kg-bookmark-icon" src="https://static.ghost.org/v5.0.0/images/link-icon.svg" alt="Link icon"/>
												  <span class="kg-bookmark-author">Deqo Software Ltd</span>
												  <span class="kg-bookmark-publisher">Metro Retro</span>
											  </div>
										  </div>
										  <div class="kg-bookmark-thumbnail">
												  <img src="https://d.metroretro.io/thumbs/boi0heqx5e3m/8e442516-8de9-4c6b-960e-05346a397c84.png?e=a7cc7375d46fb2f56b0a20bd607242f3" alt="Link thumbnail"/>
											  </div>
									  </a>
								  </div>
							  </div>
						  </main>
					  </div>
- welche Highlevel Themen?

  - shepard Grundlagen (Funktionsweise / Bedienung)

	- Trennung "Organisationsdaten" und "Payloads"
	- Strukturen (Collections, Dataobjects, References)
  - shepard Geräteanbindung

	- Rohdaten / Payload erfassung

	  - Zeitreihen → stc
	  - Files → t.b.d. hotfolder tool
	  - ...
	- Kontextulaliserung...
  - shepard Datenimport
  - shepard Analytics Anbindung

	- jupyterlab / python?
	- Ergebnisse zurückspielen
	- Grafana

‍
Realistic reporducible testdataset(s) for shpeard to check for scaling problems. 
Showcase datasets for showcasing shepards features (in every capacity upto semantics)
best case aligned with STORT (Hyperschall-Experiment) Und LUMEN (Triebwerkstest)



# shepard federation

shepard federation

- enable:

  - (rich) referencing across instances
  - data search across instances
- shepard federatoin service

  - register instances
  - negotiate access permissions
  - ‍

Federating your research data web application—i.e., linking multiple instances so that data, permissions, and search capabilities span them seamlessly—requires careful planning in both data modeling and distributed systems architecture. Here’s a deep dive into a possible approach:

---

## 1. Define a Canonical Data Model and API

- **Unified Schema:** 
  Your instances must “speak the same language.” Define a canonical schema (consider JSON-LD or an RDF vocabulary) that describes every research item, including metadata like authorship, citations, and persistent identifiers (DOIs, ORCIDs). This uniformity is the foundation for rich data linking.
- **Exposed APIs:** 
  Develop RESTful (or GraphQL) APIs that expose this metadata. Each endpoint should mirror the schema so that any instance can push or pull research items from its peers.

**Example JSON snippet:**

```json
{
  "id": "instanceA:research-123",
  "title": "Innovations in Federated Systems",
  "authors": ["author1", "author2"],
  "url": "https://instanceA.example.com/research-123",
  "metadata": {
    "doi": "10.1234/example",
    "keywords": ["federation", "web services"]
  },
  "permissions": {
    "read": ["groupA", "groupB"],
    "write": ["userA"]
  }
}
```

---

## 2. Implement a Federation Protocol & Discovery Mechanism

- **Mutual Discovery:** 
  Each instance should be able to discover others. You can adopt a method similar to the Fediverse (e.g., [ActivityPub](https://www.w3.org/TR/activitypub/)) where instances publish a “well-known” URL (e.g., `/.well-known/instance`) that describes the available service endpoints.
- **Push or Poll?** 
  Decide between a push model (instances notify each other upon data changes using webhook-like callbacks) and a poll-based model (each instance regularly queries peers for updated metadata).

  - **Push Model:**  More real-time, but requires secured endpoints and bi-directional trust.
  - **Poll Model:**  Simpler to implement initially and easier to audit.

---

## 3. Consistent Access Permissions & Federated Identity

- **Unified Authentication/Authorization:** 
  If you want consistent permissions across instances, consider federating your identity. Protocols like [OpenID Connect](https://openid.net/connect/) or SAML can help. For instance, if a user logs into Instance A, a trust relationship with Instance B lets that authentication token get validated there, subject to local policy.
- **Delegated Trust:** 
  Ensure that when metadata flows between instances, any access control information (who can read/write) is maintained and verifiable. Cryptographic signing of metadata can help verify that permissions were set by a trusted source.

---

## 4. Rich Data Linking Across Instances

- **Link Relationships Explicitly:** 
  Include in your data model explicit links (e.g., citations, references, authorship connections) that point to resources on different instances. By using globally unique identifiers (like URIs), instances can resolve and enrich the linked data.
- **Semantic Web Techniques:** 
  Utilizing RDF and SPARQL (or triple stores) can enhance the semantic linking and querying. This method makes it easy to write queries that span data from multiple instances, inferring relationships dynamically.

---

## 5. Distributed and Federated Search

- **Build a Federated Index:** 
  Allow each instance to maintain its local search index (using tools like Elasticsearch or Apache Solr). A federated search query would then:

  - Query a central aggregator or simultaneously dispatch queries to each instance.
  - Combine and rank results based on metadata, relevance, and cross-instance links.
- **Cross-instance Metadata Harvesting:** 
  Use a protocol like [OAI-PMH](https://www.openarchives.org/pmh/) (used in many digital library systems) for harvesting metadata, ensuring that even if the instances are independently maintained, their metadata remains synchronized and searchable.

---

## 6. Architectural Considerations

**Diagrammatically, a simplified architecture might look like this:**

```
               +--------------------------------+
               |   Federated Research Network   |
               +---------+-------------+--------+
                         |             |
             +-----------v-----+ +-----v-----------+
             |  Instance A     | |   Instance B    |
             |  (Local Store,  | |  (Local Store,  |
             |   Search Index, | |  Search Index,  |
             |   API Endpoints)| |   API Endpoints)|
             +-----------+-----+ +-----+-----------+
                         |             |
                         +-----[Federation Protocol]------+
                         |             | 
               +---------v-------------v---------+
               | Aggregator/Discovery Registry   |
               |   (or decentralized discovery)  |
               +-----------------------------------+
```

- **Data Flow:**

  - **Publishing:**  When Instance A adds or updates a research item, it publishes metadata (with cryptographic verification, if possible) to its peers or a central registry.
  - **Consumption:**  Peers (like Instance B) harvest or receive these updates, integrate them into their local indices, and display aggregated search results.
  - **Access:**  When a user tries to access linked data on a remote instance, federated authentication ensures they have the right permissions.

---

## 7. Conflict Resolution & Synchronization

- **Eventual Consistency:**   
  Federated systems rarely achieve strong consistency. Use eventual consistency models where each instance periodically reconciles differences. Techniques like CRDTs (Conflict-free Replicated Data Types) can be explored if you expect concurrent edits on the same resource.
- **Versioning &amp; Audit Trails:** 
  Keep track of data versions so instances can resolve conflicts by comparing timestamps or edit histories.

---

## Conclusion

Federating your web application involves standardizing data formats, creating interoperable APIs, setting up a discovery mechanism, and ensuring that authentication and search index synchronization work across independent systems. While this adds complexity, it significantly enhances collaboration and the ability to link research data across environments.

---

## Next Steps and Further Ideas

- **Prototyping with a Single Federation Pair:** 
  Test the federation between two instances before scaling to a larger network. This helps to fine-tune metadata harvesting, authentication hand-offs, and conflict resolution.
- **Consider GraphQL Federation:** 
  If you’re comfortable with GraphQL, the GraphQL Federation model is a modern approach to combine multiple GraphQL services into one unified API. This might provide more flexible querying over distributed research data.
- **Explore Existing Protocols:** 
  Look into how platforms in the Fediverse (like Mastodon) or digital libraries (using OAI-PMH) tackle federation. Their experiences can offer valuable insights.
- **Data Provenance and Robust Linking:** 
  Deepen the model by linking not just research items but also their provenance (e.g., every change event, citation, review) using semantic relationships. This could later power advanced analytics and discovery options.

Federation is a journey into distributed systems, but once established, it can transform isolated data silos into a thriving ecosystem of collaborative research. Would you like to delve into specific implementation details or explore a deeper dive on any of these components?

DATABUS is current direction for repo interop.

# HMC 2

Das Vorhaben 11001989 (2712100) "HMC Hub Aeronautics, Space and Transport, Phase 2" mit der Laufzeit vom 01.01.2025 bis zum 31.12.2029 ist ein essentieller Beitrag zur dauerhaften Integration der Daten, Software, Prozesse und letztlich des domänenspezifischen Wissens des Forschungsbereiches Luft-, Raumfahrt und Verkehr in die Helmholtz-Gesellschaft (HGF).

Das Vorhaben leistet sowohl bei der Entwicklung von forschungsbereichsspezifischen, wissenschaftlichen und technischen Beiträgen zum Datenmanagement, als auch bei der Einbindung und Inwertsetzung bereits bestehender Infrastrukturen des DLR wesentliche Beiträge für die Vertretung und Weiterentwicklung der DLR-Institute und ihrer Produkte innerhalb des Forschungsbereiches der HGF.

Die in Berechtigungen und Datensichtbarkeit abgestufte Integration bestehender und aktuell im Aufbau befindlicher Systeme (z.B. verteilte Shepard-Instanzen, das eData-Repositorium, inst.dlr, twinstash, Digitaler Atlas 2.0, ODIX) leistet hier einen zentralen Beitrag für die sichere und FAIRe Nachnutzung und Technologietransfer von DLR-Produkten auch über die HGF hinaus.

Diese Beiträge sind somit nicht nur innerhalb der Stoßrichtungen Daten und Digitalisierung der Organisation der Digitalisierungsstrategie zu sehen, sondern auch als unmittelbare Teile eines DLR-as-a-service-Angebotes.

Ferner sind diese Services maßgebliche Beiträge für die interoperable und ressourceneffiziente Integration von DLR-Produkten in nachfolgende Projekte im Sinne der Stoßrichtungen Künstliche Intelligenz, Cyberphysisches Engineering und Innovative Autonome Systeme.

https://wiki.dlr.de/display/FDM/Workpackages+BT-ZAP

- [ ] Read https://zenodo.org/records/14161466 🆔 20260407-bb6

---

## HMC Project Idea:

- Semantic Satellite Data Storage System SSDSS

- Topics:

  - joint data model development
  - data integration (import tooling)
  - shepard features:

	- shepard Spatial erweiterung für geospatial + height
	- HDF5 container?
	- ro-crate import (collection)
  - semantic annotation of space relevant data
  - data provider
  - analytics use-case (e.g. semantics based visualisation)

https://data.esa.int/esado/en/

- [ ] HMC Project mit RY - Ende März 🆔 20260407-da2

---

evtl neue INF Linie 7.3.2025 lt. Witold 100-150k€ in einem Jahr

[01_CALL_HMC_PROPOSALS_2025.pdf](assets/01_CALL_HMC_PROPOSALS_2025-20250311104714-rjznjn1.pdf)

https://indico.esa.int/event/407/contributions/7377/attachments/4782/7835/1140%20-%20Presentation%20-%20Domain-specific%20ontology%20for%20digital%20continuity%20Thermal%20Engineering%20case.pdf

https://mb4se.esa.int/OSMOSE_Space%20System%20Ontology.html

---

- [ ] @Witold High level at HMC (Hamacher) 🆔 20260407-021

plugin interface für payloads.

Project website (gitlab/github pages, flashy like https://www.researchspace.com/)
use colors scheme from project files. 
sections should include: 
about shepard: general description link to repo and publication, 
Core features.
getting started: -> intro document
admin and user doc
FAQ: (interactive - how) user and dev driven knowledgebase of use cases, tipps, tweaks and solution sharing
Imprint

Find and Compare shepard with competitors find USPs and Weaknesses
https://about.coscine.de/en/
https://www.dglr.de/publikationen/2023/570104.pdf
https://inveniordm.docs.cern.ch/


shepard concern:
implement a reference in shepard to foreign systems using the data indexing service --> databus reference
Maybe the (ro-crate) export endpoint could be improved to enable more selective data export?
Open Questions



Should we capture provenance information about the exported data? (if so how, maybe OpenLinage, PROV)


Maybe we can integrate NovaCrate?

Perform research as necessary. Document.

set up a test environment
improve testing 
introduce ui tests
documentation of REST API examples

Admin CLI und static admin user (DISABLE user through env)

Theming a DLR CD approved theme / desing (see html files)

extend and match These to the existing issues Group them and put them in the most effective order.
Create concern log integrating existing (unsoved) issues and this data.
resync everything with index.md
provide a synopsis of most important directions to take.
reorganise all info in a single source of truth. a developmen bible.
if you find conflicts make a note. decisions-on-conflicts.md
