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

package teamdash.wbs.columns;

import teamdash.wbs.WBSModel;
import teamdash.wbs.WBSNode;

public class WBSNodeColumn extends AbstractDataColumn {

    private WBSModel wbsModel;

    public WBSNodeColumn(WBSModel wbsModel) {
        this.wbsModel = wbsModel;
        this.columnName = "Name";
        this.columnID = "wbsNode";
        this.preferredWidth = 300;
    }

    public Class getColumnClass() { return WBSNode.class; }

    public boolean isCellEditable(WBSNode node) {
        return node != wbsModel.getRoot();
    }

    public Object getValueAt(WBSNode node) {
        return node;
    }

    public void setValueAt(Object aValue, WBSNode node) {}

}
