package org.nsoft.wf.visualizer.factory;

import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormFactory;
import org.nsoft.wf.visualizer.WFVisualizerForm;

/**
 * NSoftWFVisFactory — IFormFactory untuk mendaftarkan WFVisualizerForm.
 *
 * Didaftarkan via OSGI-INF/component.xml sebagai service IFormFactory.
 * Mengikuti pola yang sama dengan NSoftFormFactory di plugin workflow.
 */
public class NSoftWFVisFactory implements IFormFactory {

    @Override
    public ADForm newFormInstance(String formName) {
        if ("org.nsoft.wf.visualizer.WFVisualizerForm".equals(formName)) {
            return new WFVisualizerForm();
        }
        return null;
    }
}
