// Copyright (C) 2007 Tuma Solutions, LLC
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

package net.sourceforge.processdash.ui;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTable;

import net.sourceforge.processdash.hier.ActiveTaskModel;
import net.sourceforge.processdash.i18n.Resources;
import net.sourceforge.processdash.ui.TaskNavigationSelector.QuickSelectTaskProvider;
import net.sourceforge.processdash.ui.lib.JFilterableTreeComponent;
import net.sourceforge.processdash.ui.lib.JOptionPaneActionHandler;
import net.sourceforge.processdash.ui.lib.JOptionPaneTweaker;
import net.sourceforge.processdash.ui.lib.TreeTableModel;
import net.sourceforge.processdash.util.RuntimeUtils;
import net.sourceforge.processdash.util.StringUtils;

public class QuickSelectTaskAction extends AbstractAction {

    QuickSelectTaskProvider taskProvider;
    ActiveTaskModel activeTaskModel;
    Component parentComponent;


    private static final Resources resources = Resources
            .getDashBundle("ProcessDashboard.NavSelector");

    public QuickSelectTaskAction() {
        super(resources.getString("Choose_Task.Menu_Name"));
        RuntimeUtils.assertMethod(JTable.class, "setRowSorter");
    }

    @Override
    public void putValue(String key, Object newValue) {
        if (TaskNavigationSelector.TASK_PROVIDER_KEY.equals(key))
            taskProvider = (QuickSelectTaskProvider) newValue;
        else if (TaskNavigationSelector.ACTIVE_TASK_MODEL_KEY.equals(key))
            activeTaskModel = (ActiveTaskModel) newValue;
        else if (TaskNavigationSelector.PARENT_COMPONENT_KEY.equals(key))
            parentComponent = (Component) newValue;
        else
            super.putValue(key, newValue);
    }

    public void actionPerformed(ActionEvent e) {
        try {
            selectTask();
        } catch (Exception ex) {
            Toolkit.getDefaultToolkit().beep();
            ex.printStackTrace();
        }
    }

    private void selectTask() {
        if (taskProvider == null || activeTaskModel == null)
            throw new IllegalStateException("Object not yet initialized");

        TreeTableModel tasks = taskProvider.getTaskSelectionChoices();
        final JFilterableTreeComponent selector = new JFilterableTreeComponent(
                tasks, resources.getString("Choose_Task.Find"), false);
        final Object nodeToSelect = taskProvider
                .getTreeNodeForPath(activeTaskModel.getPath());
        new JOptionPaneActionHandler().install(selector);
        Object[] message = new Object[] {
                resources.getString("Choose_Task.Prompt"), selector,
                new JOptionPaneTweaker.MakeResizable(),
                new JOptionPaneTweaker.GrabFocus(selector.getFilterTextField()),
                new JOptionPaneTweaker(50) {
                    public void doTweak(JDialog dialog) {
                        if (nodeToSelect != null)
                            selector.setSelectedNode(nodeToSelect);
                    }}
        };
        int userChoice = JOptionPane.showConfirmDialog(parentComponent, message,
            resources.getString("Choose_Task.Title"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (userChoice != JOptionPane.OK_OPTION)
            return;

        Object newTask = selector.getSelectedLeaf();
        if (newTask == null)
            return;

        String newPath = taskProvider.getPathForTreeNode(newTask);
        if (StringUtils.hasValue(newPath))
            activeTaskModel.setPath(newPath);
    }

}