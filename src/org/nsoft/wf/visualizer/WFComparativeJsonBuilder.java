package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;

import java.util.*;

/**
 * Membangun payload JSON untuk mode COMPARATIVE yang dikonsumsi oleh vis.js Network
 * di sisi client (wf_visualizer.zul → JavaScript).
 *
 * Output JSON:
 * <pre>
 * {
 *   "nodes": [
 *     {
 *       "id": 1001,
 *       "label": "Approve Manager",
 *       "group": "definition",     // selalu ada
 *       "color": { "background": "#2196F3", "border": "#1976D2" },
 *       "shape": "box",
 *       "actualState": "CO",       // CO/OS/FA/null
 *       "avgDurationMin": 142,
 *       "isBottleneck": false,
 *       "visitCount": 3,
 *       "tooltip": "Approve Manager\nDikunjungi: 3x\nAvg: 142 menit"
 *     }
 *   ],
 *   "edges": [
 *     {
 *       "id": 2001,
 *       "from": 1001,
 *       "to": 1002,
 *       "traveled": true,          // apakah edge ini pernah dilalui
 *       "dashes": false
 *     }
 *   ],
 *   "meta": {
 *     "processCount": 5,
 *     "bottleneckNodeID": 1003,
 *     "showDefinition": true,
 *     "showActual": true
 *   }
 * }
 * </pre>
 */
public class WFComparativeJsonBuilder {

    // ── Color scheme (sesuai README) ──────────────────────────────────────────
    private static final String COLOR_START         = "#4CAF50";
    private static final String COLOR_START_BORDER  = "#388E3C";
    private static final String COLOR_END           = "#F44336";
    private static final String COLOR_END_BORDER    = "#C62828";
    private static final String COLOR_USER_CHOICE   = "#2196F3";
    private static final String COLOR_USER_BORDER   = "#1565C0";
    private static final String COLOR_SUBWF         = "#9C27B0";
    private static final String COLOR_SUBWF_BORDER  = "#6A1B9A";
    private static final String COLOR_WAIT          = "#FF9800";
    private static final String COLOR_WAIT_BORDER   = "#E65100";
    private static final String COLOR_DEFAULT       = "#78909C";
    private static final String COLOR_DEFAULT_BORDER= "#455A64";

