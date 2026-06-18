package org.nsoft.wf.visualizer.model;

import java.sql.Timestamp;

/**
 * WFProcessDTO — satu instance workflow dari AD_WF_Process.
 */
public class WFProcessDTO {
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
