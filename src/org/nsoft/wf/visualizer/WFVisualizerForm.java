package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

import org.adempiere.webui.component.*;
import org.adempiere.webui.panel.ADForm;
import org.compiere.util.*;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.*;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.*;

/**
 * WFVisualizerForm — Main ZK Form untuk Workflow Visualizer.
 *
 * Layout:
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  NORTH: Toolbar parameter (Workflow, Mode, Filter)                  │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  CENTER: Visualization area (vis.js / Chart.js rendered via Html)   │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Semua rendering dilakukan client-side via injected JavaScript.
 * Java side hanya menyiapkan JSON data dan memanggil Clients.evalJavaScript().
 */
@org.adempiere.webui.annotation.Form(name = "org.nsoft.wf.visualizer.WFVisualizerForm")
public class WFVisualizerForm extends ADForm {

    private static final CLogger log = CLogger.getCLogger(WFVisualizerForm.class);

    // ── Data ──────────────────────────────────────────────────────────────────
    private final WFDataProvider   dataProvider = new WFDataProvider();
    private final WFVisJsonBuilder jsonBuilder  = new WFVisJsonBuilder();

    // ── Parameter state ───────────────────────────────────────────────────────
    private int      selectedWorkflowId = -1;
    private int      selectedProcessId  = -1;
    private WFVisMode visMode           = WFVisMode.FLOW;
    private WFVisJsonBuilder.ChartType chartType = WFVisJsonBuilder.ChartType.BAR;
    private Timestamp dateFrom          = null;
    private Timestamp dateTo            = null;
    private boolean  highlightBottleneck = true;
    private boolean  showDefinition     = true;
    private boolean  showActual         = true;

    // ── ZK Components ─────────────────────────────────────────────────────────
    private Listbox  lbWorkflow;
    private Listbox  lbProcess;
    private Listbox  lbMode;
    private Listbox  lbChartType;
    private Datebox  dbFrom;
    private Datebox  dbTo;
    private Checkbox cbHighlight;
    private Checkbox cbShowDef;
    private Checkbox cbShowActual;
    private Html     visContainer;     // tempat vis.js di-render
    private Label    lblStatus;

    // ── Canvas ID (unik per form instance) ───────────────────────────────────
    private final String canvasId = "wfvis_" + System.currentTimeMillis();

