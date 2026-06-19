package org.nsoft.wf.visualizer.model;

/**
 * DTO untuk transisi antar node (AD_WF_NodeNext).
 */
public class WFEdgeDTO {

    private int    adWFNodeNextID;
    private int    adWFNodeID;       // source
    private int    adWFNextID;       // target
    private String description;

    public WFEdgeDTO(int adWFNodeNextID, int adWFNodeID, int adWFNextID, String description) {
        this.adWFNodeNextID = adWFNodeNextID;
        this.adWFNodeID     = adWFNodeID;
        this.adWFNextID     = adWFNextID;
        this.description    = description;
    }

    public int    getAdWFNodeNextID() { return adWFNodeNextID; }
    public int    getAdWFNodeID()     { return adWFNodeID; }
    public int    getAdWFNextID()     { return adWFNextID; }
    public String getDescription()    { return description; }
}
