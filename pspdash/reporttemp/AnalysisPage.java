// Process Dashboard - Data Automation Tool for high-maturity processes
// Copyright (C) 2003 Software Process Dashboard Initiative
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

package pspdash.reporttemp;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;

import pspdash.HTMLUtils;
import pspdash.Resources;
import pspdash.TinyCGIBase;
import pspdash.Translator;
import pspdash.data.DataRepository;
import pspdash.data.ListData;
import pspdash.data.SimpleData;
import pspdash.data.StringData;


public class AnalysisPage extends TinyCGIBase {

    protected static Resources resources = Resources.getDashBundle("pspdash.Analysis");

    protected static final String PATH_TO_REPORTS = "../";


    protected ListData getProcessList(String listName) {
        String dataName = DataRepository.createDataName(getPrefix(), listName);
        SimpleData val = getDataRepository().getSimpleValue(dataName);
        if (val instanceof ListData)
            return (ListData) val;
        else if (val instanceof StringData)
            return ((StringData) val).asList();
        else
            return new ListData();
    }

    protected List getProcessListPlain(String name) {
        ListData list = getProcessList(name);
        List result = new LinkedList();
        for (int i = 0;  i < list.size();   i++)
            result.add(list.get(i));
        return result;
    }

    protected String getProcessString(String stringName) {
        String dataName = DataRepository.createDataName(getPrefix(), stringName);
        SimpleData val = getDataRepository().getSimpleValue(dataName);
        return val == null ? "" : val.format();
    }

//    protected boolean metricIsDefined(String name) {
//        String dataName = DataRepository.createDataName(getPrefix(), name);
//        return getDataRepository().getValue(dataName) != null;
//    }
//
//    protected String interpolate(String text) {
//        return StringUtils.interpolate(this, text);
//    }
//    protected void interpOut(String text) {
//        out.write(interpolate(text));
//    }
//
//    protected String fmtArg(String name, Object value) {
//        return name + "=" + HTMLUtils.urlEncode((String) value);
//    }
//
//    protected String getSizeAbbrLabel() {
//        String sizeMetric = getProcessString("SIZE_METRIC_NAME_ABBR");
//        String displayName = Translator.translate(sizeMetric);
//        return displayName;
//    }
//
//    protected String getCumPhaseSum(ListData phases, String thruPhase, String dataElem) {
//        StringBuffer result = new StringBuffer();
//        result.append("sumFor(\"").append(escData(dataElem)).append("\", \"\u0002");
//        for (int i = 0;   i < phases.size();   i++) {
//            String phase = (String) phases.get(i);
//            result.append(escData(phase)).append('\u0002');
//            if (phase.equals(thruPhase))
//                break;
//        }
//        result.append("\")");
//        return result.toString();
//    }
//
//    protected String getCumPhaseSumPct(ListData phases, String thruPhase, String dataElem) {
//        StringBuffer result = new StringBuffer();
//        result.append(getCumPhaseSum(phases, thruPhase, dataElem))
//            .append(" / [").append(escData(dataElem)).append("]");
//        return result.toString();
//    }
//
    protected String getAggrSizeLabel() {
        return Translator.translate
            (getProcessString("AGGR_SIZE_METRIC_NAME_ABBR"));
    }


    private static final String VAR_START_PAT = "${";
    private static final String VAR_END_PAT = "}";
    public static String interpolate(String s, boolean escHTML) {
        int max_nesting = 1000;
        while (max_nesting-- > 0) {
            int beg = s.indexOf(VAR_START_PAT);
            if (beg == -1) return s;

            int end = s.indexOf(VAR_END_PAT, beg);
            if (end == -1) return s;

            String var = s.substring(beg+VAR_START_PAT.length(), end);
            String replacement = resources.getString(var);
            if (escHTML)
                replacement = HTMLUtils.escapeEntities(replacement);
            s = s.substring(0, beg) + replacement +
                s.substring(end+VAR_END_PAT.length());
        }
        throw new IllegalArgumentException
            ("Infinite recursion when interpolating.");
    }

    protected void printRes(String txt) {
        out.println(interpolate(txt, true));
    }
    protected static String urlEncode(String rem) {
        try {
            return URLEncoder.encode(rem, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // can't happen
            return rem;
        }
    }
}
