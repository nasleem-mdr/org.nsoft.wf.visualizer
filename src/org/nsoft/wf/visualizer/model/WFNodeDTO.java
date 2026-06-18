package org.nsoft.wf.visualizer.model;

/**
 * WFNodeDTO — merepresentasikan satu node dari AD_WF_Node.
 */
public class WFNodeDTO {
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
