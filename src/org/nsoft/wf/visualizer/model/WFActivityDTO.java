package org.nsoft.wf.visualizer.model;

import java.sql.Timestamp;

/**
 * DTO untuk satu baris AD_WF_Activity — aktivitas aktual per node per process instance.
 * Digunakan oleh Comparative Graph untuk overlay "actual" di atas layer definisi.
 */
public class WFActivityDTO {

    private int       adWFActivityID;
    private int       adWFProcessID;
    private int       adWFNodeID;
    private String    wfState;        // OS=Open/Suspended, CO=Completed, FA=Failed, WI=Waiting, SU=Suspended
    private Timestamp created;
    private Timestamp endWaitTime;
    private String    textMsg;
    private String    userName;       // dari join ke AD_User

    public WFActivityDTO(int adWFActivityID, int adWFProcessID, int adWFNodeID,
                         String wfState, Timestamp created, Timestamp endWaitTime,
                         String textMsg, String userName) {
        this.adWFActivityID = adWFActivityID;
        this.adWFProcessID  = adWFProcessID;
        this.adWFNodeID     = adWFNodeID;
        this.wfState        = wfState;
        this.created        = created;
        this.endWaitTime    = endWaitTime;
        this.textMsg        = textMsg;
        this.userName       = userName;
    }

    public int       getAdWFActivityID() { return adWFActivityID; }
    public int       getAdWFProcessID()  { return adWFProcessID; }
    public int       getAdWFNodeID()     { return adWFNodeID; }
    public String    getWfState()        { return wfState; }
    public Timestamp getCreated()        { return created; }
    public Timestamp getEndWaitTime()    { return endWaitTime; }
    public String    getTextMsg()        { return textMsg; }
    public String    getUserName()       { return userName; }

    /**
     * Hitung durasi dalam menit antara created dan endWaitTime.
     * Jika endWaitTime null (masih running), gunakan waktu sekarang.
     */
    public long getDurationMinutes() {
        Timestamp end = (endWaitTime != null) ? endWaitTime
                : new Timestamp(System.currentTimeMillis());
        return (end.getTime() - created.getTime()) / 60_000L;
    }

    /**
     * Apakah node ini sedang aktif (belum selesai)?
     */
    public boolean isRunning() {
        return "OS".equals(wfState) || "WI".equals(wfState) || "SU".equals(wfState);
    }

    /**
     * Apakah node ini gagal?
     */
    public boolean isFailed() {
        return "FA".equals(wfState);
    }

    /**
     * Apakah node ini sudah selesai?
     */
    public boolean isCompleted() {
        return "CO".equals(wfState);
    }
}
