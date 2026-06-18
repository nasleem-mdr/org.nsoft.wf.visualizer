package org.nsoft.wf.visualizer.model;

import java.sql.Timestamp;

/**
 * WFActivityDTO — satu aktivitas dari AD_WF_Activity (per node per instance).
 */
public class WFActivityDTO {
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
