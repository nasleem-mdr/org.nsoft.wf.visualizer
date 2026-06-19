package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;

import java.util.List;

/**
 * Membangun payload JSON untuk mode FLOW — visualisasi definisi workflow murni
 * dari AD_WF_Node + AD_WF_NodeNext tanpa data eksekusi aktual.
 *
 * Output JSON:
 * <pre>
 * {
 *   "nodes": [
 *     { "id": 1001, "label": "Approve", "shape": "box",
 *       "color": { "background": "#2196F3", "border": "#1565C0" },
 *       "title": "Approve\nTipe: UserChoice" }
 *   ],
 *   "edges": [
 *     { "id": 2001, "from": 1001, "to": 1002,
 *       "label": "", "arrows": "to" }
 *   ]
 * }
 * </pre>
 */
public class WFFlowJsonBuilder {

    // ── Color scheme (sama dengan Comparative agar konsisten) ─────────────────
    private static final String COLOR_START        = "#4CAF50";
    private static final String COLOR_START_BORDER = "#388E3C";
    private static final String COLOR_END          = "#F44336";
    private static final String COLOR_END_BORDER   = "#C62828";
    private static final String COLOR_USER_CHOICE  = "#2196F3";
    private static final String COLOR_USER_BORDER  = "#1565C0";
    private static final String COLOR_SUBWF        = "#9C27B0";
    private static final String COLOR_SUBWF_BORDER = "#6A1B9A";
    private static final String COLOR_WAIT         = "#FF9800";
    private static final String COLOR_WAIT_BORDER  = "#E65100";
    private static final String COLOR_APPS         = "#00BCD4";
    private static final String COLOR_APPS_BORDER  = "#00838F";
    private static final String COLOR_DEFAULT      = "#78909C";
    private static final String COLOR_DEFAULT_BORDER = "#455A64";

    /**
     * Bangun JSON flow dari definisi node + edge saja.
     *
     * @param nodes  list WFNodeDTO dari AD_WF_Node
     * @param edges  list WFEdgeDTO dari AD_WF_NodeNext
     * @return JSON string
     */
    public String build(List<WFNodeDTO> nodes, List<WFEdgeDTO> edges) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // ── nodes ──
        sb.append("\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            WFNodeDTO node   = nodes.get(i);
            String[]  colors = resolveColor(node.getAction());
            String    shape  = nodeShape(node.getAction());
            String    tipe   = actionLabel(node.getAction());

            sb.append("{");
            appendKV(sb, "id",    node.getAdWFNodeID()); sb.append(",");
            appendKV(sb, "label", truncate(node.getName(), 22)); sb.append(",");
            appendKV(sb, "shape", shape); sb.append(",");
            sb.append("\"color\":{");
            appendKV(sb, "background", colors[0]); sb.append(",");
            appendKV(sb, "border",     colors[1]);
            sb.append("},");
            appendKV(sb, "font", "{\"color\":\"#ECEFF1\",\"size\":13}"); sb.append(",");
            appendKV(sb, "title", node.getName() + "\\nTipe: " + tipe);
            sb.append("}");
            if (i < nodes.size() - 1) sb.append(",");
        }
        sb.append("],");

        // ── edges ──
        sb.append("\"edges\":[");
        for (int i = 0; i < edges.size(); i++) {
            WFEdgeDTO edge = edges.get(i);
            sb.append("{");
            appendKV(sb, "id",   edge.getAdWFNodeNextID()); sb.append(",");
            sb.append("\"from\":").append(edge.getAdWFNodeID()).append(",");
            sb.append("\"to\":").append(edge.getAdWFNextID()).append(",");
            appendKV(sb, "label", edge.getDescription() != null ? truncate(edge.getDescription(), 16) : "");
            sb.append(",");
            sb.append("\"arrows\":\"to\",");
            sb.append("\"color\":{\"color\":\"#546E7A\",\"opacity\":0.9},");
            sb.append("\"width\":1.8");
            sb.append("}");
            if (i < edges.size() - 1) sb.append(",");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String[] resolveColor(String action) {
        if (action == null) return new String[]{COLOR_DEFAULT, COLOR_DEFAULT_BORDER};
        switch (action) {
            case "S":  return new String[]{COLOR_START,       COLOR_START_BORDER};
            case "F":  return new String[]{COLOR_END,         COLOR_END_BORDER};
            case "C":  return new String[]{COLOR_USER_CHOICE, COLOR_USER_BORDER};
            case "X":  return new String[]{COLOR_SUBWF,       COLOR_SUBWF_BORDER};
            case "W":  return new String[]{COLOR_WAIT,        COLOR_WAIT_BORDER};
            case "A":  return new String[]{COLOR_APPS,        COLOR_APPS_BORDER};
            default:   return new String[]{COLOR_DEFAULT,     COLOR_DEFAULT_BORDER};
        }
    }

    private String nodeShape(String action) {
        if (action == null) return "box";
        switch (action) {
            case "S":  return "ellipse";
            case "F":  return "ellipse";
            case "C":  return "diamond";
            case "X":  return "square";
            default:   return "box";
        }
    }

    private String actionLabel(String action) {
        if (action == null) return "Process";
        switch (action) {
            case "S":  return "Start";
            case "F":  return "Finish";
            case "C":  return "User Choice";
            case "X":  return "Sub-Workflow";
            case "W":  return "Wait/Sleep";
            case "A":  return "Apps Process";
            case "D":  return "Document Action";
            case "R":  return "Apps Report";
            case "E":  return "Smart Browse";
            default:   return action;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void appendKV(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":\"");
        if (value != null) {
            sb.append(value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n"));
        }
        sb.append("\"");
    }

    private void appendKV(StringBuilder sb, String key, int value) {
        sb.append("\"").append(key).append("\":").append(value);
    }
}
