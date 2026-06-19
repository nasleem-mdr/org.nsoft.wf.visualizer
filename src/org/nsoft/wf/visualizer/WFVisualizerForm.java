package org.nsoft.wf.visualizer;

import org.nsoft.wf.visualizer.model.*;
import org.zkoss.zk.ui.event.*;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.*;
import org.adempiere.webui.component.*;
import org.adempiere.webui.panel.ADForm;

import java.util.*;

/**
 * WFVisualizerForm — main ZK form untuk org.nsoft.wf.visualizer.
 *
 * Mendukung 4 mode:
 *   FLOW        — vis.Network (definisi murni)
 *   COMPARATIVE — vis.Network (definisi + overlay aktual)
 *   TIMELINE    — vis.Timeline (Gantt per instance)
 *   STAT        — Chart.js (bar + pie + throughput)
 *
 * Struktur UI (Borderlayout):
 * ┌──────────────────────────────────────────────────────────────┐
 * │ NORTH: row1=workflow/instance picker + mode tabs + btn       │
 * │        row2=opsi per-mode (conditional)                      │
 * ├──────────────────────────────────────────────────────────────┤
 * │ CENTER: div#wfVisContainer  (semua renderer di sini)         │
 * ├──────────────────────────────────────────────────────────────┤
 * │ SOUTH: statusbar + legend dinamis                            │
 * └──────────────────────────────────────────────────────────────┘
 */
public class WFVisualizerForm extends ADForm implements EventListener<Event> {

    private static final long serialVersionUID = 1L;

    // ── Services ──────────────────────────────────────────────────────────────
    private final WFDataProvider          dataProvider   = new WFDataProvider();
    private final WFFlowJsonBuilder       flowBuilder    = new WFFlowJsonBuilder();
    private final WFComparativeJsonBuilder compBuilder   = new WFComparativeJsonBuilder();
    private final WFTimelineJsonBuilder   timelineBuilder = new WFTimelineJsonBuilder();
    private final WFStatJsonBuilder       statBuilder    = new WFStatJsonBuilder();

    // ── State ─────────────────────────────────────────────────────────────────
    private WFVisMode currentMode         = WFVisMode.FLOW;
    private int       adWorkflowID        = 0;
    private int       adWFProcessID       = 0;       // 0 = semua
    private boolean   showDefinition      = true;
    private boolean   showActual          = true;
    private boolean   highlightBottleneck = false;

