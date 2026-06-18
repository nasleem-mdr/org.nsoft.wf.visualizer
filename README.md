# org.nsoft.wf.visualizer — iDempiere Workflow Visualizer Plugin

## Overview

Plugin ini menyediakan visualisasi komprehensif untuk Workflow iDempiere dengan 4 mode tampilan:

| Mode | Deskripsi |
|------|-----------|
| **Flow Diagram** | Graph node & edge dari definisi AD_Workflow + AD_WF_Node + AD_WF_NodeNext |
| **Comparative Graph** | Garis penghubung yang membandingkan definisi vs aktual eksekusi (AD_WF_Process) |
| **Timeline/Gantt** | Per-instance timeline dari AD_WF_Activity dengan durasi per node |
| **Statistical Chart** | Bar/Pie/Line chart agregat: completion rate, avg duration, bottleneck nodes |

## Sumber Data

```
AD_Workflow          — Definisi workflow (nama, dokumen target)
AD_WF_Node           — Node-node dalam workflow (Start, End, UserChoice, dll)
AD_WF_NodeNext       — Transisi antar node (edges)
AD_WF_Process        — Instance workflow yang berjalan / selesai per dokumen
AD_WF_Activity       — Aktivitas per node per instance (dengan timestamp)
```

## Struktur Plugin

```
org.nsoft.wf.visualizer/
├── META-INF/
│   └── MANIFEST.MF
├── OSGI-INF/
│   └── component.xml
├── WEB-INF/
│   └── zk/
│       └── wf_visualizer.zul          ← ZUL entry point
├── web/
│   └── js/
│       └── vis-network.min.js         ← vis.js bundled (network + timeline)
├── src/
│   └── org/nsoft/wf/visualizer/
│       ├── WFVisualizerForm.java       ← Main ZK Form
│       ├── WFDataProvider.java         ← Query layer (semua SQL)
│       ├── WFVisMode.java              ← Enum: FLOW, COMPARATIVE, TIMELINE, STAT
│       ├── model/
│       │   ├── WFNodeDTO.java
│       │   ├── WFEdgeDTO.java
│       │   ├── WFProcessDTO.java
│       │   └── WFActivityDTO.java
│       └── factory/
│           └── NSoftWFVisFactory.java  ← IFormFactory registration
├── 2pack/
│   └── WFVisualizer_2pack.xml         ← AD_Form, AD_Menu entries
└── pom.xml
```

## Parameter Visualisasi

| Parameter | Tipe | Deskripsi |
|-----------|------|-----------|
| `AD_Workflow_ID` | Lookup | Workflow yang ingin divisualisasi |
| `AD_WF_Process_ID` | Lookup | Filter instance spesifik (optional) |
| `DateFrom / DateTo` | Date | Filter rentang waktu aktivitas |
| `VisMode` | Enum | FLOW / COMPARATIVE / TIMELINE / STAT |
| `ChartType` | Enum | BAR / PIE / LINE (untuk mode STAT) |
| `ShowDefinition` | Checkbox | Tampilkan layer definisi di Comparative mode |
| `ShowActual` | Checkbox | Tampilkan layer actual execution |
| `GroupBy` | Enum | NODE / USER / DATE (untuk STAT) |
| `HighlightBottleneck` | Checkbox | Warna merah node dengan avg durasi tertinggi |

## Node Color Scheme

```
Start node          → #4CAF50 (hijau)
End node            → #F44336 (merah)
UserChoice node     → #2196F3 (biru)
SubWorkflow node    → #9C27B0 (ungu)
WaitSleep node      → #FF9800 (oranye)
Activity (done)     → #66BB6A
Activity (running)  → #FFA726
Activity (failed)   → #EF5350
```
