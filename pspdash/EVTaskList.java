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


package pspdash;

import java.util.*;
import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import javax.swing.tree.TreePath;

import pspdash.data.DataRepository;

public class EVTaskList extends AbstractTreeTableModel
    implements EVTask.Listener
{

    protected String taskListName;
    protected DataRepository data;
    protected PSPProperties hierarchy;
    protected EVSchedule schedule;

    public EVTaskList(String taskListName,
                      DataRepository data,
                      PSPProperties hierarchy,
                      boolean willNeedChangeNotification) {
        super(null);
        schedule = new EVSchedule();

        this.taskListName = taskListName;
        this.data = data;
        this.hierarchy = hierarchy;
        if (willNeedChangeNotification)
            listeners = Collections.synchronizedList(new ArrayList());
        else
            listeners = null;

        root = new EVTask(taskListName);
        ((EVTask) root).add
            (new EVTask("/Project", data, hierarchy,
                        willNeedChangeNotification ? this : null));
    }

    public EVSchedule getSchedule() { return schedule; }


    //////////////////////////////////////////////////////////////////////
    /// Change notification support
    //////////////////////////////////////////////////////////////////////

    /** Defines the interface for an object that listens to changes in
     * the <b>values</b> in an EVTaskList.
     *
     * Note: this only allows for notification of changes to values in
     * the task list - not to the structure of the task list.  To be
     * notified of such changes, register as a TreeModelListener.
     */
    public interface Listener {
        /** Data has changed for one node in the task list */
        public void evNodeChanged(Event e);
    }

    /** Notification event object */
    public class Event {
        private EVTask node;
        public Event(EVTask node) { this.node = node; }
        public EVTask getNode() { return node; }
        public EVTaskList getList() { return EVTaskList.this; }
    }

    List listeners = null;
    public void addEVTaskListListener(Listener l)    { listeners.add(l);    }
    public void removeEVTaskListListener(Listener l) { listeners.remove(l); }
    public void fireEVNodeChanged(EVTask node) {
        if (listeners != null  && listeners.size() > 0) {
            Event e = new Event(node);
            for (int i = 0;  i < listeners.size();  i++)
                ((Listener) listeners.get(i)).evNodeChanged(e);
        }
    }
    public void recalc() { ((EVTask) root).recalc(schedule); }



    //////////////////////////////////////////////////////////////////////
    /// TreeTableModel support
    //////////////////////////////////////////////////////////////////////


    /** Names of the columns in the TreeTableModel. */
    protected static String[] colNames = {
        "Project/Task", "PT", "PV", "CPT", "CPV", "Plan Date", "Date", "EV" };
    public static int[] colWidths = {
        175,             50,   40,   50,    40,    80,          80,     40 };
    public static String[] toolTips = {
        null,
        "Planned Time (hours:minutes)",
        "Planned Value",
        "Cumulative Planned Time (hours:minutes)",
        "Cumulative Planned Value",
        "Planned Completion Date",
        "Actual Completion Date",
        "Actual Earned Value" };

    protected static final int TASK_COLUMN           = 0;
    protected static final int PLAN_TIME_COLUMN      = 1;
    protected static final int PLAN_VALUE_COLUMN     = 2;
    protected static final int PLAN_CUM_TIME_COLUMN  = 3;
    protected static final int PLAN_CUM_VALUE_COLUMN = 4;
    protected static final int PLAN_DATE_COLUMN      = 5;
    protected static final int DATE_COMPLETE_COLUMN  = 6;
    protected static final int VALUE_EARNED_COLUMN   = 7;

    /** Types of the columns in the TreeTableModel. */
    static protected Class[]  colTypes = {
        TreeTableModel.class,   // project/task
        String.class,           // planned time
        String.class,           // planned value
        String.class,           // planned cumulative time
        String.class,           // planned cumulative value
        Date.class,             // planned date
        Date.class,             // date
        String.class };         // earned value


    //
    // The TreeModel interface
    //

    /** Returns the number of children of <code>node</code>. */
    public int getChildCount(Object node) {
        return ((EVTask) node).getNumChildren();
    }

    /** Returns the child of <code>node</code> at index <code>i</code>. */
    public Object getChild(Object node, int i) {
        return ((EVTask) node).getChild(i);
    }

    /** Returns true if the passed in object represents a leaf, false
     *  otherwise. */
    public boolean isLeaf(Object node) {
        return ((EVTask) node).isLeaf();
    }

    /** Returns true if the value in column <code>column</code> of object
     *  <code>node</code> is editable. */
    public boolean isCellEditable(Object node, int column) {
        switch (column) {
        case TASK_COLUMN:
            // The column with the tree in it should be editable; this
            // causes the JTable to forward mouse and keyboard events
            // in the Tree column to the underlying JTree.
            return true;

        case PLAN_TIME_COLUMN:
            return ((EVTask) node).plannedTimeIsEditable();

        case DATE_COMPLETE_COLUMN:
            return ((EVTask) node).completionDateIsEditable();
        }
        return false;
    }


    //
    //  The TreeTableNode interface.
    //

    /** Returns the number of columns. */
    public int getColumnCount() { return colNames.length; }

    /** Returns the name for a particular column. */
    public String getColumnName(int column) { return colNames[column]; }

    /** Returns the class for the particular column. */
    public Class getColumnClass(int column) { return colTypes[column]; }

    /** Returns the value of the particular column. */
    public Object getValueAt(Object node, int column) {
        EVTask n = (EVTask) node;
        switch (column) {
        case TASK_COLUMN:           return n.getName();
        case PLAN_TIME_COLUMN:      return n.getPlanTime();
        case PLAN_VALUE_COLUMN:     return n.getPlanValue();
        case PLAN_CUM_TIME_COLUMN:  return n.getCumPlanTime();
        case PLAN_CUM_VALUE_COLUMN: return n.getCumPlanValue();
        case PLAN_DATE_COLUMN:      return n.getPlanDate();
        case DATE_COMPLETE_COLUMN:  return n.getActualDate();
        case VALUE_EARNED_COLUMN:   return n.getValueEarned();
        }
        return null;
    }

    /** Set the value at a particular row/column */
    public void setValueAt(Object aValue, Object node, int column) {
        System.out.println("setValueAt("+aValue+","+node+","+column+")");
        EVTask n = (EVTask) node;
        switch (column) {
        case PLAN_TIME_COLUMN:      n.setPlanTime(aValue);   break;
        case DATE_COMPLETE_COLUMN:  n.setActualDate(aValue); break;
        }
    }

}