    // ── UI Components ─────────────────────────────────────────────────────────
    private Combobox  cbWorkflow;
    private Combobox  cbProcess;
    private Button    btnFlow, btnComparative, btnTimeline, btnStat;
    private Div       optionsRow;           // row2 — isi berubah per mode
    private Checkbox  chkDefinition, chkActual, chkBottleneck;
    private Label     lblStatus;
    private Div       legendRow;
    private Div       visContainer;

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void initForm() {
        this.setSclass("wf-visualizer-form");
        buildUI();
        loadWorkflowCombo();
        injectLibsAndCss();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Builder
    // ─────────────────────────────────────────────────────────────────────────

    private void buildUI() {
        Borderlayout bl = new Borderlayout();
        bl.setHeight("100%");
        bl.setWidth("100%");
        this.appendChild(bl);

        // ── NORTH ─────────────────────────────────────────────────────────
        North north = new North();
        north.setSclass("wf-vis-north");
        north.setCollapsible(false);
        bl.appendChild(north);

        Vlayout northInner = new Vlayout();
        northInner.setSclass("wf-vis-north-inner");
        north.appendChild(northInner);

        // Row 1: picker + mode tabs + btn visualisasi
        Hlayout row1 = new Hlayout();
        row1.setSclass("wf-vis-row");
        northInner.appendChild(row1);

        row1.appendChild(label("Workflow:"));
        cbWorkflow = new Combobox();
        cbWorkflow.setReadonly(true);
        cbWorkflow.setWidth("260px");
        cbWorkflow.addEventListener(Events.ON_SELECT, this);
        row1.appendChild(cbWorkflow);

        row1.appendChild(label("Instance:"));
        cbProcess = new Combobox();
        cbProcess.setReadonly(true);
        cbProcess.setWidth("230px");
        cbProcess.addEventListener(Events.ON_SELECT, this);
        row1.appendChild(cbProcess);

        // Mode tabs
        Div tabs = new Div();
        tabs.setSclass("wf-vis-tabs");
        btnFlow        = modeBtn("Flow",        WFVisMode.FLOW,        tabs);
        btnComparative = modeBtn("Comparative", WFVisMode.COMPARATIVE, tabs);
        btnTimeline    = modeBtn("Timeline",    WFVisMode.TIMELINE,    tabs);
        btnStat        = modeBtn("Statistik",   WFVisMode.STAT,        tabs);
        row1.appendChild(tabs);

        Button btnVisualize = new Button("▶ Visualisasi");
        btnVisualize.setId("btnVisualize");
        btnVisualize.setSclass("wf-vis-btn-run");
        btnVisualize.addEventListener(Events.ON_CLICK, this);
        row1.appendChild(btnVisualize);

        // Row 2: opsi dinamis (akan di-refresh saat mode berubah)
        optionsRow = new Div();
        optionsRow.setSclass("wf-vis-options-row");
        northInner.appendChild(optionsRow);
        buildOptionsForMode(currentMode);

        // ── CENTER ────────────────────────────────────────────────────────
        Center center = new Center();
        center.setFlex(true);
        bl.appendChild(center);

        visContainer = new Div();
        visContainer.setId("wfVisContainer");
        visContainer.setSclass("wf-vis-canvas");
        visContainer.setStyle("width:100%;height:100%;min-height:500px;position:relative;");
        center.appendChild(visContainer);

        // ── SOUTH ─────────────────────────────────────────────────────────
        South south = new South();
        south.setSclass("wf-vis-south");
        south.setHeight("30px");
        bl.appendChild(south);

        Hlayout southRow = new Hlayout();
        southRow.setSclass("wf-vis-south-row");
        south.appendChild(southRow);

        lblStatus = new Label("Pilih workflow untuk memulai.");
        lblStatus.setSclass("wf-vis-status");
        southRow.appendChild(lblStatus);

        legendRow = new Div();
        legendRow.setSclass("wf-vis-legend");
        southRow.appendChild(legendRow);
        buildLegendForMode(currentMode);

        // Aktifkan tombol mode default
        updateModeTabStyle();
    }

    /** Bangun area opsi sesuai mode aktif */
    private void buildOptionsForMode(WFVisMode mode) {
        optionsRow.getChildren().clear();

        if (mode == WFVisMode.COMPARATIVE) {
            Hlayout hl = new Hlayout();
            hl.setSclass("wf-vis-row wf-vis-subopts");
            optionsRow.appendChild(hl);

            chkDefinition = new Checkbox("Tampilkan Definisi");
            chkDefinition.setChecked(showDefinition);
            chkDefinition.addEventListener(Events.ON_CHECK, this);
            hl.appendChild(chkDefinition);

            chkActual = new Checkbox("Tampilkan Aktual");
            chkActual.setChecked(showActual);
            chkActual.addEventListener(Events.ON_CHECK, this);
            hl.appendChild(chkActual);

            chkBottleneck = new Checkbox("Highlight Bottleneck");
            chkBottleneck.setChecked(highlightBottleneck);
            chkBottleneck.addEventListener(Events.ON_CHECK, this);
            hl.appendChild(chkBottleneck);
        }
        // FLOW, TIMELINE, STAT — tidak ada opsi tambahan saat ini
    }

    /** Bangun legend sesuai mode aktif */
    private void buildLegendForMode(WFVisMode mode) {
        legendRow.getChildren().clear();
        if (mode == WFVisMode.FLOW || mode == WFVisMode.COMPARATIVE) {
            legendRow.appendChild(legendDot("#4CAF50", "Start"));
            legendRow.appendChild(legendDot("#F44336", "End"));
            legendRow.appendChild(legendDot("#2196F3", "User Choice"));
            legendRow.appendChild(legendDot("#FF9800", "Wait"));
            if (mode == WFVisMode.COMPARATIVE) {
                legendRow.appendChild(legendDot("#66BB6A", "Selesai"));
                legendRow.appendChild(legendDot("#FFA726", "Running"));
                legendRow.appendChild(legendDot("#EF5350", "Gagal"));
                legendRow.appendChild(legendDot("#D32F2F", "Bottleneck"));
            }
        } else if (mode == WFVisMode.TIMELINE) {
            legendRow.appendChild(legendDot("#66BB6A", "Selesai"));
            legendRow.appendChild(legendDot("#FFA726", "Berjalan"));
            legendRow.appendChild(legendDot("#EF5350", "Aborted"));
        } else if (mode == WFVisMode.STAT) {
            legendRow.appendChild(legendDot("#66BB6A", "Completed"));
            legendRow.appendChild(legendDot("#FFA726", "Running"));
            legendRow.appendChild(legendDot("#EF5350", "Aborted"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Loading
    // ─────────────────────────────────────────────────────────────────────────

    private void loadWorkflowCombo() {
        cbWorkflow.getItems().clear();
        Comboitem empty = new Comboitem("-- Pilih Workflow --");
        empty.setValue(0);
        cbWorkflow.appendChild(empty);
        dataProvider.getWorkflowList().forEach((id, name) -> {
            Comboitem item = new Comboitem(name);
            item.setValue(id);
            cbWorkflow.appendChild(item);
        });
        cbWorkflow.setSelectedIndex(0);
    }

    private void loadProcessCombo(int workflowID) {
        cbProcess.getItems().clear();
        Comboitem all = new Comboitem("(Semua Instance)");
        all.setValue(0);
        cbProcess.appendChild(all);
        dataProvider.getProcessList(workflowID).forEach((id, label) -> {
            Comboitem item = new Comboitem(label);
            item.setValue(id);
            cbProcess.appendChild(item);
        });
        cbProcess.setSelectedIndex(0);
        adWFProcessID = 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render dispatcher
    // ─────────────────────────────────────────────────────────────────────────

    private void doRender() {
        if (adWorkflowID <= 0) {
            lblStatus.setValue("Pilih workflow terlebih dahulu.");
            return;
        }
        lblStatus.setValue("Memuat data...");

        switch (currentMode) {
            case FLOW:        renderFlow();        break;
            case COMPARATIVE: renderComparative(); break;
            case TIMELINE:    renderTimeline();    break;
            case STAT:        renderStat();        break;
        }
    }

    // ── FLOW ─────────────────────────────────────────────────────────────────

    private void renderFlow() {
        List<WFNodeDTO> nodes = dataProvider.getNodes(adWorkflowID);
        List<WFEdgeDTO> edges = dataProvider.getEdges(adWorkflowID);
        String json = flowBuilder.build(nodes, edges);

        Clients.evalJavaScript(
            "(function(){" +
            "  function r(){" +
            "    var c=document.getElementById('wfVisContainer');" +
            "    if(!c||typeof vis==='undefined'){setTimeout(r,300);return;}" +
            "    var d=" + json + ";" +
            "    var n=new vis.DataSet(d.nodes);" +
            "    var e=new vis.DataSet(d.edges);" +
            "    var opt={" +
            "      layout:{hierarchical:{enabled:true,direction:'LR',sortMethod:'directed',levelSeparation:200}}," +
            "      physics:{enabled:false}," +
            "      interaction:{hover:true,tooltipDelay:150,navigationButtons:true,keyboard:true}," +
            "      nodes:{font:{size:13,color:'#ECEFF1'},borderWidth:2,shadow:{enabled:true,size:5}}," +
            "      edges:{smooth:{type:'cubicBezier',forceDirection:'horizontal'},arrows:'to'}" +
            "    };" +
            "    if(window._wfNet){window._wfNet.destroy();}" +
            "    window._wfNet=new vis.Network(c,{nodes:n,edges:e},opt);" +
            "    window._wfNet.once('stabilized',function(){" +
            "      window._wfNet.fit({animation:{duration:500,easingFunction:'easeInOutQuad'}});" +
            "    });" +
            "  } r();" +
            "})();"
        );

        lblStatus.setValue("Flow Diagram: " + nodes.size() + " node, " + edges.size() + " edge.");
    }

    // ── COMPARATIVE ───────────────────────────────────────────────────────────

    private void renderComparative() {
        List<WFNodeDTO>    nodes     = dataProvider.getNodes(adWorkflowID);
        List<WFEdgeDTO>    edges     = dataProvider.getEdges(adWorkflowID);
        List<WFProcessDTO> processes = dataProvider.getProcesses(adWorkflowID, adWFProcessID, null, null);
        processes.forEach(dataProvider::loadActivities);

        String json = compBuilder.build(nodes, edges, processes, showDefinition, showActual, highlightBottleneck);

        Clients.evalJavaScript(
            "(function(){" +
            "  function r(){" +
            "    var c=document.getElementById('wfVisContainer');" +
            "    if(!c||typeof vis==='undefined'){setTimeout(r,300);return;}" +
            "    var d=" + json + ";" +
            "    var n=new vis.DataSet(d.nodes);" +
            "    var e=new vis.DataSet(d.edges);" +
            "    var opt={" +
            "      layout:{hierarchical:{enabled:true,direction:'LR',sortMethod:'directed',levelSeparation:200}}," +
            "      physics:{enabled:false}," +
            "      interaction:{hover:true,tooltipDelay:150,navigationButtons:true,keyboard:true}," +
            "      nodes:{font:{size:13,color:'#ECEFF1'},borderWidth:2,shadow:{enabled:true,size:5}}," +
            "      edges:{smooth:{type:'cubicBezier',forceDirection:'horizontal'},arrows:'to'}" +
            "    };" +
            "    if(window._wfNet){window._wfNet.destroy();}" +
            "    window._wfNet=new vis.Network(c,{nodes:n,edges:e},opt);" +
            "    window._wfNet.once('stabilized',function(){" +
            "      window._wfNet.fit({animation:{duration:500,easingFunction:'easeInOutQuad'}});" +
            "    });" +
            "    console.log('[WFVis] Comparative: '+d.meta.processCount+' proses, bottleneck='+d.meta.bottleneckNodeID);" +
            "  } r();" +
            "})();"
        );

        lblStatus.setValue(String.format("Comparative: %d node, %d instance dimuat.", nodes.size(), processes.size()));
    }

    // ── TIMELINE ──────────────────────────────────────────────────────────────

    private void renderTimeline() {
        List<WFProcessDTO> processes = dataProvider.getProcesses(adWorkflowID, adWFProcessID, null, null);
        processes.forEach(dataProvider::loadActivities);

        String json = timelineBuilder.build(processes);

        Clients.evalJavaScript(
            "(function(){" +
            "  function r(){" +
            "    var c=document.getElementById('wfVisContainer');" +
            "    if(!c||typeof vis==='undefined'){setTimeout(r,300);return;}" +
            "    var d=" + json + ";" +
            "    var groups=new vis.DataSet(d.groups);" +
            "    var items=new vis.DataSet(d.items);" +
            "    if(window._wfTimeline){window._wfTimeline.destroy();}" +
            "    window._wfTimeline=new vis.Timeline(c,items,groups,d.options);" +
            "    window._wfTimeline.fit();" +
            "  } r();" +
            "})();"
        );

        lblStatus.setValue("Timeline: " + processes.size() + " instance dimuat.");
    }

    // ── STAT ──────────────────────────────────────────────────────────────────

    private void renderStat() {
        List<WFProcessDTO> processes = dataProvider.getProcesses(adWorkflowID, adWFProcessID, null, null);
        processes.forEach(dataProvider::loadActivities);

        String json = statBuilder.build(processes);

        // Chart.js — load jika belum ada, lalu render 3 canvas dalam satu container
        Clients.evalJavaScript(
            "(function(){" +
            "  function r(){" +
            "    var c=document.getElementById('wfVisContainer');" +
            "    if(!c){setTimeout(r,300);return;}" +
            "    if(typeof Chart==='undefined'){" +
            "      var s=document.createElement('script');" +
            "      s.src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js';" +
            "      s.onload=function(){buildCharts(c);};" +
            "      document.head.appendChild(s); return;" +
            "    }" +
            "    buildCharts(c);" +
            "  }" +
            "  function buildCharts(c){" +
            "    var d=" + json + ";" +
            // Bersihkan container
            "    c.innerHTML='';" +
            "    c.style.overflowY='auto';" +
            // ── Summary card ──
            "    var sumDiv=document.createElement('div');" +
            "    sumDiv.className='wf-stat-summary';" +
            "    var sm=d.summary;" +
            "    sumDiv.innerHTML=" +
            "      '<div class=\"wf-stat-card\"><span class=\"wf-stat-num\">'+sm.totalProcess+'</span><br>Total Instance</div>'" +
            "     +'<div class=\"wf-stat-card wf-stat-green\"><span class=\"wf-stat-num\">'+sm.completedCount+'</span><br>Selesai</div>'" +
            "     +'<div class=\"wf-stat-card wf-stat-orange\"><span class=\"wf-stat-num\">'+sm.runningCount+'</span><br>Berjalan</div>'" +
            "     +'<div class=\"wf-stat-card wf-stat-red\"><span class=\"wf-stat-num\">'+sm.abortedCount+'</span><br>Aborted</div>'" +
            "     +'<div class=\"wf-stat-card wf-stat-blue\"><span class=\"wf-stat-num\">'+sm.avgTotalDurationMin+' mnt</span><br>Avg Durasi</div>'" +
            "     +'<div class=\"wf-stat-card wf-stat-dark\"><span class=\"wf-stat-num wf-stat-small\">'+sm.bottleneckNodeName+'</span><br>Bottleneck</div>';" +
            "    c.appendChild(sumDiv);" +
            // ── Row charts ──
            "    var row=document.createElement('div');" +
            "    row.style.cssText='display:flex;gap:12px;padding:12px;flex-wrap:wrap;';" +
            "    c.appendChild(row);" +
            // Bar chart durasi per node
            "    var divBar=document.createElement('div');" +
            "    divBar.style.cssText='flex:2;min-width:360px;background:#1E2D3D;border-radius:8px;padding:12px;';" +
            "    divBar.innerHTML='<p style=\"color:#90A4AE;font-size:12px;margin:0 0 8px\">Avg Durasi per Node (menit)</p>';" +
            "    var canBar=document.createElement('canvas');" +
            "    divBar.appendChild(canBar);" +
            "    row.appendChild(divBar);" +
            "    var nodeLabels=d.durationPerNode.map(function(x){return x.nodeName;});" +
            "    var nodeAvgs  =d.durationPerNode.map(function(x){return x.avgMin;});" +
            "    var nodeColors=d.durationPerNode.map(function(x){return x.isBottleneck?'#D32F2F':'#1976D2';});" +
            "    new Chart(canBar,{type:'bar'," +
            "      data:{labels:nodeLabels,datasets:[{label:'Avg Durasi (mnt)',data:nodeAvgs," +
            "        backgroundColor:nodeColors,borderRadius:4}]}," +
            "      options:{indexAxis:'y',responsive:true,plugins:{legend:{display:false}," +
            "        tooltip:{callbacks:{label:function(ctx){return ctx.parsed.x+' mnt ('+d.durationPerNode[ctx.dataIndex].count+'x)';}}}}" +
            "        ,scales:{x:{ticks:{color:'#90A4AE'},grid:{color:'#263238'}}," +
            "                  y:{ticks:{color:'#B0BEC5'},grid:{color:'#263238'}}}}});" +
            // Pie chart status
            "    var divPie=document.createElement('div');" +
            "    divPie.style.cssText='flex:1;min-width:220px;background:#1E2D3D;border-radius:8px;padding:12px;';" +
            "    divPie.innerHTML='<p style=\"color:#90A4AE;font-size:12px;margin:0 0 8px\">Distribusi Status</p>';" +
            "    var canPie=document.createElement('canvas');" +
            "    divPie.appendChild(canPie);" +
            "    row.appendChild(divPie);" +
            "    new Chart(canPie,{type:'doughnut'," +
            "      data:{labels:d.statusDist.map(function(x){return x.label;})," +
            "            datasets:[{data:d.statusDist.map(function(x){return x.value;})," +
            "              backgroundColor:d.statusDist.map(function(x){return x.color;}),borderWidth:2,borderColor:'#1A2332'}]}," +
            "      options:{responsive:true,plugins:{legend:{position:'bottom',labels:{color:'#90A4AE',font:{size:11}}}}}});" +
            // Bar chart throughput
            "    if(d.throughput.length>0){" +
            "      var divTp=document.createElement('div');" +
            "      divTp.style.cssText='flex:2;min-width:360px;background:#1E2D3D;border-radius:8px;padding:12px;';" +
            "      divTp.innerHTML='<p style=\"color:#90A4AE;font-size:12px;margin:0 0 8px\">Throughput per Minggu</p>';" +
            "      var canTp=document.createElement('canvas');" +
            "      divTp.appendChild(canTp);" +
            "      row.appendChild(divTp);" +
            "      new Chart(canTp,{type:'bar'," +
            "        data:{labels:d.throughput.map(function(x){return x.period;})," +
            "              datasets:[{label:'Jumlah Instance',data:d.throughput.map(function(x){return x.count;})," +
            "                backgroundColor:'#0288D1',borderRadius:4}]}," +
            "        options:{responsive:true,plugins:{legend:{display:false}}," +
            "          scales:{x:{ticks:{color:'#90A4AE'},grid:{color:'#263238'}}," +
            "                  y:{ticks:{color:'#90A4AE'},grid:{color:'#263238'},beginAtZero:true}}}});" +
            "    }" +
            "  }" +
            "  r();" +
            "})();"
        );

        lblStatus.setValue("Statistik: " + processes.size() + " instance dianalisis.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event Handler
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onEvent(Event event) throws Exception {
        Object src  = event.getTarget();
        String name = event.getName();

        if (src == cbWorkflow && Events.ON_SELECT.equals(name)) {
            Comboitem sel = cbWorkflow.getSelectedItem();
            if (sel != null) {
                adWorkflowID = (Integer) sel.getValue();
                if (adWorkflowID > 0) loadProcessCombo(adWorkflowID);
            }
        }
        else if (src == cbProcess && Events.ON_SELECT.equals(name)) {
            Comboitem sel = cbProcess.getSelectedItem();
            if (sel != null) adWFProcessID = (Integer) sel.getValue();
        }
        else if (src == chkDefinition) { showDefinition      = chkDefinition.isChecked(); }
        else if (src == chkActual)     { showActual          = chkActual.isChecked(); }
        else if (src == chkBottleneck) { highlightBottleneck = chkBottleneck.isChecked(); }
        else if (src instanceof Button && Events.ON_CLICK.equals(name)) {
            Button btn = (Button) src;
            if      (btn == btnFlow)        switchMode(WFVisMode.FLOW);
            else if (btn == btnComparative) switchMode(WFVisMode.COMPARATIVE);
            else if (btn == btnTimeline)    switchMode(WFVisMode.TIMELINE);
            else if (btn == btnStat)        switchMode(WFVisMode.STAT);
            else                            doRender();  // btnVisualize
        }
    }

    private void switchMode(WFVisMode mode) {
        currentMode = mode;
        updateModeTabStyle();
        buildOptionsForMode(mode);
        buildLegendForMode(mode);
    }

    private void updateModeTabStyle() {
        setTabActive(btnFlow,        currentMode == WFVisMode.FLOW);
        setTabActive(btnComparative, currentMode == WFVisMode.COMPARATIVE);
        setTabActive(btnTimeline,    currentMode == WFVisMode.TIMELINE);
        setTabActive(btnStat,        currentMode == WFVisMode.STAT);
    }

    private void setTabActive(Button btn, boolean active) {
        btn.setSclass(active ? "wf-vis-tab wf-vis-tab-active" : "wf-vis-tab");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lib injection
    // ─────────────────────────────────────────────────────────────────────────

    private void injectLibsAndCss() {
        // CSS
        Clients.evalJavaScript(
            "if(!document.getElementById('wf-vis-css')){" +
            "  var s=document.createElement('style');s.id='wf-vis-css';" +
            "  s.textContent=`" + WF_VIS_CSS + "`;" +
            "  document.head.appendChild(s);" +
            "}"
        );
        // vis.js (Network + Timeline dalam satu bundle)
        Clients.evalJavaScript(
            "if(typeof vis==='undefined'){" +
            "  var s=document.createElement('script');" +
            "  s.src='https://cdnjs.cloudflare.com/ajax/libs/vis/4.21.0/vis.min.js';" +
            "  document.head.appendChild(s);" +
            "  var l=document.createElement('link');l.rel='stylesheet';" +
            "  l.href='https://cdnjs.cloudflare.com/ajax/libs/vis/4.21.0/vis.min.css';" +
            "  document.head.appendChild(l);" +
            "}"
        );
        // Chart.js sudah di-load lazy hanya saat mode STAT dirender
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Label label(String text) {
        Label l = new Label(text);
        l.setSclass("wf-vis-label");
        return l;
    }

    private Button modeBtn(String text, WFVisMode mode, Div parent) {
        Button btn = new Button(text);
        btn.setSclass("wf-vis-tab");
        btn.addEventListener(Events.ON_CLICK, this);
        parent.appendChild(btn);
        return btn;
    }

    private Span legendDot(String color, String label) {
        Span s = new Span();
        s.setSclass("wf-legend-item");
        Span dot = new Span();
        dot.setStyle("display:inline-block;width:11px;height:11px;" +
                     "border-radius:2px;background:" + color + ";margin-right:4px;");
        s.appendChild(dot);
        s.appendChild(new Label(label));
        return s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CSS
    // ─────────────────────────────────────────────────────────────────────────

    private static final String WF_VIS_CSS =
        // Form wrapper
        ".wf-visualizer-form{display:flex;flex-direction:column;height:100%;}" +
        // North
        ".wf-vis-north{background:#1A2332;border-bottom:1px solid #263238;}" +
        ".wf-vis-north-inner{padding:8px 12px;gap:4px;}" +
        ".wf-vis-row{display:flex;align-items:center;gap:10px;padding:4px 0;flex-wrap:wrap;}" +
        ".wf-vis-label{color:#B0BEC5;font-size:12px;white-space:nowrap;}" +
        // Mode tabs
        ".wf-vis-tabs{display:flex;gap:2px;background:#0D1B2A;border-radius:6px;padding:3px;}" +
        ".wf-vis-tab{background:transparent;color:#78909C;border:none;padding:5px 14px;" +
        "  border-radius:4px;cursor:pointer;font-size:12px;font-weight:500;transition:all .15s;}" +
        ".wf-vis-tab:hover{color:#B0BEC5;background:#1E2D3D;}" +
        ".wf-vis-tab-active{background:#1976D2 !important;color:#fff !important;}" +
        // Run button
        ".wf-vis-btn-run{background:#00897B;color:#fff;border:none;" +
        "  padding:6px 18px;border-radius:4px;cursor:pointer;font-weight:600;font-size:13px;}" +
        ".wf-vis-btn-run:hover{background:#00695C;}" +
        // Options sub-row
        ".wf-vis-options-row{padding:4px 0;}" +
        ".wf-vis-subopts{gap:16px;padding:4px 0;border-top:1px solid #263238;}" +
        ".wf-vis-subopts .z-checkbox-cnt{color:#90A4AE;font-size:12px;}" +
        // Canvas
        ".wf-vis-canvas{background:#0D1B2A;}" +
        // South
        ".wf-vis-south{background:#1A2332;border-top:1px solid #263238;}" +
        ".wf-vis-south-row{display:flex;align-items:center;gap:14px;padding:0 12px;height:30px;}" +
        ".wf-vis-status{color:#546E7A;font-size:11px;margin-right:12px;}" +
        ".wf-vis-legend{display:flex;align-items:center;gap:12px;}" +
        ".wf-legend-item{display:flex;align-items:center;color:#78909C;font-size:11px;}" +
        // Stat cards
        ".wf-stat-summary{display:flex;flex-wrap:wrap;gap:10px;padding:12px;}" +
        ".wf-stat-card{background:#1E2D3D;border-radius:8px;padding:12px 18px;" +
        "  color:#90A4AE;font-size:11px;text-align:center;min-width:110px;}" +
        ".wf-stat-num{color:#ECEFF1;font-size:22px;font-weight:700;display:block;}" +
        ".wf-stat-small{font-size:13px !important;}" +
        ".wf-stat-green{border-left:3px solid #66BB6A;}" +
        ".wf-stat-orange{border-left:3px solid #FFA726;}" +
        ".wf-stat-red{border-left:3px solid #EF5350;}" +
        ".wf-stat-blue{border-left:3px solid #29B6F6;}" +
        ".wf-stat-dark{border-left:3px solid #D32F2F;}";
}
