package org.nsoft.wf.visualizer;

import org.adempiere.webui.panel.IFormFactory;
import org.compiere.Adempiere;
import org.idempiere.osgi.bridge.Incremental2PackActivator;
import org.nsoft.wf.visualizer.factory.NSoftWFVisFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * MyActivator — Bundle activator + 2pack auto-installer.
 *
 * Extends Incremental2PackActivator sehingga 2pack XML di-install
 * otomatis saat bundle pertama kali aktif (seperti di org.nsoft.workflow.activities).
 */
public class MyActivator extends Incremental2PackActivator {

    private ServiceRegistration<IFormFactory> formFactoryReg;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);

        // Daftarkan IFormFactory
        formFactoryReg = context.registerService(
            IFormFactory.class,
            new NSoftWFVisFactory(),
            null
        );
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (formFactoryReg != null) {
            formFactoryReg.unregister();
            formFactoryReg = null;
        }
        super.stop(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Incremental2PackActivator hooks
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected String get2PackFolderPath() {
        // Folder 2pack di dalam bundle jar
        return "2pack";
    }

    @Override
    protected String getSystemName() {
        return "org.nsoft.wf.visualizer";
    }
}
