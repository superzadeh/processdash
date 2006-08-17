// Copyright (C) 2006 Tuma Solutions, LLC
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

package net.sourceforge.processdash.ui.web.reports.snippets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sourceforge.processdash.data.DataContext;
import net.sourceforge.processdash.i18n.Resources;
import net.sourceforge.processdash.i18n.Translator;
import net.sourceforge.processdash.net.cms.SnippetDataEnumerator;
import net.sourceforge.processdash.net.cms.TranslatingAutocompleter;
import net.sourceforge.processdash.process.ProcessUtil;
import net.sourceforge.processdash.ui.web.TinyCGIBase;
import net.sourceforge.processdash.util.HTMLUtils;
import net.sourceforge.processdash.util.StringUtils;

public class TableOfPhaseMetrics extends TinyCGIBase {

    private static final String DISPLAY_NAME_ATTR = "DisplayName";

    private static final String DATA_NAME_ATTR = "DataName";

    private static final String ITEM_TYPE = "Metric";

    private static final String HEADING_PARAM = "Heading";

    private static final String LABEL_PARAM = "Label";

    private static final Resources resources = Resources
            .getDashBundle("Analysis.MetricsPhaseTable");

    protected void writeContents() throws IOException {
        DataContext dataContext = getDataContext();

        // retrieve the heading and label the user wants displayed
        String heading = getParameter(HEADING_PARAM);
        String label = getParameter(LABEL_PARAM);

        // retrieve the list of columns the user wants to display
        List columns = new ArrayList();
        for (int i = 0; i < COLUMNS.length; i++) {
            if (COLUMNS[i].isShowing(parameters))
                columns.add(COLUMNS[i]);
        }
        if (dataContext.getSimpleValue("Rollup Tag") != null) {
            columns.remove(MetricsTableColumn.TO_DATE);
            columns.remove(MetricsTableColumn.TO_DATE_PCT);
        }
        if (columns.isEmpty()) {
            out.write("<!-- no columns selected;  no table to display -->\n\n");
            return;
        }

        ProcessUtil procUtil = new ProcessUtil(getDataContext());

        // retrieve the list of phases the user wants to display
        String phaseList = getSelectedPhaseList();
        List phases = null;
        for (int i = 0; i < PHASES.length; i++) {
            if (PHASES[i][0].equals(phaseList)) {
                phases = procUtil.getProcessListPlain(PHASES[i][1]);
                break;
            }
        }
        if (phases == null)
            // default to all phases
            phases = procUtil.getProcessListPlain(PHASES[0][1]);

        // retrieve the list of metrics the user wants to display
        Map[] metrics = SnippetDataEnumerator.getEnumeratedValues(parameters,
                ITEM_TYPE);
        if (metrics == null) {
            out.write("<!-- no metrics selected;  no table to display -->\n\n");
            return;
        }

        // write out heading if requested
        if (StringUtils.hasValue(heading)) {
            out.write("<h2>");
            out.write(HTMLUtils.escapeEntities(heading));
            out.write("</h2>\n\n");
        }

        // write the header row of the table
        out.write("<p><table><tr><th align=\"left\">");
        if (label != null)
            out.write(esc(label));
        out.write("</th>\n");

        if (metrics.length > 1) {
            for (int i = 0; i < metrics.length; i++) {
                out.write("<th");
                if (i == 0) out.write(MetricsTableColumn.PADDING_LEFT);
                out.write(" colspan=\"");
                out.write(Integer.toString(columns.size()));
                out.write("\">");
                String metricName = (String) metrics[i].get(DISPLAY_NAME_ATTR);
                if (metricName == null || metricName.trim().length() == 0) {
                    String dataName = (String) metrics[i].get(DATA_NAME_ATTR);
                    metricName = TranslatingAutocompleter.translateDataName(dataName);
                }
                out.write(esc(metricName));
                out.write("</th>\n");
            }
            out.write("</tr>\n<tr><th></th>");
        }
        for (int j = 0; j < metrics.length; j++) {
            boolean pad = true;
            for (Iterator i = columns.iterator(); i.hasNext();) {
                MetricsTableColumn col = (MetricsTableColumn) i.next();
                col.writeHeader(out, resources, pad);
                pad = false;
            }
        }
        out.write("</tr>\n");

        // write a table row for each process phase
        for (Iterator i = phases.iterator(); i.hasNext();) {
            String phase = (String) i.next();
            writeTableRow(phase, metrics, columns, dataContext, procUtil);
        }

        if (parameters.containsKey("ShowTotalRow")
                && ALL_PHASES.equals(phaseList))
            writeTableRow(null, metrics, columns, dataContext, procUtil);

        out.write("</table></p>\n\n");
    }

    private void writeTableRow(String phase, Map[] metrics, List columns,
            DataContext data, ProcessUtil procUtil) throws IOException {
        out.write("<tr><td>");
        if (phase == null)
            out.write(resources.getHTML("Total"));
        else
            out.write(esc(getPhaseDisplayName(procUtil, phase)));
        out.write("</td>\n");

        String[] extraAttrs = new String[columns.size()];
        Arrays.fill(extraAttrs, "");
        if (metrics.length > 1) {
            extraAttrs[0] = " ";
        }

        for (int m = 0; m < metrics.length; m++) {
            Map metric = metrics[m];
            String dataName = (String) metric.get(DATA_NAME_ATTR);
            if (phase != null)
                dataName = phase + "/" + dataName;
            boolean pad = true;
            for (Iterator i = columns.iterator(); i.hasNext();) {
                MetricsTableColumn col = (MetricsTableColumn) i.next();
                if (phase == null
                    && (col == MetricsTableColumn.ACTUAL_PCT
                            || col == MetricsTableColumn.TO_DATE_PCT))
                    // don't write "%" columns on the total row.
                    out.write("<td></td>\n");
                else
                    col.writeCell(out, data, dataName, pad);
                pad = false;
            }
        }
        out.write("</tr>\n");
    }

    private String getPhaseDisplayName(ProcessUtil procUtil, String phase) {
        String phaseName = procUtil.getProcessString(phase + "/Phase_Long_Name");
        if (StringUtils.hasValue(phaseName))
            return phaseName;
        else
            return Translator.translate(phase);
    }

    private String getSelectedPhaseList() {
        String result = getParameter("PhaseGroup");
        if (result == null)
            result = ALL_PHASES;
        return result;
    }

    private String esc(String s) {
        return HTMLUtils.escapeEntities(s);
    }

    private static final MetricsTableColumn[] COLUMNS = {
            MetricsTableColumn.PLAN,
            MetricsTableColumn.ACTUAL,
            MetricsTableColumn.ACTUAL_PCT,
            MetricsTableColumn.TO_DATE,
            MetricsTableColumn.TO_DATE_PCT, };

    private static final String ALL_PHASES = "All";

    private static final String[][] PHASES = {
        { ALL_PHASES, "Phase_List"},
        { "Appraisal", "Appraisal_Phase_List"},
        { "Failure", "Failure_Phase_List" },
        { "Quality", "Quality_Phase_List" },
    };

}