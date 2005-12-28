// Process Dashboard - Data Automation Tool for high-maturity processes
// Copyright (C) 2005 Software Process Dashboard Initiative
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

package net.sourceforge.processdash.tool.export.impl;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.processdash.data.DoubleData;
import net.sourceforge.processdash.data.SimpleData;
import net.sourceforge.processdash.tool.export.mgr.ExportManager;
import net.sourceforge.processdash.util.IteratorFilter;

public class DefaultDataExportFilter extends IteratorFilter {

    private boolean skipToDateData = true;

    private boolean skipZero = true;

    private boolean skipInfNaN = true;

    private boolean skipNodesAndLeaves = true;

    private boolean skipProcessAutoData = true;

    public DefaultDataExportFilter(Iterator dataElements) {
        super(dataElements);
        // don't call init yet, though. Let our kids call it.
    }

    public boolean isSkipNodesAndLeaves() {
        return skipNodesAndLeaves;
    }

    public void setSkipNodesAndLeaves(boolean skipNodesAndLeaves) {
        this.skipNodesAndLeaves = skipNodesAndLeaves;
    }

    public boolean isSkipProcessAutoData() {
        return skipProcessAutoData;
    }

    public void setSkipProcessAutoData(boolean skipProcessAutoData) {
        this.skipProcessAutoData = skipProcessAutoData;
    }

    public boolean isSkipToDateData() {
        return skipToDateData;
    }

    public void setSkipToDateData(boolean skipToDateData) {
        this.skipToDateData = skipToDateData;
    }

    public boolean isSkipZero() {
        return skipZero;
    }

    public void setSkipZero(boolean skipZeroInfNaN) {
        this.skipZero = skipZeroInfNaN;
    }

    public boolean isSkipInfNaN() {
        return skipInfNaN;
    }

    public void setSkipInfNaN(boolean skipInfNaN) {
        // disabled for now. Infinity and NaN get unusual treatment by the
        // number formatter we're using.
        // this.skipInfNaN = skipInfNaN;
        this.skipInfNaN = false;
    }

    public void init() {
        super.init();
    }

    protected boolean includeInResults(Object o) {
        ExportedDataValue v = (ExportedDataValue) o;
        if (isExportInstruction(v) || isSkippableToDateData(v)
                || isSkippableNodeLeaf(v) || isSkippableProcessAutoData(v)
                || isSkippableDoubleData(v))
            return false;
        else
            return true;
    }

    private boolean isExportInstruction(ExportedDataValue v) {
        return (v.getName().indexOf(ExportManager.EXPORT_DATANAME) != -1);
    }

    private boolean isSkippableToDateData(ExportedDataValue v) {
        return skipToDateData && v.getName().endsWith(" To Date");
    }

    private boolean isSkippableNodeLeaf(ExportedDataValue v) {
        return skipNodesAndLeaves && (v.getName().endsWith("/node") //
                || v.getName().endsWith("/leaf"));
    }

    private boolean isSkippableProcessAutoData(ExportedDataValue v) {
        Matcher m = PROCESS_AUTO_DATA_PATTERN.matcher(v.getName());
        return m.find();
    }

    private static Pattern PROCESS_AUTO_DATA_PATTERN;

    private static final String[] IGNORABLE_AUTO_DATA = { "_METRIC_NAME",
            "Last_Failure_Phase", "Use_Rollup", "FILES_XML", "Child_List",
            "Phase_List", "PROBE_NO_EDIT_INPUT", "_Rollup_List", "Normalized_",
            "Prototypical PSP Data" };
    static {
        StringBuffer pat = new StringBuffer();
        for (int i = 0; i < IGNORABLE_AUTO_DATA.length; i++)
            pat.append("|\\Q").append(IGNORABLE_AUTO_DATA[i]).append("\\E");
        PROCESS_AUTO_DATA_PATTERN = Pattern.compile(pat.substring(1));
    }

    private boolean isSkippableDoubleData(ExportedDataValue v) {
        if (skipZero || skipInfNaN) {
            SimpleData value = v.getSimpleValue();
            if (value instanceof DoubleData) {
                double d = ((DoubleData) value).getDouble();
                return ((skipZero && d == 0) || (skipInfNaN && (Double.isNaN(d) || Double
                        .isInfinite(d))));
            }
        }
        return false;
    }

}