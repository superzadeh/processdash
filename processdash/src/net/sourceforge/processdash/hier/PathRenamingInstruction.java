// Copyright (C) 2005-2006 Tuma Solutions, LLC
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

package net.sourceforge.processdash.hier;

import java.util.Iterator;
import java.util.List;


public class PathRenamingInstruction {

    private String oldPath;

    private String newPath;

    public PathRenamingInstruction(String oldPath, String newPath) {
        this.oldPath = oldPath;
        this.newPath = newPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public String getOldPath() {
        return oldPath;
    }

    public static String renamePath(List renamingInstructions, String path) {
        if (path == null)
            return null;

        for (Iterator iter = renamingInstructions.iterator(); iter.hasNext();) {
            PathRenamingInstruction instr = (PathRenamingInstruction) iter
                    .next();
            if (Filter.pathMatches(path, instr.getOldPath(), true)) {
                path = instr.getNewPath()
                        + path.substring(instr.getOldPath().length());
            }
        }

        return path;
    }

}
