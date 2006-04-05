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

package org.zaval.tools.i18n.translator;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;

import javax.swing.AbstractAction;


public class OpenTranslatorAction extends AbstractAction {

    private String filename;
    private Comparator filter;
    private Comparator sorter;
    private ActionListener saveListener;
    private ActionListener helpListener;
    
    public OpenTranslatorAction(String filename, String displayName, 
                                Comparator filter, 
                                Comparator sorter,
                                ActionListener saveListener,
                                ActionListener helpListener) {
        super(displayName);
        this.filename = filename;
        this.filter = filter;
        this.sorter = sorter;
        this.saveListener = saveListener;
        this.helpListener = helpListener;
    }

    public void actionPerformed(ActionEvent e) {
        try {
            if (sorter != null)
                BundleSet.DEFAULT_COMPARATOR = sorter;
            
            Main.main(new String[] { filename});
            Main.setFilter(filter);
            Main.setSaveListener(saveListener);
            Main.setHelpListener(helpListener);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }
}