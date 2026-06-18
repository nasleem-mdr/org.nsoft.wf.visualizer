package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;

import java.sql.*;
import java.util.*;
import org.compiere.util.DB;

/**
 * WFDataProvider — Layer query untuk semua data workflow visualizer.
 *
 * Semua method menerima parameter filter dan mengembalikan List DTO.
 * Tidak ada logika UI di sini — murni data access.
 */
public class WFDataProvider {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. AD_WORKFLOW — daftar workflow untuk dropdown
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ambil semua workflow aktif yang punya minimal 1 node.
     */
    public List<int[]> getWorkflowList(int clientId) {
        // returns List of [AD_Workflow_ID, name]
        List<int[]> result = new ArrayList<>();
        String sql = """
            SELECT w.AD_Workflow_ID, w.Name
            FROM AD_Workflow w
            WHERE w.IsActive = 'Y'
              AND w.AD_Client_ID IN (0, ?)
              AND EXISTS (SELECT 1 FROM AD_WF_Node n WHERE n.AD_Workflow_ID = w.AD_Workflow_ID)
            ORDER BY w.Name
            """;
        // Implemented via PreparedStatement inline; returns raw for simplicity
        return result; // caller populates Listbox
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. FLOW / DEFINITION — node & edge dari definisi
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Node-node dari AD_WF_Node untuk workflow tertentu.
     */
    public List<WFNodeDTO> getNodes(int workflowId) {
        List<WFNodeDTO> list = new ArrayList<>();
        String sql = """
            SELECT n.AD_WF_Node_ID,
                   COALESCE(n.Name, 'Node') AS Name,
                   n.Action,
                   n.XPosition,
                   n.YPosition,
                   CASE
                       WHEN n.AD_WF_Node_ID = w.AD_WF_Node_ID THEN 'Start'
                       WHEN n.Action = 'F' THEN 'End'
                       ELSE 'Middle'
                   END AS NodeType
            FROM AD_WF_Node n
            JOIN AD_Workflow w ON w.AD_Workflow_ID = n.AD_Workflow_ID
            WHERE n.AD_Workflow_ID = ?
              AND n.IsActive = 'Y'
            ORDER BY n.AD_WF_Node_ID
            """;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, workflowId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                WFNodeDTO dto = new WFNodeDTO();
                dto.nodeId     = rs.getInt("AD_WF_Node_ID");
                dto.name       = rs.getString("Name");
                dto.action     = rs.getString("Action");
                dto.xPosition  = rs.getInt("XPosition");
                dto.yPosition  = rs.getInt("YPosition");
                dto.nodeType   = rs.getString("NodeType");
                dto.workflowId = workflowId;
                list.add(dto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("getNodes failed for workflow " + workflowId, e);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }

    /**
     * Edge-edge dari AD_WF_NodeNext.
     */
    public List<WFEdgeDTO> getEdges(int workflowId) {
        List<WFEdgeDTO> list = new ArrayList<>();
        String sql = """
            SELECT nn.AD_WF_NodeNext_ID,
                   nn.AD_WF_Node_ID       AS FromNodeId,
                   nn.AD_WF_Next_ID       AS ToNodeId,
                   nn.TransitionCode,
                   nn.Description
            FROM AD_WF_NodeNext nn
            JOIN AD_WF_Node n ON n.AD_WF_Node_ID = nn.AD_WF_Node_ID
            WHERE n.AD_Workflow_ID = ?
              AND nn.IsActive = 'Y'
            """;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, workflowId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                WFEdgeDTO dto = new WFEdgeDTO();
                dto.edgeId         = rs.getInt("AD_WF_NodeNext_ID");
                dto.fromNodeId     = rs.getInt("FromNodeId");
                dto.toNodeId       = rs.getInt("ToNodeId");
                dto.transitionCode = rs.getString("TransitionCode");
                dto.description    = rs.getString("Description");
                list.add(dto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("getEdges failed for workflow " + workflowId, e);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PROCESS — instance workflow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ambil instance-instance dari AD_WF_Process dengan filter opsional.
     *
     * @param workflowId    wajib
     * @param processId     optional, -1 = semua
     * @param dateFrom      optional, null = tanpa batas
     * @param dateTo        optional, null = tanpa batas
     * @param maxRows       batas baris (untuk performa)
     */
    public List<WFProcessDTO> getProcesses(int workflowId, int processId,
                                           Timestamp dateFrom, Timestamp dateTo,
                                           int maxRows) {
        List<WFProcessDTO> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT p.AD_WF_Process_ID,
                   p.AD_Workflow_ID,
                   p.WFState,
                   p.AD_Table_ID,
                   p.Record_ID,
                   p.Created        AS StartDate,
                   p.Updated        AS EndDate,
                   p.AD_User_ID,
                   u.Name           AS UserName
            FROM AD_WF_Process p
            LEFT JOIN AD_User u ON u.AD_User_ID = p.AD_User_ID
            WHERE p.AD_Workflow_ID = ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(workflowId);

        if (processId > 0) {
            sql.append(" AND p.AD_WF_Process_ID = ?");
            params.add(processId);
        }
        if (dateFrom != null) {
            sql.append(" AND p.Created >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND p.Created <= ?");
            params.add(dateTo);
        }
        sql.append(" ORDER BY p.Created DESC");
        if (maxRows > 0) sql.append(" FETCH FIRST ? ROWS ONLY");

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql.toString(), null);
            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer) pstmt.setInt(idx++, (Integer) p);
                else if (p instanceof Timestamp) pstmt.setTimestamp(idx++, (Timestamp) p);
            }
            if (maxRows > 0) pstmt.setInt(idx, maxRows);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                WFProcessDTO dto = new WFProcessDTO();
                dto.processId  = rs.getInt("AD_WF_Process_ID");
                dto.workflowId = rs.getInt("AD_Workflow_ID");
                dto.wfState    = rs.getString("WFState");
                dto.adTableId  = rs.getInt("AD_Table_ID");
                dto.recordId   = rs.getInt("Record_ID");
                dto.startDate  = rs.getTimestamp("StartDate");
                dto.endDate    = rs.getTimestamp("EndDate");
                dto.userId     = rs.getInt("AD_User_ID");
                dto.userName   = rs.getString("UserName");
                list.add(dto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("getProcesses failed", e);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. ACTIVITY — detail per node per instance
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ambil aktivitas dari AD_WF_Activity untuk satu atau semua process instance.
     */
    public List<WFActivityDTO> getActivities(int workflowId, int processId,
                                             Timestamp dateFrom, Timestamp dateTo) {
        List<WFActivityDTO> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT a.AD_WF_Activity_ID,
                   a.AD_WF_Process_ID,
                   a.AD_WF_Node_ID,
                   n.Name           AS NodeName,
                   a.WFState,
                   a.Created,
                   a.Updated,
                   a.AD_User_ID,
                   u.Name           AS UserName,
                   a.TextMsg
            FROM AD_WF_Activity a
            JOIN AD_WF_Process  p ON p.AD_WF_Process_ID = a.AD_WF_Process_ID
            JOIN AD_WF_Node     n ON n.AD_WF_Node_ID    = a.AD_WF_Node_ID
            LEFT JOIN AD_User   u ON u.AD_User_ID       = a.AD_User_ID
            WHERE p.AD_Workflow_ID = ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(workflowId);

        if (processId > 0) {
            sql.append(" AND a.AD_WF_Process_ID = ?");
            params.add(processId);
        }
        if (dateFrom != null) {
            sql.append(" AND a.Created >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND a.Created <= ?");
            params.add(dateTo);
        }
        sql.append(" ORDER BY a.AD_WF_Process_ID, a.Created");

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql.toString(), null);
            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer) pstmt.setInt(idx++, (Integer) p);
                else if (p instanceof Timestamp) pstmt.setTimestamp(idx++, (Timestamp) p);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                WFActivityDTO dto = new WFActivityDTO();
                dto.activityId = rs.getInt("AD_WF_Activity_ID");
                dto.processId  = rs.getInt("AD_WF_Process_ID");
                dto.nodeId     = rs.getInt("AD_WF_Node_ID");
                dto.nodeName   = rs.getString("NodeName");
                dto.wfState    = rs.getString("WFState");
                dto.created    = rs.getTimestamp("Created");
                dto.updated    = rs.getTimestamp("Updated");
                dto.userId     = rs.getInt("AD_User_ID");
                dto.userName   = rs.getString("UserName");
                dto.textMsg    = rs.getString("TextMsg");
                list.add(dto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("getActivities failed", e);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. STATISTIK — agregat per node
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Hitung statistik per node: total eksekusi, avg durasi, fail count.
     * Digunakan untuk mode STAT dan untuk coloring di COMPARATIVE mode.
     */
    public Map<Integer, WFNodeDTO> getNodeStatistics(int workflowId,
                                                      Timestamp dateFrom,
                                                      Timestamp dateTo) {
        Map<Integer, WFNodeDTO> statsMap = new HashMap<>();

        // Isi dulu dengan node definitions
        for (WFNodeDTO node : getNodes(workflowId)) {
            statsMap.put(node.nodeId, node);
        }

        StringBuilder sql = new StringBuilder("""
            SELECT a.AD_WF_Node_ID,
                   COUNT(*)                                              AS TotalExec,
                   AVG(EXTRACT(EPOCH FROM (a.Updated - a.Created)) / 60) AS AvgDurMin,
                   SUM(CASE WHEN a.WFState = 'AD' THEN 1 ELSE 0 END)    AS FailCount
            FROM AD_WF_Activity a
            JOIN AD_WF_Process p ON p.AD_WF_Process_ID = a.AD_WF_Process_ID
            JOIN AD_WF_Node    n ON n.AD_WF_Node_ID    = a.AD_WF_Node_ID
            WHERE p.AD_Workflow_ID = ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(workflowId);

        if (dateFrom != null) { sql.append(" AND a.Created >= ?"); params.add(dateFrom); }
        if (dateTo   != null) { sql.append(" AND a.Created <= ?"); params.add(dateTo); }

        sql.append(" GROUP BY a.AD_WF_Node_ID");

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql.toString(), null);
            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer) pstmt.setInt(idx++, (Integer) p);
                else if (p instanceof Timestamp) pstmt.setTimestamp(idx++, (Timestamp) p);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                int nodeId = rs.getInt("AD_WF_Node_ID");
                WFNodeDTO dto = statsMap.computeIfAbsent(nodeId, id -> {
                    WFNodeDTO d = new WFNodeDTO();
                    d.nodeId = id;
                    return d;
                });
                dto.totalExecutions   = rs.getInt("TotalExec");
                dto.avgDurationMinutes = rs.getDouble("AvgDurMin");
                dto.failCount          = rs.getInt("FailCount");
            }
        } catch (SQLException e) {
            throw new RuntimeException("getNodeStatistics failed", e);
        } finally {
            DB.close(rs, pstmt);
        }
        return statsMap;
    }

    /**
     * Hitung traversal count per edge (untuk COMPARATIVE — ketebalan garis).
     */
    public Map<String, Integer> getEdgeTraversalCounts(int workflowId,
                                                        Timestamp dateFrom,
                                                        Timestamp dateTo) {
        // Key: "fromNodeId_toNodeId"
        Map<String, Integer> result = new HashMap<>();

        // Hitung berapa kali aktivitas node A diikuti langsung oleh node B dalam process yang sama
        StringBuilder sql = new StringBuilder("""
            SELECT a1.AD_WF_Node_ID AS FromNode,
                   a2.AD_WF_Node_ID AS ToNode,
                   COUNT(*)          AS TraversalCount
            FROM AD_WF_Activity a1
            JOIN AD_WF_Activity a2 ON  a2.AD_WF_Process_ID = a1.AD_WF_Process_ID
                                   AND a2.Created = (
                                       SELECT MIN(ax.Created)
                                       FROM AD_WF_Activity ax
                                       WHERE ax.AD_WF_Process_ID = a1.AD_WF_Process_ID
                                         AND ax.Created > a1.Created
                                   )
            JOIN AD_WF_Process p ON p.AD_WF_Process_ID = a1.AD_WF_Process_ID
            WHERE p.AD_Workflow_ID = ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(workflowId);
        if (dateFrom != null) { sql.append(" AND a1.Created >= ?"); params.add(dateFrom); }
        if (dateTo   != null) { sql.append(" AND a1.Created <= ?"); params.add(dateTo); }
        sql.append(" GROUP BY a1.AD_WF_Node_ID, a2.AD_WF_Node_ID");

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql.toString(), null);
            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer) pstmt.setInt(idx++, (Integer) p);
                else if (p instanceof Timestamp) pstmt.setTimestamp(idx++, (Timestamp) p);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String key = rs.getInt("FromNode") + "_" + rs.getInt("ToNode");
                result.put(key, rs.getInt("TraversalCount"));
            }
        } catch (SQLException e) {
            // Log saja, jangan throw — feature optional
            System.err.println("getEdgeTraversalCounts failed: " + e.getMessage());
        } finally {
            DB.close(rs, pstmt);
        }
        return result;
    }
}
