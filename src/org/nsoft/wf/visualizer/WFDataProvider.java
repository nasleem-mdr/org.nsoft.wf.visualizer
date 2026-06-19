package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;
import org.compiere.util.DB;

import java.sql.*;
import java.util.*;

/**
 * Query layer untuk org.nsoft.wf.visualizer.
 * Semua SQL ke tabel AD_WF_* dikumpulkan di sini.
 */
public class WFDataProvider {

    // ─────────────────────────────────────────────────────────────────────────
    // NODE & EDGE  (definisi workflow)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ambil semua node untuk workflow tertentu dari AD_WF_Node.
     *
     * @param adWorkflowID  ID workflow
     * @return list WFNodeDTO
     */
    public List<WFNodeDTO> getNodes(int adWorkflowID) {
        List<WFNodeDTO> list = new ArrayList<>();
        String sql =
            "SELECT AD_WF_Node_ID, Name, Action, XPosition, YPosition " +
            "  FROM AD_WF_Node " +
            " WHERE AD_Workflow_ID = ? AND IsActive = 'Y' " +
            " ORDER BY AD_WF_Node_ID";

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, adWorkflowID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new WFNodeDTO(
                    rs.getInt("AD_WF_Node_ID"),
                    rs.getString("Name"),
                    rs.getString("Action"),
                    rs.getInt("XPosition"),
                    rs.getInt("YPosition")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getNodes: " + e.getMessage(), e);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }

    /**
     * Ambil semua edge (transisi) untuk workflow tertentu dari AD_WF_NodeNext.
     *
     * @param adWorkflowID  ID workflow
     * @return list WFEdgeDTO
     */
    public List<WFEdgeDTO> getEdges(int adWorkflowID) {
        List<WFEdgeDTO> list = new ArrayList<>();
        String sql =
            "SELECT nn.AD_WF_NodeNext_ID, nn.AD_WF_Node_ID, nn.AD_WF_Next_ID, nn.Description " +
            "  FROM AD_WF_NodeNext nn " +
            "  JOIN AD_WF_Node n ON n.AD_WF_Node_ID = nn.AD_WF_Node_ID " +
            " WHERE n.AD_Workflow_ID = ? AND nn.IsActive = 'Y'";

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, adWorkflowID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new WFEdgeDTO(
                    rs.getInt("AD_WF_NodeNext_ID"),
                    rs.getInt("AD_WF_Node_ID"),
                    rs.getInt("AD_WF_Next_ID"),
                    rs.getString("Description")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getEdges: " + e.getMessage(), e);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROCESS & ACTIVITY  (eksekusi aktual)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ambil process instance untuk workflow + filter opsional.
     *
     * @param adWorkflowID      ID workflow
     * @param adWFProcessID     0 = semua instance
     * @param dateFrom          null = tidak difilter
     * @param dateTo            null = tidak difilter
     * @return list WFProcessDTO (belum berisi aktivitas)
     */
    public List<WFProcessDTO> getProcesses(int adWorkflowID, int adWFProcessID,
                                           Timestamp dateFrom, Timestamp dateTo) {
        List<WFProcessDTO> list = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
            "SELECT p.AD_WF_Process_ID, p.AD_Workflow_ID, p.WFState, p.Created, " +
            "       p.AD_Table_ID, p.Record_ID, " +
            "       COALESCE(c.DocumentNo, CAST(p.Record_ID AS VARCHAR)) AS DocumentNo, " +
            "       t.TableName " +
            "  FROM AD_WF_Process p " +
            "  JOIN AD_Table t ON t.AD_Table_ID = p.AD_Table_ID " +
            "  LEFT JOIN C_Order c ON c.C_Order_ID = p.Record_ID AND t.TableName = 'C_Order' " +
            " WHERE p.AD_Workflow_ID = ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(adWorkflowID);

        if (adWFProcessID > 0) {
            sql.append(" AND p.AD_WF_Process_ID = ? ");
            params.add(adWFProcessID);
        }
        if (dateFrom != null) {
            sql.append(" AND p.Created >= ? ");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND p.Created <= ? ");
            params.add(dateTo);
        }
        sql.append(" ORDER BY p.Created DESC FETCH FIRST 50 ROWS ONLY");

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql.toString(), null);
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer)   pstmt.setInt(i + 1, (Integer) p);
                else if (p instanceof Timestamp) pstmt.setTimestamp(i + 1, (Timestamp) p);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new WFProcessDTO(
                    rs.getInt("AD_WF_Process_ID"),
                    rs.getInt("AD_Workflow_ID"),
                    rs.getString("WFState"),
                    rs.getTimestamp("Created"),
                    rs.getString("DocumentNo"),
                    rs.getString("TableName"),
                    rs.getInt("Record_ID")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getProcesses: " + e.getMessage(), e);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }

    /**
     * Muat semua aktivitas untuk satu process dan masukkan ke dalam WFProcessDTO.
     *
     * @param process  DTO process yang akan diisi
     */
    public void loadActivities(WFProcessDTO process) {
        String sql =
            "SELECT a.AD_WF_Activity_ID, a.AD_WF_Process_ID, a.AD_WF_Node_ID, " +
            "       a.WFState, a.Created, a.EndWaitTime, a.TextMsg, " +
            "       COALESCE(u.Name, '') AS UserName " +
            "  FROM AD_WF_Activity a " +
            "  LEFT JOIN AD_User u ON u.AD_User_ID = a.AD_User_ID " +
            " WHERE a.AD_WF_Process_ID = ? " +
            " ORDER BY a.Created";

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, process.getAdWFProcessID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                process.addActivity(new WFActivityDTO(
                    rs.getInt("AD_WF_Activity_ID"),
                    rs.getInt("AD_WF_Process_ID"),
                    rs.getInt("AD_WF_Node_ID"),
                    rs.getString("WFState"),
                    rs.getTimestamp("Created"),
                    rs.getTimestamp("EndWaitTime"),
                    rs.getString("TextMsg"),
                    rs.getString("UserName")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loadActivities: " + e.getMessage(), e);
        } finally {
            DB.close(rs, pstmt);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WORKFLOW LIST  (untuk combobox pemilihan)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ambil daftar workflow yang aktif, terurut berdasarkan nama.
     *
     * @return map AD_Workflow_ID → Name
     */
    public Map<Integer, String> getWorkflowList() {
        Map<Integer, String> map = new LinkedHashMap<>();
        String sql =
            "SELECT AD_Workflow_ID, Name FROM AD_Workflow " +
            " WHERE IsActive = 'Y' ORDER BY Name";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                map.put(rs.getInt("AD_Workflow_ID"), rs.getString("Name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getWorkflowList: " + e.getMessage(), e);
        } finally {
            DB.close(rs, pstmt);
        }
        return map;
    }

    /**
     * Ambil daftar process instance untuk satu workflow (untuk combobox filter).
     *
     * @param adWorkflowID  ID workflow
     * @return map AD_WF_Process_ID → label (DocumentNo + state)
     */
    public Map<Integer, String> getProcessList(int adWorkflowID) {
        Map<Integer, String> map = new LinkedHashMap<>();
        String sql =
            "SELECT p.AD_WF_Process_ID, " +
            "       COALESCE(c.DocumentNo, CAST(p.Record_ID AS VARCHAR)) || ' [' || p.WFState || ']' AS Label " +
            "  FROM AD_WF_Process p " +
            "  LEFT JOIN C_Order c ON c.C_Order_ID = p.Record_ID " +
            " WHERE p.AD_Workflow_ID = ? " +
            " ORDER BY p.Created DESC FETCH FIRST 100 ROWS ONLY";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, adWorkflowID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                map.put(rs.getInt("AD_WF_Process_ID"), rs.getString("Label"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getProcessList: " + e.getMessage(), e);
        } finally {
            DB.close(rs, pstmt);
        }
        return map;
    }
}
