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

import pspdash.DashController;
import pspdash.PSPDashboard;
import pspdash.TinyCGIBase;

public class showenv extends TinyCGIBase {

    /** Generate CGI script output. */
    protected void writeContents() throws IOException {
        if (parameters.get("config") != null) {
            out.print(DashController.getSettingsFileName());
            return;
        }

        out.println("<HTML><HEAD><TITLE>User settings</TITLE></HEAD>");
        out.println("<BODY><H1>Your settings</H1>");

        out.print("<P>Your configuration file is:<PRE>     ");
        out.println(DashController.getSettingsFileName());
        out.println("</PRE></P>");

        out.print("<P>Your data is located in the directory:<PRE>     ");
        out.println(PSPDashboard.getDefaultDirectory());
        out.println("</PRE></P>");

        out.println("</BODY></HTML>");
    }

}