// Copyright (C) 2002-2010 Tuma Solutions, LLC
// Team Functionality Add-ons for the Process Dashboard
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

package teamdash.wbs;

import javax.swing.event.TableModelEvent;
import javax.swing.table.TableModel;

public class WBSModelEvent extends TableModelEvent {

    private boolean isExpansionOnly;

    public WBSModelEvent(TableModel source, boolean isExpansionOnly) {
        super(source);
        this.isExpansionOnly = isExpansionOnly;
    }

    public WBSModelEvent(TableModel source, int firstRow, int lastRow,
            int column, int type, boolean isExpansionOnly) {
        super(source, firstRow, lastRow, column, type);
        this.isExpansionOnly = isExpansionOnly;
    }

    public boolean isExpansionOnly() {
        return isExpansionOnly;
    }

    public static boolean isStructuralChange(TableModelEvent e) {
        if (e instanceof WBSModelEvent) {
            WBSModelEvent wbsEvent = (WBSModelEvent) e;
            if (wbsEvent.isExpansionOnly())
                return false;
        }
        return true;
    }
}
