// PSP Dashboard - Data Automation Tool for PSP-like processes
// Copyright (C) 1999  United States Air Force
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
// OO-ALC/TISHD
// Attn: PSP Dashboard Group
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC:  ken.raisor@hill.af.mil

import java.io.IOException;
import pspdash.data.DataRepository;
import pspdash.data.ResultSet;

public class table extends pspdash.TinyCGIBase {

    protected void writeContents() throws IOException {
        String title = (String) parameters.get("title");
        String style = (String) parameters.get("style");
        String units = (String) parameters.get("units");

        boolean skipRowHdr = (parameters.get("skipRowHdr") != null);
        boolean skipColHdr = (parameters.get("skipColHdr") != null);
        boolean includable = (parameters.get("includable") != null);

        if (!includable) {
            out.println("<HTML><HEAD>");
            if (title != null) out.println("<TITLE>" + title + "</TITLE>");
            if (style != null)
                out.println("<LINK REL='stylesheet' TYPE='text/css' HREF='" +
                            style + "'>");

            out.println("</HEAD><BODY>");
            if (title != null) out.println("<H1>" + title + "</H1>");
            if (units != null) out.println("<P>(" + units + ")</P>");
            out.println(style == null ? "<TABLE BORDER>" : "<TABLE>");
        }

        // get the data
        if (parameters.get("h0") == null) parameters.put("h0", "Project/Task");
        String prefix = (String) parameters.get("hierarchyPath");
        if (prefix == null) prefix = (String) env.get("PATH_TRANSLATED");
        ResultSet tableData =
            ResultSet.get(getDataRepository(), parameters, prefix,
                          getPSPProperties());
        if (parameters.get("transpose") != null)
            tableData = tableData.transpose();

        // print the table
        int firstRow = (skipColHdr ? 1 : 0);
        int firstCol = (skipRowHdr ? 1 : 0);
        for (int row=firstRow;  row <= tableData.numRows();  row++) {
            out.println("<TR>");
            for (int col=firstCol;  col <= tableData.numCols();  col++) {
                out.print("<TD" + getColAttributes(col) + ">");
                out.print(tableData.format(row, col));
                out.println("</TD>");
            }
            out.println("</TR>");
        }

        if (!includable)
            out.println("</TABLE></BODY></HTML>");
    }

    private String getColAttributes(int col) {
        String cssClass = (String) parameters.get("c" + col);
        if (cssClass == null) cssClass = (String) parameters.get("c");
        if (cssClass == null) return "";
        return " class='" + cssClass + "'";
    }

}
