// Copyright (C) 2007 Tuma Solutions, LLC
// Process Dashboard - Data Automation Tool for high-maturity processes
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// The author(s) may be contacted at:
// Process Dashboard Group
// c/o Ken Raisor
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC:  processdash-devel@lists.sourceforge.net

package net.sourceforge.processdash.log.ui.importer;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.beans.EventHandler;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import net.sourceforge.processdash.DashboardContext;
import net.sourceforge.processdash.InternalSettings;
import net.sourceforge.processdash.ProcessDashboard;
import net.sourceforge.processdash.Settings;
import net.sourceforge.processdash.hier.DashHierarchy;
import net.sourceforge.processdash.hier.PropertyKey;
import net.sourceforge.processdash.i18n.Resources;
import net.sourceforge.processdash.log.defects.Defect;
import net.sourceforge.processdash.log.defects.DefectLog;
import net.sourceforge.processdash.log.defects.DefectUtil;
import net.sourceforge.processdash.net.http.WebServer;
import net.sourceforge.processdash.templates.SqlDriverManager;
import net.sourceforge.processdash.templates.TemplateLoader;
import net.sourceforge.processdash.ui.DashboardIconFactory;
import net.sourceforge.processdash.ui.lib.binding.BoundForm;
import net.sourceforge.processdash.ui.lib.binding.BoundSqlConnection;
import net.sourceforge.processdash.util.StringUtils;
import net.sourceforge.processdash.util.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DefectImportForm extends BoundForm {

    private DashboardContext dashboardContext;
    private String selectedPath;
    private String defectLogPath;
    private String formId;

    private JFrame frame;
    private Map persistentValues;

    private static final String SETTING_ATTR = "setting";



    static final Resources resources = Resources
            .getDashBundle("Defects.ImportForm");
    private static final Logger logger = Logger
            .getLogger(DefectImportForm.class.getName());


    public DefectImportForm(DashboardContext dashboardContext,
            Element configElement, String selectedPath, String defectLogPath,
            String displayName) throws AbortImport {

        this.dashboardContext = dashboardContext;
        this.selectedPath = selectedPath;
        this.defectLogPath = defectLogPath;
        this.formId = this.hashChars = configElement.getAttribute("id");
        setResources(resources);

        Document spec = openSpecDocument(configElement);
        checkPackageRequirements(spec);
        if (!StringUtils.hasValue(this.formId))
            this.formId = spec.getDocumentElement().getAttribute("id");

        readExtraProperties(configElement);
        registerExplicitSqlDrivers();
        addDefectSpecificTypes();
        createValueMappers();
        addFormHeader();
        addFormElements(spec.getDocumentElement());
        buildAndShowWindow(spec.getDocumentElement(), displayName);
    }


    public DashboardContext getDashContext() {
        return dashboardContext;
    }

    public String getSelectedPath() {
        return selectedPath;
    }

    public String getDefectLogPath() {
        return defectLogPath;
    }

    public String getFormId() {
        return formId;
    }


    private Document openSpecDocument(Element xml) throws AbortImport {
        String userSettingName = getUserSettingFullName("specLocation");
        String href = Settings.getVal(userSettingName);
        if (!StringUtils.hasValue(href))
            href = xml.getAttribute("specLocation");

        InputStream in = openStream(href);
        if (in == null) {
            logger.log(Level.SEVERE,
                    "For defect-importer {0}, cannot locate spec {1}",
                    new Object[] { formId, href });
            boolean noNetwork = false;
            try {
                if (InetAddress.getLocalHost().isLoopbackAddress())
                    noNetwork = true;
            } catch (Exception e) {}
            AbortImport.showErrorAndAbort(noNetwork ? "No_Network_Available"
                    : "Cannot_Find_Spec");
        }

        try {
            return XMLUtils.parse(in);
        } catch (Exception e) {
            e.printStackTrace();
            AbortImport.showErrorAndAbort("Cannot_Read_Spec", href);
            return null; // this line will not be reached
        }
    }


    private InputStream openStream(String href) {
        InputStream in = null;
        try {
            // check for windows-style file path (UNC or drive letter)
            if (href.startsWith("\\\\") || href.indexOf(":\\") == 1)
                in = new FileInputStream(href);

            // check for URL
            else if (href.indexOf(":/") != -1) {
                URLConnection conn = new URL(href).openConnection();
                String version = TemplateLoader.getPackageVersion("pspdash");
                if (StringUtils.hasValue(version))
                    conn.setRequestProperty("X-Process-Dashboard-Version",
                            version);
                in = conn.getInputStream();

            // default: spec is packaged in dashboard template area
            } else {
                if (href.startsWith("/"))
                    href = WebServer.DASHBOARD_PROTOCOL + ":" + href;
                else
                    href = WebServer.DASHBOARD_PROTOCOL + ":/" + href;
                in = new URL(href).openStream();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return in;
    }


    private void checkPackageRequirements(Document spec) throws AbortImport {
        String requires = spec.getDocumentElement().getAttribute("requires");
        if (!TemplateLoader.meetsPackageRequirement(requires))
            AbortImport.showErrorAndAbort("Version_Mismatch",
                    TemplateLoader.getPackageVersion("pspdash"));
    }


    private void readExtraProperties(Element configElement) {
        // first, see if there is a "properties" attribute on the config
        String propertiesFile = configElement.getAttribute("properties");
        if (StringUtils.hasValue(propertiesFile)) {
            String userSettingName = getUserSettingFullName("propertiesLocation");
            String userSettingVal = Settings.getVal(userSettingName);
            if (StringUtils.hasValue(userSettingVal))
                propertiesFile = userSettingVal;
            readExternalProperties(propertiesFile);
        }

        // now, see if the config has any child elements that specify properties
        List children = XMLUtils.getChildElements(configElement);
        for (Iterator i = children.iterator(); i.hasNext();) {
            Element e = (Element) i.next();
            if (!"property".equals(e.getTagName()))
                // we're only interested in <property> tags.
                continue;

            // for a child of the form <property file="..."/>, load
            // external properties from the named file.
            if (e.hasAttribute("file"))
                readExternalProperties(e.getAttribute("file"));

            // for a child of the form <property name="..." value="..."/>,
            // set a single property value as indicated.
            else if (e.hasAttribute("name") && e.hasAttribute("value"))
                put(e.getAttribute("name"), e.getAttribute("value"));
        }
    }


    private void readExternalProperties(String propertiesFile) {
        if (!StringUtils.hasValue(propertiesFile))
            return;

        InputStream in = openStream(propertiesFile);
        if (in == null) {
            logger.log(Level.SEVERE,
                "For defect-importer {0}, cannot locate properties {1}",
                new Object[] { formId, propertiesFile });
            return;
        }

        Properties props = new Properties();
        try {
            props.load(in);
        } catch (IOException e) {
            logger.log(Level.SEVERE,
                "For defect-importer {0}, cannot read properties {1}",
                new Object[] { formId, propertiesFile });
            e.printStackTrace();
            return;
        }
        putAll(props);
    }


    private void registerExplicitSqlDrivers() {
        String driver = (String) get(BoundSqlConnection.DEFAULT_ID
                + ".driver");
        if (StringUtils.hasValue(driver))
            SqlDriverManager.registerDriver(driver);
    }


    private void addDefectSpecificTypes() {
        addElementType("default-type-selector", DefaultTypeSelector.class);
        addElementType("default-inj-phase-selector", DefaultPhaseSelector.class);
        addElementType("default-rem-phase-selector", DefaultPhaseSelector.class);
    }



    public Object addFormElement(Element xml) {
        Object result = super.addFormElement(xml);
        if (result == null)
            return null;

        String settingName = xml.getAttribute(SETTING_ATTR);
        if (StringUtils.hasValue(settingName)) {
            String id = xml.getAttribute("id");
            registerSetting(id, settingName);
        }

        return result;
    }


    protected String getUserSettingFullName(String name) {
        return "defectImport." + formId + "." + name;
    }


    protected void registerSetting(String id, String settingName) {
        String fullSettingName = getUserSettingFullName(settingName);
        String settingValue = Settings.getVal(fullSettingName);
        if (!StringUtils.hasValue(settingValue))
            settingValue = System.getProperty(settingName);

        if (persistentValues == null)
            persistentValues = new HashMap();
        persistentValues.put(id, fullSettingName);
        put(id, settingValue);
    }


    protected void persistSettings() {
        if (persistentValues != null) {
            for (Iterator i = persistentValues.entrySet().iterator(); i.hasNext();) {
                Map.Entry e = (Map.Entry) i.next();
                String id = (String) e.getKey();
                String fullSettingName = (String) e.getValue();
                String settingValue = StringUtils.asString(get(id));
                InternalSettings.set(fullSettingName, settingValue);
            }
        }
    }


    private void createValueMappers() {
        put(DefaultTypeSelector.TYPE_ID, Defect.UNSPECIFIED);
        new DefectFieldMapper(this, //
                BoundDefectData.getMapperId(BoundDefectData.TYPE), //
                "FIXME-type-translator", //
                DefaultTypeSelector.TYPE_ID, //
                null);

        List defectPhases = DefectUtil.getDefectPhases(defectLogPath,
                dashboardContext);
        String removalPhase = DefectUtil.guessRemovalPhase(defectLogPath,
                selectedPath, dashboardContext);
        String injectionPhase = DefectUtil.guessInjectionPhase(defectPhases,
                removalPhase);

        defectPhases.add(0, Defect.UNSPECIFIED);
        put(DefaultPhaseSelector.PHASE_LIST_ID, defectPhases);

        if (!defectPhases.contains(injectionPhase))
            injectionPhase = Defect.UNSPECIFIED;
        put(DefaultPhaseSelector.INJ_PHASE_ID, injectionPhase);
        new DefectFieldMapper(this, //
                BoundDefectData.getMapperId(BoundDefectData.INJECTED), //
                "FIXME-inj-translator", //
                DefaultPhaseSelector.INJ_PHASE_ID, //
                defectPhases);

        if (!defectPhases.contains(removalPhase))
            removalPhase = Defect.UNSPECIFIED;
        put(DefaultPhaseSelector.REM_PHASE_ID, removalPhase);
        new DefectFieldMapper(this, //
                BoundDefectData.getMapperId(BoundDefectData.REMOVED), //
                "FIXME-rem-translator", //
                DefaultPhaseSelector.REM_PHASE_ID, //
                defectPhases);
    }

    public Object addFormElement(Element xml, String type) {
        if ("sql-connection".equals(type))
            SqlDriverManager.registerDriver(xml.getAttribute("driver"));

        return super.addFormElement(xml, type);
    }

    private void addFormHeader() {
        String headerText = resources.format("Header_FMT", selectedPath);
        JLabel headerLabel = new JLabel(headerText);
        Font font = headerLabel.getFont();
        headerLabel.setFont(font.deriveFont(font.getSize2D() * 1.3f));
        addFormComponent(headerLabel, (String) null);
    }


    private void buildAndShowWindow(Element xml, String windowName) {
        frame = new JFrame(windowName) {
            public void dispose() {
                super.dispose();
                disposeForm();
            }
        };
        frame.setIconImage(DashboardIconFactory.getWindowIconImage());
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        ((JComponent) getContainer()).setBorder(new EmptyBorder(5, 5, 5, 5));
        frame.getContentPane().add(BorderLayout.NORTH, getContainer());

        BoundDefectTable defectTable = new BoundDefectTable(this);
        defectTable.setBorder(new EmptyBorder(0, 10, 0, 10));
        frame.getContentPane().add(BorderLayout.CENTER, defectTable);

        frame.getContentPane().add(BorderLayout.SOUTH, createButtonBox());

        int width = XMLUtils.getXMLInt(xml, "windowWidth");
        if (width <= 0) width = 600;
        int height = XMLUtils.getXMLInt(xml, "windowHeight");
        if (height <= 0) height = 500;
        frame.setSize(width, height);
        frame.show();
    }

    private Box createButtonBox() {
        JButton cancelButton = new JButton(resources.getString("Cancel"));
        cancelButton.addActionListener((ActionListener) EventHandler.create(
                ActionListener.class, this, "cancel"));

        final JButton importButton = new JButton(resources
                .getString("Import_Button"));
        importButton.addActionListener((ActionListener) EventHandler.create(
                ActionListener.class, this, "doImport"));
        importButton.setEnabled(false);

        final BoundDefectData data = BoundDefectData.getDefectData(this);
        data.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                importButton.setEnabled(data.hasSelectedDefects());
            }});

        Box buttonBox = Box.createHorizontalBox();
        buttonBox.add(Box.createHorizontalGlue());
        buttonBox.add(cancelButton);
        buttonBox.add(Box.createHorizontalStrut(10));
        buttonBox.add(importButton);
        buttonBox.setBorder(new EmptyBorder(10, 10, 10, 10));

        return buttonBox;
    }

    public void cancel() {
        frame.dispose();
    }

    public void doImport() {
        BoundDefectData data = BoundDefectData.getDefectData(this);
        List defects = data.getAsDefectList();
        if (defects != null && !defects.isEmpty())
            importDefects(defects);

        persistSettings();
        frame.dispose();
    }


    private void importDefects(List defects) {
        DashHierarchy hier = dashboardContext.getHierarchy();
        PropertyKey defectLogKey = hier.findExistingKey(defectLogPath);
        ProcessDashboard dashboard = (ProcessDashboard) dashboardContext;
        String filename = hier.pget(defectLogKey).getDefectLog();
        if (!StringUtils.hasValue(filename)) {
            AbortImport.showError("Hierarchy_Changed", selectedPath);
            return;
        }
        DefectLog defectLog = new DefectLog(
                dashboard.getDirectory() + filename, defectLogPath,
                dashboardContext.getData(), dashboard);

        int addedCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (Iterator i = defects.iterator(); i.hasNext();) {
            Defect newDefect = (Defect) i.next();
            Defect oldDefect = defectLog.getDefect(newDefect.number);
            if (oldDefect == null) {
                addedCount++;
                defectLog.writeDefect(newDefect);
            } else {
                Defect originalDefect = (Defect) oldDefect.clone();
                oldDefect.defect_type = merge(oldDefect.defect_type, newDefect.defect_type);
                oldDefect.phase_injected = merge(oldDefect.phase_injected, newDefect.phase_injected);
                oldDefect.phase_removed = merge(oldDefect.phase_removed, newDefect.phase_removed);
                oldDefect.description = merge(oldDefect.description, newDefect.description);
                oldDefect.fix_time = merge(oldDefect.fix_time, newDefect.fix_time);
                oldDefect.fix_defect = merge(oldDefect.fix_defect, newDefect.fix_defect);
                if (originalDefect.equals(oldDefect)) {
                    unchangedCount++;
                } else {
                    updatedCount++;
                    defectLog.writeDefect(oldDefect);
                }
            }
        }

        if (addedCount == 0 && updatedCount == 0) {
            JOptionPane.showMessageDialog(frame,
                    resources.getStrings("Nothing_To_Do.Message"),
                    resources.getString("Nothing_To_Do.Title"),
                    JOptionPane.INFORMATION_MESSAGE);

        } else if (addedCount < defects.size()) {
            String title = resources.getString("Defects_Updated.Title");
            Object[] message = new Object[4];
            message[0] = resources.getStrings("Defects_Updated.Message");
            message[1] = getUpdatedSegment("Added", addedCount);
            message[2] = getUpdatedSegment("Unchanged", unchangedCount);
            message[3] = getUpdatedSegment("Updated", updatedCount);
            JOptionPane.showMessageDialog(frame, message, title,
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static String merge(String a, String b) {
        if (a == null || a.trim().length() == 0 || Defect.UNSPECIFIED.equals(a))
            return b;
        else
            return a;
    }

    private String getUpdatedSegment(String key, int count) {
        if (count == 0)
            return null;
        else
            return "    " + resources.format("Defects_Updated." + key + "_FMT",
                    new Integer(count));
    }
}