    @Override
    protected void doCreateLayoutCenterPanel(Component parent) {
        parent.setSclass("wfvis-form");

        Borderlayout layout = new Borderlayout();
        layout.setParent(parent);
        layout.setVflex("1");
        layout.setHflex("1");

        buildNorthPanel(layout);
        buildCenterPanel(layout);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NORTH PANEL — Parameter toolbar
    // ─────────────────────────────────────────────────────────────────────────

    private void buildNorthPanel(Borderlayout layout) {
        North north = new North();
        north.setParent(layout);
        north.setCollapsible(true);
        north.setBorder("normal");

        Vlayout vl = new Vlayout();
        vl.setParent(north);
        vl.setSclass("wfvis-north");

        // ── Row 1: Workflow + Mode + ChartType ────────────────────────────────
        Hlayout row1 = new Hlayout();
        row1.setParent(vl);
        row1.setSclass("wfvis-toolbar-row");

        // Workflow lookup
        new Label("Workflow:").setParent(row1);
        lbWorkflow = new Listbox();
        lbWorkflow.setParent(row1);
        lbWorkflow.setMold("select");
        lbWorkflow.setWidth("200px");
        populateWorkflowDropdown();
        lbWorkflow.addEventListener(Events.ON_SELECT, e -> onWorkflowSelected());

        // Mode
        new Label("Mode:").setParent(row1);
        lbMode = new Listbox();
        lbMode.setParent(row1);
        lbMode.setMold("select");
        lbMode.setWidth("160px");
        for (WFVisMode m : WFVisMode.values()) {
            Listitem li = new Listitem(m.getDisplayName(), m.name());
            li.setParent(lbMode);
        }
        lbMode.setSelectedIndex(0);
        lbMode.addEventListener(Events.ON_SELECT, e -> onModeChanged());

        // Chart type (hanya visible saat STAT)
        new Label("Chart:").setParent(row1);
        lbChartType = new Listbox();
        lbChartType.setParent(row1);
        lbChartType.setMold("select");
        lbChartType.setWidth("100px");
        lbChartType.setVisible(false);
        for (WFVisJsonBuilder.ChartType ct : WFVisJsonBuilder.ChartType.values()) {
            Listitem li = new Listitem(ct.name(), ct.name());
            li.setParent(lbChartType);
        }
        lbChartType.setSelectedIndex(0);
        lbChartType.addEventListener(Events.ON_SELECT, e -> {
            Listitem sel = lbChartType.getSelectedItem();
            if (sel != null) chartType = WFVisJsonBuilder.ChartType.valueOf((String) sel.getValue());
        });

        // ── Row 2: Process filter + Date range + Checkboxes ───────────────────
        Hlayout row2 = new Hlayout();
        row2.setParent(vl);
        row2.setSclass("wfvis-toolbar-row");

        new Label("Process:").setParent(row2);
        lbProcess = new Listbox();
        lbProcess.setParent(row2);
        lbProcess.setMold("select");
        lbProcess.setWidth("180px");
        lbProcess.appendChild(new Listitem("(Semua)", "-1"));
        lbProcess.setSelectedIndex(0);
        lbProcess.addEventListener(Events.ON_SELECT, e -> {
            Listitem sel = lbProcess.getSelectedItem();
            selectedProcessId = sel != null ? Integer.parseInt((String) sel.getValue()) : -1;
        });

        new Label("Dari:").setParent(row2);
        dbFrom = new Datebox();
        dbFrom.setParent(row2);
        dbFrom.setWidth("120px");
        dbFrom.setFormat("dd/MM/yyyy");

        new Label("s/d:").setParent(row2);
        dbTo = new Datebox();
        dbTo.setParent(row2);
        dbTo.setWidth("120px");
        dbTo.setFormat("dd/MM/yyyy");

        // Checkboxes
        cbHighlight = new Checkbox("Highlight Bottleneck");
        cbHighlight.setParent(row2);
        cbHighlight.setChecked(true);
        cbHighlight.addEventListener(Events.ON_CHECK, e ->
            highlightBottleneck = cbHighlight.isChecked());

        cbShowDef = new Checkbox("Definisi");
        cbShowDef.setParent(row2);
        cbShowDef.setChecked(true);
        cbShowDef.setVisible(false);
        cbShowDef.addEventListener(Events.ON_CHECK, e ->
            showDefinition = cbShowDef.isChecked());

        cbShowActual = new Checkbox("Aktual");
        cbShowActual.setParent(row2);
        cbShowActual.setChecked(true);
        cbShowActual.setVisible(false);
        cbShowActual.addEventListener(Events.ON_CHECK, e ->
            showActual = cbShowActual.isChecked());

        // ── Row 3: Action buttons ──────────────────────────────────────────────
        Hlayout row3 = new Hlayout();
        row3.setParent(vl);
        row3.setSclass("wfvis-toolbar-row");

        Button btnVisualize = new Button("Visualisasi");
        btnVisualize.setParent(row3);
        btnVisualize.setSclass("wfvis-btn-primary");
        btnVisualize.setImage("/images/zoom16.png");
        btnVisualize.addEventListener(Events.ON_CLICK, e -> doVisualize());

        Button btnExport = new Button("Export PNG");
        btnExport.setParent(row3);
        btnExport.setSclass("wfvis-btn-secondary");
        btnExport.addEventListener(Events.ON_CLICK, e ->
            Clients.evalJavaScript("wfVisExportPng('" + canvasId + "')"));

        Button btnFitView = new Button("Fit View");
        btnFitView.setParent(row3);
        btnFitView.addEventListener(Events.ON_CLICK, e ->
            Clients.evalJavaScript("wfVisFitView('" + canvasId + "')"));

        lblStatus = new Label("");
        lblStatus.setParent(row3);
        lblStatus.setSclass("wfvis-status");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CENTER PANEL — Visualization container
    // ─────────────────────────────────────────────────────────────────────────

    private void buildCenterPanel(Borderlayout layout) {
        Center center = new Center();
        center.setParent(layout);
        center.setBorder("none");
        center.setAutoscroll(true);

        // Html component sebagai container vis.js
        // Style diset via CSS class, bukan inline
        visContainer = new Html();
        visContainer.setParent(center);
        visContainer.setSclass("wfvis-container");
        visContainer.setContent(buildInitialHtml());

        // Inject CSS & library loader script
        Clients.evalJavaScript(buildLibraryLoaderScript());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENT HANDLERS
    // ─────────────────────────────────────────────────────────────────────────

    private void onWorkflowSelected() {
        Listitem sel = lbWorkflow.getSelectedItem();
        if (sel == null) return;
        selectedWorkflowId = (int) sel.getValue();
        selectedProcessId  = -1;
        populateProcessDropdown();
    }

    private void onModeChanged() {
        Listitem sel = lbMode.getSelectedItem();
        if (sel == null) return;
        visMode = WFVisMode.fromString((String) sel.getValue());

        boolean isComparative = visMode == WFVisMode.COMPARATIVE;
        boolean isStat        = visMode == WFVisMode.STAT;

        lbChartType.setVisible(isStat);
        cbShowDef.setVisible(isComparative);
        cbShowActual.setVisible(isComparative);
        cbHighlight.setVisible(isComparative || isStat);
    }

    /** Main visualize action — dispatch ke renderer yang sesuai */
    private void doVisualize() {
        if (selectedWorkflowId <= 0) {
            showStatus("⚠ Pilih workflow terlebih dahulu", "error");
            return;
        }

        // Baca filter tanggal
        dateFrom = dbFrom.getValue() != null ? new Timestamp(dbFrom.getValue().getTime()) : null;
        dateTo   = dbTo.getValue()   != null ? new Timestamp(dbTo.getValue().getTime())   : null;

        showStatus("Memuat data...", "info");

        try {
            switch (visMode) {
                case FLOW:        renderFlow();        break;
                case COMPARATIVE: renderComparative(); break;
                case TIMELINE:    renderTimeline();    break;
                case STAT:        renderStat();        break;
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Visualization failed", ex);
            showStatus("❌ Error: " + ex.getMessage(), "error");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERERS
    // ─────────────────────────────────────────────────────────────────────────

    private void renderFlow() {
        List<WFNodeDTO> nodes = dataProvider.getNodes(selectedWorkflowId);
        List<WFEdgeDTO> edges = dataProvider.getEdges(selectedWorkflowId);
        String json = jsonBuilder.buildFlowJson(nodes, edges);

        String js = String.format("""
            wfVisRenderNetwork('%s', %s, {
                layout: { hierarchical: { enabled: true, direction: 'UD', sortMethod: 'directed' } },
                physics: { enabled: false },
                interaction: { hover: true, tooltipDelay: 200 }
            });
            """, canvasId, json);
        Clients.evalJavaScript(js);
        showStatus("✓ Flow diagram: " + nodes.size() + " node, " + edges.size() + " edge", "ok");
    }

    private void renderComparative() {
        List<WFNodeDTO>      defNodes     = dataProvider.getNodes(selectedWorkflowId);
        List<WFEdgeDTO>      defEdges     = dataProvider.getEdges(selectedWorkflowId);
        Map<Integer, WFNodeDTO> statsMap  = dataProvider.getNodeStatistics(selectedWorkflowId, dateFrom, dateTo);
        Map<String, Integer> traversalMap = dataProvider.getEdgeTraversalCounts(selectedWorkflowId, dateFrom, dateTo);

        String json = jsonBuilder.buildComparativeJson(
            defNodes, defEdges, statsMap, traversalMap, highlightBottleneck);

        // Keterangan layer di dalam tooltip
        String js = String.format("""
            wfVisRenderNetwork('%s', %s, {
                groups: {
                    definition: { color: { background: '#1565C0', border: '#0D47A1' } },
                    actual:     { color: { background: '#E65100', border: '#BF360C' } }
                },
                physics: { enabled: true, stabilization: { iterations: 150 } },
                interaction: { hover: true, tooltipDelay: 200 },
                layout: { randomSeed: 42 }
            });
            wfVisAddLegend('%s', [
                { color: '#1565C0', label: 'Definisi Workflow' },
                { color: '#E65100', label: 'Eksekusi Aktual' },
                { color: '#BDBDBD', label: 'Bridge (definisi ↔ aktual)' },
                { color: '#FF1744', label: 'Bottleneck Node' }
            ]);
            """, canvasId, json, canvasId);
        Clients.evalJavaScript(js);

        long activeNodes = statsMap.values().stream().filter(n -> n.totalExecutions > 0).count();
        showStatus(String.format("✓ Comparative: %d def-node | %d node aktual | %d edge dilalui",
            defNodes.size(), activeNodes, traversalMap.size()), "ok");
    }

    private void renderTimeline() {
        List<WFProcessDTO>  processes  = dataProvider.getProcesses(
            selectedWorkflowId, selectedProcessId, dateFrom, dateTo, 100);
        List<WFActivityDTO> activities = dataProvider.getActivities(
            selectedWorkflowId, selectedProcessId, dateFrom, dateTo);

        String json = jsonBuilder.buildTimelineJson(processes, activities);

        String js = String.format("""
            wfVisRenderTimeline('%s', %s, {
                stack: true,
                showMajorLabels: true,
                showMinorLabels: true,
                orientation: { axis: 'top' },
                zoomMin: 60000,
                zoomMax: 31536000000
            });
            """, canvasId, json);
        Clients.evalJavaScript(js);
        showStatus("✓ Timeline: " + processes.size() + " proses, " + activities.size() + " aktivitas", "ok");
    }

    private void renderStat() {
        Map<Integer, WFNodeDTO> statsMap = dataProvider.getNodeStatistics(
            selectedWorkflowId, dateFrom, dateTo);

        String json = jsonBuilder.buildStatJson(statsMap, chartType, WFVisJsonBuilder.GroupBy.NODE);

        String js = String.format("wfVisRenderChart('%s', %s);", canvasId, json);
        Clients.evalJavaScript(js);

        long activeNodes = statsMap.values().stream().filter(n -> n.totalExecutions > 0).count();
        showStatus("✓ Statistics: " + activeNodes + " node dengan data", "ok");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POPULATION HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void populateWorkflowDropdown() {
        lbWorkflow.getChildren().clear();
        lbWorkflow.appendChild(new Listitem("-- Pilih Workflow --", -1));

        String sql = """
            SELECT w.AD_Workflow_ID, w.Name
            FROM AD_Workflow w
            WHERE w.IsActive = 'Y'
              AND w.AD_Client_ID IN (0, ?)
              AND EXISTS (SELECT 1 FROM AD_WF_Node n WHERE n.AD_Workflow_ID = w.AD_Workflow_ID)
            ORDER BY w.Name
            """;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, Env.getAD_Client_ID(Env.getCtx()));
            rs = pstmt.executeQuery();
            while (rs.next()) {
                Listitem li = new Listitem(rs.getString("Name"), rs.getInt("AD_Workflow_ID"));
                lbWorkflow.appendChild(li);
            }
        } catch (SQLException e) {
            log.warning("populateWorkflowDropdown: " + e.getMessage());
        } finally {
            DB.close(rs, pstmt);
        }
        lbWorkflow.setSelectedIndex(0);
    }

    private void populateProcessDropdown() {
        lbProcess.getChildren().clear();
        lbProcess.appendChild(new Listitem("(Semua)", "-1"));

        if (selectedWorkflowId <= 0) { lbProcess.setSelectedIndex(0); return; }

        String sql = """
            SELECT p.AD_WF_Process_ID,
                   p.WFState,
                   p.Created
            FROM AD_WF_Process p
            WHERE p.AD_Workflow_ID = ?
            ORDER BY p.Created DESC
            FETCH FIRST 200 ROWS ONLY
            """;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy HH:mm");
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, selectedWorkflowId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String label = "Process #" + rs.getInt("AD_WF_Process_ID")
                    + " [" + rs.getString("WFState") + "] "
                    + sdf.format(rs.getTimestamp("Created"));
                lbProcess.appendChild(
                    new Listitem(label, String.valueOf(rs.getInt("AD_WF_Process_ID"))));
            }
        } catch (SQLException e) {
            log.warning("populateProcessDropdown: " + e.getMessage());
        } finally {
            DB.close(rs, pstmt);
        }
        lbProcess.setSelectedIndex(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void showStatus(String msg, String type) {
        lblStatus.setValue(msg);
        lblStatus.setSclass("wfvis-status wfvis-status-" + type);
    }

    /** HTML awal untuk container — placeholder sebelum vis.js render */
    private String buildInitialHtml() {
        return "<div id=\"" + canvasId + "\" class=\"wfvis-canvas\">" +
               "<div class=\"wfvis-placeholder\">" +
               "<span>Pilih workflow dan klik <b>Visualisasi</b></span>" +
               "</div></div>";
    }

    /**
     * Script untuk memuat vis.js, vis-timeline, Chart.js secara lazy
     * dan mendefinisikan fungsi-fungsi wfVisRenderXxx.
     */
    private String buildLibraryLoaderScript() {
        return """
            (function() {
              // ── Library loader ─────────────────────────────────────────────
              function loadScript(src, cb) {
                if (document.querySelector('script[src="' + src + '"]')) { cb && cb(); return; }
                var s = document.createElement('script');
                s.src = src; s.onload = cb;
                document.head.appendChild(s);
              }
              function loadCss(href) {
                if (document.querySelector('link[href="' + href + '"]')) return;
                var l = document.createElement('link');
                l.rel = 'stylesheet'; l.href = href;
                document.head.appendChild(l);
              }

              // Load dari CDN (dapat diganti ke local path)
              loadCss('https://cdnjs.cloudflare.com/ajax/libs/vis/4.21.0/vis.min.css');
              loadScript('https://cdnjs.cloudflare.com/ajax/libs/vis/4.21.0/vis.min.js', function() {
                loadScript('https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.0/chart.umd.min.js', null);
              });

              // ── Inject CSS ─────────────────────────────────────────────────
              var style = document.createElement('style');
              style.textContent = `
                .wfvis-form           { display:flex; flex-direction:column; height:100%; }
                .wfvis-north          { padding:8px 12px; background:#F5F5F5; }
                .wfvis-toolbar-row    { display:flex; align-items:center; gap:8px; flex-wrap:wrap;
                                        padding:4px 0; }
                .wfvis-toolbar-row label { font-weight:600; white-space:nowrap; color:#424242; }
                .wfvis-container      { width:100%; height:100%; }
                .wfvis-canvas         { width:100%; height:calc(100vh - 200px);
                                        background:#FAFAFA; border:1px solid #E0E0E0;
                                        border-radius:4px; position:relative; }
                .wfvis-placeholder    { position:absolute; top:50%; left:50%;
                                        transform:translate(-50%,-50%);
                                        color:#9E9E9E; font-size:16px; }
                .wfvis-btn-primary    { background:#1565C0; color:#fff; border:none;
                                        padding:4px 12px; border-radius:3px; cursor:pointer; }
                .wfvis-btn-secondary  { background:#546E7A; color:#fff; border:none;
                                        padding:4px 12px; border-radius:3px; cursor:pointer; }
                .wfvis-status         { font-size:12px; padding:2px 8px; border-radius:3px; }
                .wfvis-status-ok      { background:#E8F5E9; color:#2E7D32; }
                .wfvis-status-error   { background:#FFEBEE; color:#C62828; }
                .wfvis-status-info    { background:#E3F2FD; color:#1565C0; }
                .wfvis-legend         { position:absolute; top:10px; right:10px;
                                        background:rgba(255,255,255,0.9);
                                        border:1px solid #ddd; border-radius:4px;
                                        padding:8px; font-size:12px; }
                .wfvis-legend-item    { display:flex; align-items:center; gap:6px; margin:2px 0; }
                .wfvis-legend-dot     { width:12px; height:12px; border-radius:50%; flex-shrink:0; }
              `;
              document.head.appendChild(style);

              // Store per-canvas references
              window._wfVisInstances = window._wfVisInstances || {};
              window._chartInstances = window._chartInstances || {};

              // ── wfVisRenderNetwork (Flow & Comparative) ────────────────────
              window.wfVisRenderNetwork = function(canvasId, data, options) {
                var container = document.getElementById(canvasId);
                if (!container) return;
                container.innerHTML = '';  // clear placeholder & legend

                if (window._wfVisInstances[canvasId]) {
                  try { window._wfVisInstances[canvasId].destroy(); } catch(e) {}
                }

                var network = new vis.Network(container,
                  { nodes: new vis.DataSet(data.nodes),
                    edges: new vis.DataSet(data.edges) },
                  options || {});
                window._wfVisInstances[canvasId] = network;
              };

              // ── wfVisRenderTimeline ────────────────────────────────────────
              window.wfVisRenderTimeline = function(canvasId, data, options) {
                var container = document.getElementById(canvasId);
                if (!container) return;
                container.innerHTML = '';

                if (window._wfVisInstances[canvasId]) {
                  try { window._wfVisInstances[canvasId].destroy(); } catch(e) {}
                }

                var timeline = new vis.Timeline(container,
                  new vis.DataSet(data.items),
                  new vis.DataSet(data.groups),
                  options || {});
                window._wfVisInstances[canvasId] = timeline;
              };

              // ── wfVisRenderChart (Chart.js) ────────────────────────────────
              window.wfVisRenderChart = function(canvasId, data) {
                var container = document.getElementById(canvasId);
                if (!container) return;
                container.innerHTML = '<canvas id="' + canvasId + '_chart"></canvas>';

                if (window._chartInstances[canvasId]) {
                  window._chartInstances[canvasId].destroy();
                }

                var ctx = document.getElementById(canvasId + '_chart').getContext('2d');
                window._chartInstances[canvasId] = new Chart(ctx, data);
              };

              // ── wfVisAddLegend ─────────────────────────────────────────────
              window.wfVisAddLegend = function(canvasId, items) {
                var container = document.getElementById(canvasId);
                if (!container) return;
                var legend = document.createElement('div');
                legend.className = 'wfvis-legend';
                items.forEach(function(item) {
                  var row = document.createElement('div');
                  row.className = 'wfvis-legend-item';
                  row.innerHTML = '<div class="wfvis-legend-dot" style="background:' +
                    item.color + '"></div><span>' + item.label + '</span>';
                  legend.appendChild(row);
                });
                container.appendChild(legend);
              };

              // ── wfVisFitView ───────────────────────────────────────────────
              window.wfVisFitView = function(canvasId) {
                var inst = window._wfVisInstances[canvasId];
                if (inst && inst.fit) inst.fit({ animation: { duration: 500 } });
              };

              // ── wfVisExportPng ─────────────────────────────────────────────
              window.wfVisExportPng = function(canvasId) {
                var inst = window._wfVisInstances[canvasId];
                if (inst && inst.getCanvas) {
                  var url = inst.getCanvas().toDataURL('image/png');
                  var a = document.createElement('a');
                  a.href = url; a.download = 'workflow_' + canvasId + '.png';
                  a.click();
                }
              };

            })();
            """;
    }
}
