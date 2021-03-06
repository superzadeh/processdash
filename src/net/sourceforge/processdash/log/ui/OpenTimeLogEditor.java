// Copyright (C) 2007-2013 Tuma Solutions, LLC
// Process Dashboard - Data Automation Tool for high-maturity processes
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 3
// of the License, or (at your option) any later version.
//
// Additional permissions also apply; see the README-license.txt
// file in the project root directory for more information.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, see <http://www.gnu.org/licenses/>.
//
// The author(s) may be contacted at:
//     processdash@tuma-solutions.com
//     processdash-devel@lists.sourceforge.net

package net.sourceforge.processdash.log.ui;

import java.io.IOException;
import java.util.Enumeration;

import org.w3c.dom.Element;

import net.sourceforge.processdash.DashController;
import net.sourceforge.processdash.data.SimpleData;
import net.sourceforge.processdash.data.repository.DataRepository;
import net.sourceforge.processdash.hier.DashHierarchy;
import net.sourceforge.processdash.hier.Prop;
import net.sourceforge.processdash.hier.PropertyKey;
import net.sourceforge.processdash.log.time.RolledUpTimeLog;
import net.sourceforge.processdash.log.time.TimeLog;
import net.sourceforge.processdash.ui.web.TinyCGIBase;
import net.sourceforge.processdash.ui.web.reports.analysis.AnalysisPage;
import net.sourceforge.processdash.util.XMLUtils;

public class OpenTimeLogEditor extends TinyCGIBase {

    protected void doPost() throws IOException {
        parseFormData();
        super.doPost();
    }

    protected void writeContents() throws IOException {
        DashController.checkIP(env.get("REMOTE_ADDR"));
        String type = getParameter("type");
        if ("rollup".equals(type))
            showRollupTimeLog();
        else
            DashController.showTimeLogEditor(getPrefix());
        DashController.printNullDocument(out);
    }

    private void showRollupTimeLog() {
        TimeLog tl = new RolledUpTimeLog.FromResultSet(getDashboardContext(),
                getPrefix(), parameters);
        TimeLogEditor e = new TimeLogEditor(tl, getAugmentedHierarchy(), null,
                null);

        if (parameters.containsKey(
                RolledUpTimeLog.FromResultSet.MERGE_PREFIXES_PARAM))
            try {
                e.narrowToPath(getPrefix());
            } catch (IllegalArgumentException iae) {
                // this will be thrown if we attempt to narrow to a prefix that
                // does not exist.  It's best to ignore the error and proceed.
            }

        String displayPrefix = AnalysisPage.localizePrefix(getPrefix());
        String title = TimeLogEditor.resources.format(
                "Time_Log_Viewer_Window_Title_FMT", displayPrefix);
        e.setTitle(title);
    }

    private DashHierarchy getAugmentedHierarchy() {

        // create a copy of the standard hierarchy
        DashHierarchy result = new DashHierarchy(getPSPProperties().dataPath);
        result.copy(getPSPProperties());

        // find the leaves in the hierarchy, and augment each leaf with
        // supplementary children provided from data element specifications
        DataRepository data = getDataRepository();
        Enumeration<PropertyKey> leaves = result.getLeaves(PropertyKey.ROOT);
        while (leaves.hasMoreElements()) {
            PropertyKey leaf = leaves.nextElement();
            augmentHierarchy(result, leaf, data);
        }

        return result;
    }

    private void augmentHierarchy(DashHierarchy hier, PropertyKey node,
            DataRepository data) {
        String path = node.path();
        String dataName = DataRepository.createDataName(path,
            "Project_Component_Info");
        SimpleData val = data.getSimpleValue(dataName);
        if (val == null)
            return;

        try {
            Element xml = XMLUtils.parse(val.format()).getDocumentElement();
            augmentHierarchy(hier, node, xml);
        } catch (Exception e) {
        }
    }

    private void augmentHierarchy(DashHierarchy hier, PropertyKey parentNode,
            Element parentXml) {
        Prop parentProp = (Prop) hier.get(parentNode);
        if (parentProp == null)
            return;

        for (Element childXml : XMLUtils.getChildElements(parentXml)) {
            String childName = childXml.getAttribute("name");
            PropertyKey childNode = new PropertyKey(parentNode, childName);
            parentProp.addChild(childNode, -1);
            hier.put(childNode, new Prop());
            augmentHierarchy(hier, childNode, childXml);
        }
    }

}
