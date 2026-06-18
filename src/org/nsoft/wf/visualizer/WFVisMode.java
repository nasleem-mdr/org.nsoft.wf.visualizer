package org.nsoft.wf.visualizer;

/**
 * Enum untuk mode visualisasi Workflow.
 */
public enum WFVisMode {

    /**
     * Flow diagram: node & edge dari definisi AD_Workflow.
     * Menampilkan struktur statis workflow tanpa data eksekusi.
     */
    FLOW("Flow Diagram"),

    /**
     * Comparative graph: membandingkan definisi vs aktual eksekusi.
     * Layer biru = definisi (AD_WF_Node + AD_WF_NodeNext).
     * Layer oranye = jalur aktual yang dilalui (AD_WF_Activity).
     * Garis penghubung antar layer menunjukkan perbedaan/kesesuaian.
     */
    COMPARATIVE("Comparative Graph"),

    /**
     * Timeline/Gantt per instance workflow (AD_WF_Activity dengan timestamp).
     */
    TIMELINE("Timeline / Gantt"),

    /**
     * Statistical chart: bar/pie/line agregat data workflow.
     */
    STAT("Statistical Chart");

    private final String displayName;

    WFVisMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static WFVisMode fromString(String s) {
        if (s == null) return FLOW;
        for (WFVisMode m : values()) {
            if (m.name().equalsIgnoreCase(s)) return m;
        }
        return FLOW;
    }
}
