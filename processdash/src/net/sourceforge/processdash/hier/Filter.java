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

package net.sourceforge.processdash.hier;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

/**
 * This class implements a primitive filter for hierarchy paths. If the string
 * begins with one of the hierarchy prefixes in the Vector, or if the Vector is
 * null, it passes.
 */
public class Filter {

    public static boolean matchesFilter(Vector theFilter, String name) {
        // this method is preserved for binary compatibility reasons.
        return matchesFilter((Collection) theFilter, name);
    }

    public static boolean matchesFilter(Collection theFilter, String name) {
        if (theFilter == null)
            return true;
        for (Iterator iter = theFilter.iterator(); iter.hasNext();) {
            String oneFilter = (String) iter.next();
            if (pathMatches(name, oneFilter))
                return true;
        }
        return false;
    }

    public static boolean pathMatches(String path, String prefix) {
        return pathMatches(path, prefix, true);
    }

    public static boolean pathMatches(String path, String prefix,
            boolean includeChildren) {
        if (!path.startsWith(prefix))
            return false;

        if (path.length() > prefix.length()) {
            if (includeChildren) {
                if (path.charAt(prefix.length()) != '/')
                    return false;
            } else
                return false;
        }

        return true;
    }

}