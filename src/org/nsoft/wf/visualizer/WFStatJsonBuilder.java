package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;

import java.util.*;

/**
 * Membangun payload JSON untuk mode STAT — Statistical Charts.
 *
 * Menghasilkan 3 dataset sekaligus, dirender client-side via Chart.js:
 *   1. Bar chart   — avg durasi per node (bottleneck analysis)
 *   2. Pie chart   — distribusi status process (CC/IP/AD)
 *   3. Bar chart   — jumlah instance per minggu/bulan (throughput trend)
 *
 * Output JSON:
 * <pre>
 * {
 *   "durationPerNode": [
 *     { "nodeId": 1001, "nodeName": "Approve Manager", "avgMin": 142, "count": 5, "isBottleneck": true }
 *   ],
 *   "statusDist": [
 *     { "label": "Selesai",  "value": 12, "color": "#66BB6A" },
 *     { "label": "Berjalan", "value": 3,  "color": "#FFA726" },
 *     { "label": "Aborted",  "value": 1,  "color": "#EF5350" }
 *   ],
 *   "throughput": [
 *     { "period": "2025-W01", "count": 4 },
 *     { "period": "2025-W02", "count": 7 }
 *   ],
 *   "summary": {
 *     "totalProcess": 16,
 *     "completedCount": 12,
 *     "runningCount": 3,
 *     "abortedCount": 1,
 *     "avgTotalDurationMin": 320,
 *     "bottleneckNodeName": "Approve Manager"
 *   }
 * }
 * </pre>
 */
public class WFStatJsonBuilder {

