package org.nsoft.wf.visualizer.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO untuk satu instance workflow (AD_WF_Process).
 * Berisi daftar aktivitas (AD_WF_Activity) yang terjadi pada instance ini.
 */
public class WFProcessDTO {

    private int                  adWFProcessID;
    private int                  adWorkflowID;
    private String               wfState;        // status process keseluruhan
    private Timestamp            created;
    private String               documentNo;     // nomor dokumen yang sedang diproses
    private String               tableName;
    private int                  recordID;
    private List<WFActivityDTO>  activities = new ArrayList<>();

    public WFProcessDTO(int adWFProcessID, int adWorkflowID, String wfState,
                        Timestamp created, String documentNo, String tableName, int recordID) {
        this.adWFProcessID = adWFProcessID;
        this.adWorkflowID  = adWorkflowID;
        this.wfState       = wfState;
        this.created       = created;
        this.documentNo    = documentNo;
        this.tableName     = tableName;
        this.recordID      = recordID;
    }

    public int                  getAdWFProcessID() { return adWFProcessID; }
    public int                  getAdWFWorkflowID(){ return adWorkflowID; }
    public String               getWfState()       { return wfState; }
    public Timestamp            getCreated()       { return created; }
    public String               getDocumentNo()    { return documentNo; }
    public String               getTableName()     { return tableName; }
    public int                  getRecordID()      { return recordID; }
    public List<WFActivityDTO>  getActivities()    { return activities; }

    public void addActivity(WFActivityDTO a) {
        activities.add(a);
    }

    /**
     * Kembalikan set node ID yang sudah dilalui dalam proses ini.
     */
    public java.util.Set<Integer> getVisitedNodeIDs() {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (WFActivityDTO a : activities) {
            ids.add(a.getAdWFNodeID());
        }
        return ids;
    }
}
