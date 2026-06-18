package org.nsoft.wf.visualizer;

import org.adempiere.plugin.utils.Incremental2PackActivator;
import org.osgi.framework.BundleContext;

/**
 * MyActivator — Bundle activator + 2pack auto-installer.
 *
 * Extends Incremental2PackActivator (org.adempiere.plugin.utils) sehingga
 * 2pack zip yang ditempatkan di META-INF (dengan skema nama
 * "2Pack_&lt;name&gt;_&lt;version&gt;.zip") di-install otomatis saat bundle pertama
 * kali aktif.
 *
 * Catatan: registrasi service IFormFactory (NSoftWFVisFactory) dilakukan secara
 * deklaratif via OSGI-INF/component.xml (Declarative Services), bukan secara
 * manual di sini, untuk menghindari registrasi ganda.
 */
public class MyActivator extends Incremental2PackActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        super.stop(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Incremental2PackActivator hooks
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String getName() {
        return "org.nsoft.wf.visualizer";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "NSoft Workflow Visualizer — auto pack-in WFVisualizerForm AD_Form & AD_Menu";
    }
}