    /**
     * Bangun JSON statistik dari list process + aktivitas.
     *
     * @param processes  list WFProcessDTO (sudah berisi aktivitas)
     * @return JSON string
     */
    public String build(List<WFProcessDTO> processes) {

        // ── 1. Hitung per-node: totalDuration + count + nodeName ─────────
        // Pakai LinkedHashMap agar urutan node konsisten
        Map<Integer, long[]>  durationMap  = new LinkedHashMap<>(); // nodeID → [sumMin, count]
        Map<Integer, String>  nodeNameMap  = new LinkedHashMap<>(); // nodeID → nodeName

        // ── 2. Status distribution ─────────────────────────────────────────
        int countCC = 0, countIP = 0, countAD = 0, countOther = 0;

        // ── 3. Throughput: process created per minggu ──────────────────────
        // key: "yyyy-Www"
        Map<String, Integer> throughputMap = new TreeMap<>();

        // ── 4. Total durasi per process (end-to-end) ──────────────────────
        long sumTotalDurMin = 0;
        int  processWithDur = 0;

        for (WFProcessDTO proc : processes) {
            // Status distribusi
            String ws = proc.getWfState();
            if      ("CC".equals(ws)) countCC++;
            else if ("IP".equals(ws)) countIP++;
            else if ("AD".equals(ws)) countAD++;
            else                      countOther++;

            // Throughput (berdasarkan tanggal created process)
            if (proc.getCreated() != null) {
                String week = toWeekKey(proc.getCreated());
                throughputMap.merge(week, 1, Integer::sum);
            }

            // Per-node stats + end-to-end durasi
            long processMinStart = Long.MAX_VALUE;
            long processMaxEnd   = Long.MIN_VALUE;

            for (WFActivityDTO act : proc.getActivities()) {
                int    nid = act.nodeId;
                String nm  = act.nodeName != null ? act.nodeName : "Node " + nid;
                nodeNameMap.putIfAbsent(nid, nm);

                long dur = (long) act.getDurationMinutes();
                long[] stat = durationMap.computeIfAbsent(nid, k -> new long[]{0L, 0L});
                stat[0] += dur;
                stat[1]++;

                // Untuk end-to-end
                if (act.created != null && act.created.getTime() < processMinStart)
                    processMinStart = act.created.getTime();
                if (act.updated != null && act.updated.getTime() > processMaxEnd)
                    processMaxEnd = act.updated.getTime();
            }
            if (processMinStart < Long.MAX_VALUE && processMaxEnd > Long.MIN_VALUE && processMaxEnd > processMinStart) {
                sumTotalDurMin += (processMaxEnd - processMinStart) / 60_000L;
                processWithDur++;
            }
        }

        // ── Cari bottleneck ────────────────────────────────────────────────
        int    bottleneckNodeID   = -1;
        String bottleneckNodeName = "-";
        double maxAvg             = 0;
        for (Map.Entry<Integer, long[]> e : durationMap.entrySet()) {
            long[] s = e.getValue();
            if (s[1] == 0) continue;
            double avg = (double) s[0] / s[1];
            if (avg > maxAvg) {
                maxAvg           = avg;
                bottleneckNodeID = e.getKey();
                bottleneckNodeName = nodeNameMap.getOrDefault(bottleneckNodeID, "-");
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // Build JSON
        // ─────────────────────────────────────────────────────────────────
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // ── durationPerNode ──
        sb.append("\"durationPerNode\":[");
        List<Map.Entry<Integer, long[]>> durEntries = new ArrayList<>(durationMap.entrySet());
        // Sort descending by avg untuk langsung tampil bottleneck di kiri bar chart
        durEntries.sort((a, b) -> {
            double avgA = a.getValue()[1] > 0 ? (double) a.getValue()[0] / a.getValue()[1] : 0;
            double avgB = b.getValue()[1] > 0 ? (double) b.getValue()[0] / b.getValue()[1] : 0;
            return Double.compare(avgB, avgA);
        });
        for (int i = 0; i < durEntries.size(); i++) {
            Map.Entry<Integer, long[]> e = durEntries.get(i);
            int    nid  = e.getKey();
            long[] s    = e.getValue();
            long   avg  = s[1] > 0 ? s[0] / s[1] : 0;
            boolean isB = nid == bottleneckNodeID;

            sb.append("{");
            sb.append("\"nodeId\":").append(nid).append(",");
            appendKV(sb, "nodeName", nodeNameMap.getOrDefault(nid, "Node " + nid)); sb.append(",");
            sb.append("\"avgMin\":").append(avg).append(",");
            sb.append("\"count\":").append(s[1]).append(",");
            sb.append("\"isBottleneck\":").append(isB);
            sb.append("}");
            if (i < durEntries.size() - 1) sb.append(",");
        }
        sb.append("],");

        // ── statusDist ──
        sb.append("\"statusDist\":[");
        appendStatItem(sb, "Selesai",   countCC,    "#66BB6A"); sb.append(",");
        appendStatItem(sb, "Berjalan",  countIP,    "#FFA726"); sb.append(",");
        appendStatItem(sb, "Aborted",   countAD,    "#EF5350");
        if (countOther > 0) {
            sb.append(",");
            appendStatItem(sb, "Lainnya", countOther, "#90A4AE");
        }
        sb.append("],");

        // ── throughput ──
        sb.append("\"throughput\":[");
        List<Map.Entry<String, Integer>> tpList = new ArrayList<>(throughputMap.entrySet());
        for (int i = 0; i < tpList.size(); i++) {
            sb.append("{");
            appendKV(sb, "period", tpList.get(i).getKey()); sb.append(",");
            sb.append("\"count\":").append(tpList.get(i).getValue());
            sb.append("}");
            if (i < tpList.size() - 1) sb.append(",");
        }
        sb.append("],");

        // ── summary ──
        long avgTotal = processWithDur > 0 ? sumTotalDurMin / processWithDur : 0;
        sb.append("\"summary\":{");
        sb.append("\"totalProcess\":").append(processes.size()).append(",");
        sb.append("\"completedCount\":").append(countCC).append(",");
        sb.append("\"runningCount\":").append(countIP).append(",");
        sb.append("\"abortedCount\":").append(countAD).append(",");
        sb.append("\"avgTotalDurationMin\":").append(avgTotal).append(",");
        appendKV(sb, "bottleneckNodeName", bottleneckNodeName);
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void appendStatItem(StringBuilder sb, String label, int value, String color) {
        sb.append("{");
        appendKV(sb, "label", label); sb.append(",");
        sb.append("\"value\":").append(value).append(",");
        appendKV(sb, "color", color);
        sb.append("}");
    }

    /**
     * Format Timestamp ke "yyyy-Www" (ISO week).
     * Contoh: 2025-01-06 → "2025-W02"
     */
    private String toWeekKey(java.sql.Timestamp ts) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(ts);
        int year = cal.getWeekYear();
        int week = cal.get(java.util.Calendar.WEEK_OF_YEAR);
        return String.format("%d-W%02d", year, week);
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
}