    // Overlay actual status
    private static final String COLOR_DONE          = "#66BB6A";
    private static final String COLOR_DONE_BORDER   = "#388E3C";
    private static final String COLOR_RUNNING       = "#FFA726";
    private static final String COLOR_RUNNING_BORDER= "#E65100";
    private static final String COLOR_FAILED        = "#EF5350";
    private static final String COLOR_FAILED_BORDER = "#C62828";
    private static final String COLOR_BOTTLENECK    = "#D32F2F";

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Bangun JSON comparative dari definisi + list process instance.
     *
     * @param nodes             definisi node dari AD_WF_Node
     * @param edges             definisi edge dari AD_WF_NodeNext
     * @param processes         list process instance (sudah berisi aktivitas)
     * @param showDefinition    tampilkan layer definisi
     * @param showActual        tampilkan layer aktual
     * @param highlightBottleneck warnai node bottleneck merah terang
     * @return JSON string
     */
    public String build(List<WFNodeDTO> nodes, List<WFEdgeDTO> edges,
                        List<WFProcessDTO> processes,
                        boolean showDefinition, boolean showActual,
                        boolean highlightBottleneck) {

        // ── 1. Hitung statistik per node ──────────────────────────────────
        // nodeStats: nodeID → { totalDuration, visitCount, lastState }
        Map<Integer, long[]>   totalDurationMap = new HashMap<>();  // [sum, count]
        Map<Integer, String>   lastStateMap     = new HashMap<>();
        Set<Integer>           visitedNodes     = new HashSet<>();

        // Pasang yang traveled: edge source→target yang benar-benar dilalui
        // Kita track sebagai "nodeID → Set<nextNodeID>" yang sudah dilalui
        Map<Integer, Set<Integer>> traveledEdges = new HashMap<>();

        for (WFProcessDTO proc : processes) {
            List<WFActivityDTO> acts = proc.getActivities();
            for (int i = 0; i < acts.size(); i++) {
                WFActivityDTO act = acts.get(i);
                int nid = act.getAdWFNodeID();
                visitedNodes.add(nid);

                // Durasi
                long dur = act.getDurationMinutes();
                long[] stat = totalDurationMap.computeIfAbsent(nid, k -> new long[]{0L, 0L});
                stat[0] += dur;
                stat[1]++;

                // State terakhir (prioritas: FA > OS/WI > CO)
                String existing = lastStateMap.get(nid);
                String cur = act.getWfState();
                if (existing == null
                        || (act.isFailed())
                        || (act.isRunning() && "CO".equals(existing))) {
                    lastStateMap.put(nid, cur);
                }

                // Track edge yang dilalui (jika ada aktivitas berikutnya)
                if (i + 1 < acts.size()) {
                    int nextNid = acts.get(i + 1).getAdWFNodeID();
                    traveledEdges.computeIfAbsent(nid, k -> new HashSet<>()).add(nextNid);
                }
            }
        }

        // ── 2. Cari bottleneck (node dengan rata-rata durasi tertinggi) ───
        int    bottleneckNodeID  = -1;
        double maxAvgDuration    = 0;
        for (Map.Entry<Integer, long[]> e : totalDurationMap.entrySet()) {
            double avg = (double) e.getValue()[0] / e.getValue()[1];
            if (avg > maxAvgDuration) {
                maxAvgDuration   = avg;
                bottleneckNodeID = e.getKey();
            }
        }

        // ── 3. Build JSON ─────────────────────────────────────────────────
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // ── nodes ──
        sb.append("\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            WFNodeDTO node   = nodes.get(i);
            int       nid    = node.getAdWFNodeID();
            boolean   visited = visitedNodes.contains(nid);
            String    state  = lastStateMap.get(nid);      // null jika belum dilalui
            long[]    stat   = totalDurationMap.get(nid);
            long      avgDur = (stat != null && stat[1] > 0) ? stat[0] / stat[1] : 0;
            long      visits = (stat != null) ? stat[1] : 0;
            boolean   isBottleneck = highlightBottleneck && nid == bottleneckNodeID;

            String[] colors = resolveColor(node, state, visited, isBottleneck);
            String   bg     = colors[0];
            String   border = colors[1];

            // Opacity/font untuk node belum dilalui
            double opacity = (!showActual || visited) ? 1.0 : 0.35;

            // Tooltip
            String tooltip = buildTooltip(node.getName(), state, visits, avgDur);

            sb.append("{");
            appendKV(sb, "id",          nid);       sb.append(",");
            appendKV(sb, "label",       node.getName()); sb.append(",");
            appendKV(sb, "group",       "definition"); sb.append(",");
            sb.append("\"color\":{");
            appendKV(sb, "background",  bg);        sb.append(",");
            appendKV(sb, "border",      border);
            sb.append("},");
            appendKV(sb, "shape",       nodeShape(node.getAction())); sb.append(",");
            appendKV(sb, "actualState", state != null ? state : ""); sb.append(",");
            sb.append("\"avgDurationMin\":").append(avgDur).append(",");
            sb.append("\"isBottleneck\":").append(isBottleneck).append(",");
            sb.append("\"visitCount\":").append(visits).append(",");
            sb.append("\"opacity\":").append(opacity).append(",");
            appendKV(sb, "title", tooltip);
            sb.append("}");
            if (i < nodes.size() - 1) sb.append(",");
        }
        sb.append("],");

        // ── edges ──
        sb.append("\"edges\":[");
        for (int i = 0; i < edges.size(); i++) {
            WFEdgeDTO edge    = edges.get(i);
            int       from    = edge.getAdWFNodeID();
            int       to      = edge.getAdWFNextID();
            boolean   traveled = traveledEdges.getOrDefault(from, Collections.emptySet()).contains(to);

            sb.append("{");
            appendKV(sb, "id",   edge.getAdWFNodeNextID()); sb.append(",");
            sb.append("\"from\":").append(from).append(",");
            sb.append("\"to\":").append(to).append(",");
            sb.append("\"traveled\":").append(traveled).append(",");
            sb.append("\"dashes\":").append(!traveled).append(",");

            // Warna edge: traveled=biru tebal, belum=abu tipis
            if (traveled) {
                sb.append("\"color\":{\"color\":\"#1976D2\",\"opacity\":1.0},");
                sb.append("\"width\":2.5,");
            } else {
                sb.append("\"color\":{\"color\":\"#90A4AE\",\"opacity\":0.4},");
                sb.append("\"width\":1.0,");
            }
            sb.append("\"arrows\":\"to\"");
            sb.append("}");
            if (i < edges.size() - 1) sb.append(",");
        }
        sb.append("],");

        // ── meta ──
        sb.append("\"meta\":{");
        sb.append("\"processCount\":").append(processes.size()).append(",");
        sb.append("\"bottleneckNodeID\":").append(bottleneckNodeID).append(",");
        sb.append("\"showDefinition\":").append(showDefinition).append(",");
        sb.append("\"showActual\":").append(showActual);
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String[] resolveColor(WFNodeDTO node, String state, boolean visited, boolean isBottleneck) {
        if (isBottleneck)           return new String[]{COLOR_BOTTLENECK, COLOR_FAILED_BORDER};
        if (visited && state != null) {
            switch (state) {
                case "CO": return new String[]{COLOR_DONE,    COLOR_DONE_BORDER};
                case "FA": return new String[]{COLOR_FAILED,  COLOR_FAILED_BORDER};
                case "OS":
                case "WI":
                case "SU": return new String[]{COLOR_RUNNING, COLOR_RUNNING_BORDER};
            }
        }
        // Warna berdasarkan tipe node (dari definisi)
        String action = node.getAction() != null ? node.getAction() : "";
        switch (action) {
            case "S":  return new String[]{COLOR_START,       COLOR_START_BORDER};
            case "F":  return new String[]{COLOR_END,         COLOR_END_BORDER};
            case "C":  return new String[]{COLOR_USER_CHOICE, COLOR_USER_BORDER};
            case "X":  return new String[]{COLOR_SUBWF,       COLOR_SUBWF_BORDER};
            case "W":  return new String[]{COLOR_WAIT,        COLOR_WAIT_BORDER};
            default:   return new String[]{COLOR_DEFAULT,     COLOR_DEFAULT_BORDER};
        }
    }

    private String nodeShape(String action) {
        if (action == null) return "box";
        switch (action) {
            case "S":  return "circle";
            case "F":  return "circle";
            case "C":  return "diamond";
            case "X":  return "square";
            default:   return "box";
        }
    }

    private String buildTooltip(String name, String state, long visits, long avgDur) {
        StringBuilder tt = new StringBuilder();
        tt.append(name);
        if (visits > 0) {
            tt.append("\\nDikunjungi: ").append(visits).append("x");
            tt.append("\\nAvg durasi: ").append(avgDur).append(" mnt");
        }
        if (state != null) {
            tt.append("\\nStatus: ").append(stateLabel(state));
        }
        return tt.toString();
    }

    private String stateLabel(String state) {
        if (state == null) return "-";
        switch (state) {
            case "CO": return "Selesai";
            case "FA": return "Gagal";
            case "OS": return "Menunggu";
            case "WI": return "Menunggu (sleep)";
            case "SU": return "Suspended";
            default:   return state;
        }
    }

    // ── JSON string escaping minimal ──────────────────────────────────────────
    private void appendKV(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":\"");
        if (value != null) sb.append(value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
        sb.append("\"");
    }

    private void appendKV(StringBuilder sb, String key, int value) {
        sb.append("\"").append(key).append("\":").append(value);
    }
}
