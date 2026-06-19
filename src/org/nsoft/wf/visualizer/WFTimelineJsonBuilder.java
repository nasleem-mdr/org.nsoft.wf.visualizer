package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Membangun payload JSON untuk mode TIMELINE — vis.Timeline (Gantt horizontal).
 *
 * Setiap GROUP  = satu AD_WF_Process (satu baris di gantt).
 * Setiap ITEM   = satu AD_WF_Activity (satu bar dalam baris tersebut).
 *
 * Output JSON:
 * <pre>
 * {
 *   "groups": [
 *     { "id": 5001, "content": "SO-00001 [CC]" }
 *   ],
 *   "items": [
 *     {
 *       "id": 9001,
 *       "group": 5001,
 *       "content": "Approve Manager",
 *       "start": "2025-01-03T08:00:00",
 *       "end":   "2025-01-03T10:30:00",
 *       "style": "background-color:#66BB6A;border-color:#388E3C;",
 *       "title": "Approve Manager\nDurasi: 150 mnt\nUser: Admin\nStatus: Selesai"
 *     }
 *   ],
 *   "options": {
 *     "start": "2025-01-01T00:00:00",
 *     "end":   "2025-01-10T00:00:00"
 *   }
 * }
 * </pre>
 */
public class WFTimelineJsonBuilder {

    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    // ── Warna per wfState (sama dengan Comparative) ───────────────────────────
    private static final String COLOR_DONE    = "#66BB6A; border-color:#388E3C";
    private static final String COLOR_RUNNING = "#FFA726; border-color:#E65100";
    private static final String COLOR_FAILED  = "#EF5350; border-color:#C62828";
    private static final String COLOR_DEFAULT = "#78909C; border-color:#455A64";

    /**
     * Bangun JSON Timeline dari list process + aktivitas.
     *
     * @param processes  list WFProcessDTO (sudah berisi aktivitas)
     * @return JSON string
     */
    public String build(List<WFProcessDTO> processes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        Timestamp globalMin = null;
        Timestamp globalMax = null;

        // ── groups ──
        sb.append("\"groups\":[");
        for (int i = 0; i < processes.size(); i++) {
            WFProcessDTO proc = processes.get(i);
            String label = escape(proc.getDocumentNo()) + " [" + escape(proc.getWfState()) + "]";
            sb.append("{");
            sb.append("\"id\":").append(proc.getAdWFProcessID()).append(",");
            appendKV(sb, "content", label);
            sb.append("}");
            if (i < processes.size() - 1) sb.append(",");
        }
        sb.append("],");

        // ── items ──
        sb.append("\"items\":[");
        boolean firstItem = true;
        for (WFProcessDTO proc : processes) {
            List<WFActivityDTO> acts = proc.getActivities();
            for (WFActivityDTO act : acts) {
                if (act.created == null) continue;

                Timestamp start = act.created;
                Timestamp end   = (act.updated != null) ? act.updated
                        : new Timestamp(System.currentTimeMillis());

                // Pastikan end > start minimal 1 mnt agar bar tidak hilang
                if (end.getTime() <= start.getTime()) {
                    end = new Timestamp(start.getTime() + 60_000L);
                }

                // Track global range untuk options.start/end
                if (globalMin == null || start.before(globalMin)) globalMin = start;
                if (globalMax == null || end.after(globalMax))     globalMax = end;

                String style  = "background-color:" + colorFor(act.wfState) + ";color:#fff;border-radius:4px;";
                String tooltip = buildTooltip(act);
                long   durMin  = (long) act.getDurationMinutes();

                if (!firstItem) sb.append(",");
                firstItem = false;

                sb.append("{");
                sb.append("\"id\":").append(act.activityId).append(",");
                sb.append("\"group\":").append(proc.getAdWFProcessID()).append(",");
                appendKV(sb, "content", truncate(act.nodeName != null ? act.nodeName : "Node " + act.nodeId, 20));
                sb.append(",");
                appendKV(sb, "start", ISO.format(start)); sb.append(",");
                appendKV(sb, "end",   ISO.format(end));   sb.append(",");
                appendKV(sb, "style", style); sb.append(",");
                appendKV(sb, "title", tooltip);
                sb.append("}");
            }
        }
        sb.append("],");

        // ── options ──
        // Tambah padding 10% di kiri-kanan agar tidak kepotong
        long rangeMs  = (globalMin != null && globalMax != null)
                        ? (globalMax.getTime() - globalMin.getTime()) : 86_400_000L;
        long padMs    = Math.max(rangeMs / 10, 3_600_000L);
        Timestamp optStart = (globalMin != null)
                ? new Timestamp(globalMin.getTime() - padMs)
                : new Timestamp(System.currentTimeMillis() - 86_400_000L);
        Timestamp optEnd   = (globalMax != null)
                ? new Timestamp(globalMax.getTime() + padMs)
                : new Timestamp(System.currentTimeMillis());

        sb.append("\"options\":{");
        appendKV(sb, "start", ISO.format(optStart)); sb.append(",");
        appendKV(sb, "end",   ISO.format(optEnd));   sb.append(",");
        sb.append("\"stack\":true,");
        sb.append("\"showMajorLabels\":true,");
        sb.append("\"showMinorLabels\":true,");
        sb.append("\"zoomMin\":60000,");         // min zoom: 1 menit
        sb.append("\"zoomMax\":31536000000,");   // max zoom: 1 tahun
        sb.append("\"tooltip\":{\"followMouse\":true}");
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String colorFor(String state) {
        if (state == null) return COLOR_DEFAULT;
        switch (state) {
            case "CC": return COLOR_DONE;
            case "AD": return COLOR_FAILED;
            case "IP": return COLOR_RUNNING;
            default:   return COLOR_DEFAULT;
        }
    }

    private String buildTooltip(WFActivityDTO act) {
        StringBuilder tt = new StringBuilder();
        tt.append(act.nodeName != null ? act.nodeName : "Node " + act.nodeId);
        tt.append("\\nDurasi: ").append((long) act.getDurationMinutes()).append(" mnt");
        if (act.userName != null && !act.userName.isEmpty()) {
            tt.append("\\nUser: ").append(act.userName);
        }
        tt.append("\\nStatus: ").append(stateLabel(act.wfState));
        if (act.textMsg != null && !act.textMsg.isEmpty()) {
            tt.append("\\nPesan: ").append(truncate(act.textMsg, 60));
        }
        return tt.toString();
    }

    private String stateLabel(String state) {
        if (state == null) return "-";
        switch (state) {
            case "CC": return "Selesai";
            case "AD": return "Aborted";
            case "IP": return "Berjalan";
            case "ST": return "Start";
            default:   return state;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void appendKV(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":\"");
        if (value != null) sb.append(escape(value));
        sb.append("\"");
    }
}
