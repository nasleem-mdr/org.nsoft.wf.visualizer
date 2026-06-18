package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * WFVisJsonBuilder — Mengkonversi DTO ke JSON string yang siap dikonsumsi vis.js
 * di sisi browser.
 *
 * Tidak ada dependency ke ZK/iDempiere UI — murni String building.
 */
public class WFVisJsonBuilder {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // ─────────────────────────────────────────────────────────────────────────
    // 1. FLOW — vis-network nodes & edges
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build JSON untuk mode FLOW (vis.js Network).
     *
     * Output format:
     * {
     *   "nodes": [ { id, label, color, shape, x, y, title } ],
     *   "edges": [ { id, from, to, label, arrows } ]
     * }
     */
    public String buildFlowJson(List<WFNodeDTO> nodes, List<WFEdgeDTO> edges) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"nodes\":[");

        boolean first = true;
        for (WFNodeDTO n : nodes) {
            if (!first) sb.append(",");
            first = false;
            String shape = resolveShape(n.action, n.nodeType);
            String color = n.resolveColor(false, 0);
            sb.append(String.format(
                "{\"id\":%d,\"label\":\"%s\",\"color\":\"%s\",\"shape\":\"%s\"," +
                "\"x\":%d,\"y\":%d,\"title\":\"%s\",\"font\":{\"color\":\"#fff\"}}",
                n.nodeId,
                escapeJson(n.name),
                color,
                shape,
                n.xPosition * 3,   // scale XPosition ke pixel
                n.yPosition * 3,
                escapeJson("Action: " + nvl(n.action) + " | Type: " + nvl(n.nodeType))
            ));
        }

        sb.append("],\"edges\":[");
        first = true;
        for (WFEdgeDTO e : edges) {
            if (!first) sb.append(",");
            first = false;
            sb.append(String.format(
                "{\"id\":%d,\"from\":%d,\"to\":%d,\"arrows\":\"to\",\"label\":\"%s\"}",
                e.edgeId, e.fromNodeId, e.toNodeId,
                escapeJson(nvl(e.transitionCode))
            ));
        }
        sb.append("]}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. COMPARATIVE — dua layer node + bridging edges
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build JSON untuk mode COMPARATIVE.
     *
     * Strategi layout:
     * - Layer kiri (x offset -400): node definisi (warna biru)
     * - Layer kanan (x offset +400): node aktual yg dilalui (warna oranye/hijau/merah)
     * - Bridge edges: garis abu-abu tipis dari definisi ke aktual node yang sama
     * - Edge aktual: garis oranye tebal, ketebalan ∝ traversalCount
     *
     * @param defNodes      definisi node dari AD_WF_Node
     * @param defEdges      definisi edges dari AD_WF_NodeNext
     * @param statsMap      statistik per node (totalExecutions, avgDuration, failCount)
     * @param traversalMap  traversal count per edge (key: "fromId_toId")
     * @param highlightBottleneck warnai merah node dengan avg durasi tertinggi
     */
    public String buildComparativeJson(List<WFNodeDTO> defNodes,
                                       List<WFEdgeDTO> defEdges,
                                       Map<Integer, WFNodeDTO> statsMap,
                                       Map<String, Integer> traversalMap,
                                       boolean highlightBottleneck) {
        double maxAvg = statsMap.values().stream()
            .mapToDouble(n -> n.avgDurationMinutes).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"nodes\":[");

        // — LAYER DEFINISI (offset x ke kiri) —
        boolean first = true;
        for (WFNodeDTO n : defNodes) {
            if (!first) sb.append(",");
            first = false;
            sb.append(String.format(
                "{\"id\":\"def_%d\",\"label\":\"%s\",\"group\":\"definition\"," +
                "\"color\":{\"background\":\"#1565C0\",\"border\":\"#0D47A1\"}," +
                "\"shape\":\"%s\",\"x\":%d,\"y\":%d," +
                "\"font\":{\"color\":\"#fff\"},\"title\":\"[DEFINISI] %s\"}",
                n.nodeId, escapeJson(n.name),
                resolveShape(n.action, n.nodeType),
                (n.xPosition * 3) - 500,
                n.yPosition * 3,
                escapeJson(n.name)
            ));
        }

        // — LAYER AKTUAL (node yang pernah dieksekusi) —
        for (WFNodeDTO stat : statsMap.values()) {
            if (stat.totalExecutions == 0) continue; // skip node yg tdk pernah dilalui
            if (!first) sb.append(",");
            first = false;
            String color = stat.resolveColor(highlightBottleneck, maxAvg);
            String tooltip = String.format("Eksekusi: %d | Avg Durasi: %.1f mnt | Gagal: %d",
                stat.totalExecutions, stat.avgDurationMinutes, stat.failCount);
            sb.append(String.format(
                "{\"id\":\"act_%d\",\"label\":\"%s\",\"group\":\"actual\"," +
                "\"color\":{\"background\":\"%s\",\"border\":\"#E65100\"}," +
                "\"shape\":\"%s\",\"x\":%d,\"y\":%d," +
                "\"font\":{\"color\":\"#fff\"},\"title\":\"%s\"," +
                "\"value\":%d}",
                stat.nodeId, escapeJson(stat.name),
                color,
                resolveShape(stat.action, stat.nodeType),
                (stat.xPosition * 3) + 500,
                stat.yPosition * 3,
                escapeJson(tooltip),
                Math.max(1, stat.totalExecutions) // vis scaling
            ));
        }

        sb.append("],\"edges\":[");
        first = true;

        // — EDGES DEFINISI (biru tipis) —
        for (WFEdgeDTO e : defEdges) {
            if (!first) sb.append(",");
            first = false;
            sb.append(String.format(
                "{\"id\":\"def_e_%d\",\"from\":\"def_%d\",\"to\":\"def_%d\"," +
                "\"color\":{\"color\":\"#42A5F5\"},\"arrows\":\"to\"," +
                "\"width\":1,\"dashes\":true,\"label\":\"%s\"}",
                e.edgeId, e.fromNodeId, e.toNodeId,
                escapeJson(nvl(e.transitionCode))
            ));
        }

        // — BRIDGE EDGES (abu-abu, dari def ke actual untuk node yg sama) —
        for (WFNodeDTO stat : statsMap.values()) {
            if (stat.totalExecutions == 0) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append(String.format(
                "{\"id\":\"bridge_%d\",\"from\":\"def_%d\",\"to\":\"act_%d\"," +
                "\"color\":{\"color\":\"#BDBDBD\"},\"dashes\":[5,10]," +
                "\"width\":1,\"arrows\":\"\",\"smooth\":{\"type\":\"curvedCW\",\"roundness\":0.3}}",
                stat.nodeId, stat.nodeId, stat.nodeId
            ));
        }

        // — EDGES AKTUAL (oranye, tebal ∝ traversalCount) —
        int bridgeEdgeId = 90000;
        for (Map.Entry<String, Integer> entry : traversalMap.entrySet()) {
            String[] parts = entry.getKey().split("_");
            if (parts.length != 2) continue;
            int fromId = Integer.parseInt(parts[0]);
            int toId   = Integer.parseInt(parts[1]);
            int count  = entry.getValue();
            int width  = Math.min(10, Math.max(1, count / 5 + 1));
            if (!first) sb.append(",");
            first = false;
            sb.append(String.format(
                "{\"id\":\"act_e_%d\",\"from\":\"act_%d\",\"to\":\"act_%d\"," +
                "\"color\":{\"color\":\"#FF6F00\"},\"arrows\":\"to\"," +
                "\"width\":%d,\"title\":\"Dilalui %d kali\",\"label\":\"%d×\"}",
                bridgeEdgeId++, fromId, toId, width, count, count
            ));
        }

        sb.append("]}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. TIMELINE — vis-timeline items & groups
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build JSON untuk mode TIMELINE (vis.js Timeline).
     *
     * Groups = instance process.
     * Items   = aktivitas per node.
     */
    public String buildTimelineJson(List<WFProcessDTO> processes,
                                    List<WFActivityDTO> activities) {
        StringBuilder sb = new StringBuilder();

        // Groups
        sb.append("{\"groups\":[");
        boolean first = true;
        for (WFProcessDTO p : processes) {
            if (!first) sb.append(",");
            first = false;
            String label = "Process #" + p.processId
                + (p.documentNo != null ? " [" + p.documentNo + "]" : "");
            sb.append(String.format(
                "{\"id\":%d,\"content\":\"%s\",\"style\":\"color:%s\"}",
                p.processId, escapeJson(label), p.resolveColor()
            ));
        }
        sb.append("],\"items\":[");

        // Items
        first = true;
        int itemId = 1;
        for (WFActivityDTO a : activities) {
            if (a.created == null) continue;
            if (!first) sb.append(",");
            first = false;
            String end = a.updated != null ? "\"end\":\"" + SDF.format(a.updated) + "\"," : "";
            String tooltip = escapeJson(
                a.nodeName + " | " + nvl(a.userName) + " | " +
                String.format("%.1f mnt", a.getDurationMinutes()) +
                (a.textMsg != null && !a.textMsg.isEmpty() ? " | " + a.textMsg : "")
            );
            sb.append(String.format(
                "{\"id\":%d,\"group\":%d,\"content\":\"%s\",\"start\":\"%s\",%s" +
                "\"style\":\"background-color:%s;border-color:%s\",\"title\":\"%s\"}",
                itemId++, a.processId,
                escapeJson(a.nodeName),
                SDF.format(a.created),
                end,
                a.resolveColor(), a.resolveColor(),
                tooltip
            ));
        }
        sb.append("]}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. STAT — Chart.js data (bar/pie/line)
    // ─────────────────────────────────────────────────────────────────────────

    public enum ChartType { BAR, PIE, LINE }
    public enum GroupBy   { NODE, USER, DATE }

    /**
     * Build JSON untuk mode STAT (Chart.js).
     *
     * @param statsMap  statistik per node
     * @param chartType BAR / PIE / LINE
     * @param groupBy   NODE / USER / DATE
     */
    public String buildStatJson(Map<Integer, WFNodeDTO> statsMap,
                                ChartType chartType,
                                GroupBy groupBy) {
        List<String> labels    = new ArrayList<>();
        List<Integer> execData = new ArrayList<>();
        List<Double>  durData  = new ArrayList<>();
        List<Integer> failData = new ArrayList<>();
        List<String>  colors   = new ArrayList<>();

        statsMap.values().stream()
            .filter(n -> n.totalExecutions > 0)
            .sorted(Comparator.comparingInt((WFNodeDTO n) -> n.totalExecutions).reversed())
            .forEach(n -> {
                labels.add(n.name != null ? n.name : "Node#" + n.nodeId);
                execData.add(n.totalExecutions);
                durData.add(Math.round(n.avgDurationMinutes * 10.0) / 10.0);
                failData.add(n.failCount);
                colors.add(n.resolveColor(false, 0));
            });

        String type = chartType == ChartType.PIE ? "pie"
                    : chartType == ChartType.LINE ? "line"
                    : "bar";

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type).append("\",");
        sb.append("\"data\":{");
        sb.append("\"labels\":").append(toJsonStringArray(labels)).append(",");
        sb.append("\"datasets\":[");

        // Dataset 1: Total eksekusi
        sb.append("{\"label\":\"Total Eksekusi\",");
        sb.append("\"data\":").append(toJsonIntArray(execData)).append(",");
        sb.append("\"backgroundColor\":").append(toJsonStringArray(colors)).append(",");
        sb.append("\"borderColor\":\"#1565C0\",\"borderWidth\":2},");

        // Dataset 2: Avg Durasi (menit) — hanya untuk BAR & LINE
        if (chartType != ChartType.PIE) {
            sb.append("{\"label\":\"Avg Durasi (mnt)\",");
            sb.append("\"data\":").append(toJsonDoubleArray(durData)).append(",");
            sb.append("\"backgroundColor\":\"rgba(255,111,0,0.4)\",");
            sb.append("\"borderColor\":\"#FF6F00\",\"borderWidth\":2,\"yAxisID\":\"y2\"},");

            // Dataset 3: Fail count
            sb.append("{\"label\":\"Gagal\",");
            sb.append("\"data\":").append(toJsonIntArray(failData)).append(",");
            sb.append("\"backgroundColor\":\"rgba(239,83,80,0.5)\",");
            sb.append("\"borderColor\":\"#EF5350\",\"borderWidth\":2,\"yAxisID\":\"y1\"}");
        } else {
            // PIE: tutup setelah dataset pertama
            sb.setLength(sb.length() - 1); // hapus trailing comma
        }

        sb.append("]},");
        sb.append("\"options\":{\"responsive\":true,\"plugins\":{\"legend\":{\"position\":\"bottom\"}},");
        if (chartType != ChartType.PIE) {
            sb.append("\"scales\":{\"y1\":{\"type\":\"linear\",\"position\":\"left\",\"title\":{\"display\":true,\"text\":\"Jumlah\"}},");
            sb.append("\"y2\":{\"type\":\"linear\",\"position\":\"right\",\"title\":{\"display\":true,\"text\":\"Menit\"},\"grid\":{\"drawOnChartArea\":false}}}");
        }
        sb.append("}}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String resolveShape(String action, String nodeType) {
        if ("Start".equals(nodeType)) return "circle";
        if ("End".equals(nodeType))   return "circle";
        if ("U".equals(action))       return "diamond";
        if ("S".equals(action))       return "box";
        if ("W".equals(action))       return "ellipse";
        return "box";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String nvl(String s) { return s == null ? "" : s; }

    private String toJsonStringArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonIntArray(List<Integer> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonDoubleArray(List<Double> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
