package org.nsoft.wf.visualizer.model;

/**
 * DTO untuk satu node dalam definisi workflow (AD_WF_Node).
 */
public class WFNodeDTO {

    private int     adWFNodeID;
    private String  name;
    private String  action;      // S=Start, F=Finish, C=UserChoice, X=SubWorkflow, W=WaitSleep, A=AppsProcess, dll
    private int     xPosition;
    private int     yPosition;

    public WFNodeDTO(int adWFNodeID, String name, String action, int xPosition, int yPosition) {
        this.adWFNodeID = adWFNodeID;
        this.name       = name;
        this.action     = action;
        this.xPosition  = xPosition;
        this.yPosition  = yPosition;
    }

    public int    getAdWFNodeID() { return adWFNodeID; }
    public String getName()       { return name; }
    public String getAction()     { return action; }
    public int    getXPosition()  { return xPosition; }
    public int    getYPosition()  { return yPosition; }
}
