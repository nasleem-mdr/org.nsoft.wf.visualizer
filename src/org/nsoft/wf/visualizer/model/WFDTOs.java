package org.nsoft.wf.visualizer.model;

import java.sql.Timestamp;

// ─────────────────────────────────────────────────────────────────────────────
// WFNodeDTO — merepresentasikan satu node dari AD_WF_Node
// ─────────────────────────────────────────────────────────────────────────────
class WFNodeDTO {
    public int    nodeId;
    public String name;
    public String action;       // AD_WF_Node.Action: "U"=UserChoice, "S"=SubWF, "W"=Wait, dll
    public String nodeType;     // "Start", "End", "Middle"
    public int    xPosition;    // AD_WF_Node.XPosition
    public int    yPosition;    // AD_WF_Node.YPosition
    public int    workflowId;

    // Statistik (diisi oleh WFDataProvider untuk mode STAT & COMPARATIVE)
    public int    totalExecutions;
    public double avgDurationMinutes;
    public int    failCount;

    /** Warna node berdasarkan action & statistik */
    public String resolveColor(boolean highlightBottleneck, double maxAvgDuration) {
        if ("Start".equals(nodeType))  return "#4CAF50";
        if ("End".equals(nodeType))    return "#F44336";
        if (highlightBottleneck && maxAvgDuration > 0
                && avgDurationMinutes == maxAvgDuration) return "#FF1744";

        switch (action == null ? "" : action) {
            case "U": return "#2196F3"; // UserChoice
            case "S": return "#9C27B0"; // SubWorkflow
            case "W": return "#FF9800"; // WaitSleep
            case "Z": return "#00BCD4"; // SetVariable
            default:  return "#78909C"; // Default / DocumentAction
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WFEdgeDTO — transisi antar node dari AD_WF_NodeNext
// ─────────────────────────────────────────────────────────────────────────────
class WFEdgeDTO {
    public int    edgeId;
    public int    fromNodeId;
    public int    toNodeId;
    public String transitionCode; // AD_WF_NodeNext.TransitionCode (optional)
    public String description;

    // Untuk mode COMPARATIVE: apakah transisi ini benar-benar dilalui?
    public boolean traversedInActual = false;
    public int     traversalCount    = 0;
}

// ─────────────────────────────────────────────────────────────────────────────
// WFProcessDTO — satu instance workflow dari AD_WF_Process
// ─────────────────────────────────────────────────────────────────────────────
class WFProcessDTO {
    public int    processId;
    public int    workflowId;
    public String wfState;     // AD_WF_Process.WFState: "CC"=Complete, "AD"=Aborted, dll
    public int    adTableId;
    public int    recordId;
    public String documentNo;  // diisi dari join ke dokumen
    public Timestamp startDate;
    public Timestamp endDate;
    public int    userId;
    public String userName;

    /** Durasi proses dalam menit */
    public double getDurationMinutes() {
        if (startDate == null || endDate == null) return 0;
        return (endDate.getTime() - startDate.getTime()) / 60000.0;
    }

    /** Label warna berdasarkan state */
    public String resolveColor() {
        if (wfState == null) return "#90A4AE";
        switch (wfState) {
            case "CC": return "#66BB6A"; // Completed
            case "AD": return "#EF5350"; // Aborted
            case "IP": return "#FFA726"; // In Progress
            case "ST": return "#AB47BC"; // Suspended
            default:   return "#90A4AE";
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WFActivityDTO — satu aktivitas dari AD_WF_Activity (per node per instance)
// ─────────────────────────────────────────────────────────────────────────────
class WFActivityDTO {
    public int    activityId;
    public int    processId;
    public int    nodeId;
    public String nodeName;
    public String wfState;       // "CC", "AD", "IP", "ST"
    public Timestamp created;
    public Timestamp updated;
    public int    userId;
    public String userName;
    public String textMsg;       // pesan approval/rejection

    /** Durasi aktivitas dalam menit */
    public double getDurationMinutes() {
        if (created == null || updated == null) return 0;
        return (updated.getTime() - created.getTime()) / 60000.0;
    }

    public String resolveColor() {
        if (wfState == null) return "#90A4AE";
        switch (wfState) {
            case "CC": return "#66BB6A"; // Done
            case "AD": return "#EF5350"; // Failed/Aborted
            case "IP": return "#FFA726"; // Running
            default:   return "#90A4AE";
        }
    }
}
