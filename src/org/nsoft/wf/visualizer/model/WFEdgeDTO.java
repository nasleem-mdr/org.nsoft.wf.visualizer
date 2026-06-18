package org.nsoft.wf.visualizer.model;

/**
 * WFEdgeDTO — transisi antar node dari AD_WF_NodeNext.
 */
public class WFEdgeDTO {
    public int    edgeId;
    public int    fromNodeId;
    public int    toNodeId;
    public String transitionCode; // AD_WF_NodeNext.TransitionCode (optional)
    public String description;

    // Untuk mode COMPARATIVE: apakah transisi ini benar-benar dilalui?
    public boolean traversedInActual = false;
    public int     traversalCount    = 0;
}
