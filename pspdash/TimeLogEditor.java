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

import javax.swing.*;
import javax.swing.event.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import javax.swing.tree.*;
import javax.swing.text.JTextComponent;
import pspdash.data.DataRepository;
import pspdash.data.NumberData;
import pspdash.data.DoubleData;


public class TimeLogEditor extends Object implements TreeSelectionListener, TableValidator
{
    /** Class Attributes */
    protected JFrame          frame;
    protected JTree           tree;
    protected PropTreeModel   treeModel;
    protected PSPProperties   useProps;
    protected PSPDashboard    dashboard = null;
    protected ValidatingTable table;
    protected UserWarning     warnUser;

    protected Hashtable       postedChanges = new Hashtable();

    JTextField toDate       = null;
    JTextField fromDate     = null;
    TimeLog    tl           = new TimeLog();
    Vector     currentLog   = new Vector();
    String     validateCell = null;

    static final long DAY_IN_MILLIS = 24 * 60 * 60 * 1000;


    //
    // member functions
    //
    private void debug(String msg) {
        System.out.println("TimeLogEditor:" + msg);
    }


                                // constructor
    public TimeLogEditor(PSPDashboard dash,
                         ConfigureButton button,
                         PSPProperties props) {
        dashboard        = dash;
        JPanel   panel   = new JPanel(true);

        useProps       = props;
        try {
            tl.read (dashboard.getTimeLog());
        } catch (IOException e) {}

        frame = new JFrame("TimeLogEditor");
        frame.setTitle("TimeLogEditor");
        frame.setIconImage(java.awt.Toolkit.getDefaultToolkit().createImage
                           (getClass().getResource("icon32.gif")));
        frame.getContentPane().add("Center", panel);
        frame.setBackground(Color.lightGray);

        /* Create the JTreeModel. */
        treeModel = new PropTreeModel (new DefaultMutableTreeNode ("root"), null);

        /* Create the tree. */
        tree = new JTree(treeModel);
        treeModel.fill (useProps);
        setTimes ();
        tree.expandRow (0);
        tree.setShowsRootHandles (true);
        tree.setEditable(false);
        tree.addTreeSelectionListener (this);
        tree.setRootVisible(false);
        tree.setRowHeight(-1);      // Make tree ask for the height of each row.

        /* Put the Tree in a scroller. */
        JScrollPane sp = new JScrollPane
            (ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
             ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        sp.setPreferredSize(new Dimension(300, 300));
        sp.getViewport().add(tree);

        /* And show it. */
        panel.setLayout(new BorderLayout());
        panel.add("North", constructFilterPanel());
        panel.add("West", sp);
        panel.add("Center", constructEditPanel());
        panel.add("South", constructControlPanel());

        frame.addWindowListener( new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                frame.setVisible (false);
            }
        });

        applyFilter(false);
        cancelPostedChanges();
        frame.pack();
        frame.show();
    }


    void postTimeChange (PropertyKey key, long deltaMinutes) {
        //log the change for future action
        if (deltaMinutes == 0) // if no change, return
            return;
        Long delta = (Long)postedChanges.get (key);
        if (delta == null)
            delta = new Long (deltaMinutes);
        else
            delta = new Long (delta.longValue() + deltaMinutes);
        Object a = postedChanges.put (key, delta);
    }


    void cancelPostedChanges () {
        postedChanges.clear ();
    }


    // ApplyPostedChanges takes the changes made to the TimeLogEditor and
    // applies them to the data repository.
    void applyPostedChanges () {
        long l;

        // If there are any changes, apply them.
        if (postedChanges.size() > 0) {
            DataRepository data = dashboard.getDataRepository();
            String thePath;

            // Get the posted changes (keys) and loop through them all
            Enumeration keys = postedChanges.keys();
            while (keys.hasMoreElements ()) {
                PropertyKey k = (PropertyKey)keys.nextElement ();

                // Store the change's key information into k, and data into l.
                // Remove the change from the posted changes.
                l = ((Long)postedChanges.remove (k)).longValue();
                if (l != 0) {
                    thePath = k.path();

                    // Extract the data from the data repository that corresponds
                    // to the change we are currently applying.
                    NumberData pt = (NumberData)data.getValue (thePath + "/Time");

                    l += (long)pt.getInteger ();

                    // If the data is editable, make the change.
                    if (pt.isEditable ()) {
                        data.putValue (thePath + "/Time", new DoubleData (l));
                    }

                    else {
                        // The data repository does not seem to keep track of the edit
                        // status, so change the data anyway.
                        pt.setEditable(true);

                        // Apply the change to the proper place in the data repository,
                        // and restore the edit status.
                        data.putValue (thePath + "/Time", new DoubleData (l));
                        pt.setEditable(false);
                    }
                }
            }
        }
        postedChanges.clear ();
    }


    protected long parseTime (String s) {
        int colon = s.indexOf (":");
        long lv = 0;
        if (colon >= 0) {
            try {
                lv = 60 * Long.valueOf (s.substring (0, colon)).longValue();
            } catch (Exception e) { }
            try {
                lv += Long.valueOf (s.substring (colon + 1)).longValue();
            } catch (Exception e) { }
        } else {
            try {
                lv = Long.valueOf (s).longValue();
            } catch (Exception e) { }
        }
        return lv;
    }

    protected String formatTime (long t) {
        if (t < 60)
            return String.valueOf(t);
        int min = (int) (t % 60);
        if (min < 10)
            return String.valueOf (t / 60) + ":0" + String.valueOf (min);
        return String.valueOf (t / 60) + ":" + String.valueOf (min);
    }

    public long setTimes (Object node, Hashtable times) {
        long t = 0;                 // time for this node

                                    // recursively compute total time for each
                                    // child and add total time for this node.
        for (int i = 0; i < treeModel.getChildCount (node); i++) {
            t += setTimes (treeModel.getChild (node, i), times);
        }

                                    // fetch and add time spent in this node
        Object [] path = treeModel.getPathToRoot((TreeNode) node);
        Long l = (Long) times.get(treeModel.getPropKey (useProps, path));
        if (l != null)
            t += l.longValue();

                                      // display the time next to the node name
                                      // in the tree display
        String s = (String) ((DefaultMutableTreeNode)node).getUserObject();
        int index = s.lastIndexOf ("=");
        if (index < 0)
            s = s + " = " + formatTime (t);
        else
            s = s.substring (0, index) + "= " + formatTime (t);
        ((DefaultMutableTreeNode)node).setUserObject(s);

        return t;
    }

    public void setTimes () {
        Date fd = ((fromDate == null) ? null :
                   DateFormatter.parseDate (fromDate.getText()));
        Date td = ((toDate == null) ? null :
                   DateFormatter.parseDate (toDate.getText()));
        if (td != null)             // need to add a day so search is inclusive
            td = new Date (td.getTime() + DAY_IN_MILLIS);

        setTimes (treeModel.getRoot(), tl.getTimes(fd, td));

        treeModel.nodeStructureChanged((TreeNode)treeModel.getRoot());
        tree.repaint(tree.getVisibleRect());
    }

    void applyFilter (boolean resetTimes) {
        PropertyKey key = null;
        Date fd = DateFormatter.parseDate (fromDate.getText());
        Date td = DateFormatter.parseDate (toDate.getText());
        if (td != null)             // need to add a day so search is inclusive
            td = new Date (td.getTime() + DAY_IN_MILLIS);
        DefaultMutableTreeNode selected = getSelectedNode();
        if (selected != null) {
            key = treeModel.getPropKey (useProps, selected.getPath());
        }

        //if editing, stop edits
        if (table.table.isEditing())
            table.table.editingStopped
                (new ChangeEvent("Applying filter: Stop edit"));

        // apply the filter and load the vector (and the table)
        VTableModel model = (VTableModel)table.table.getModel();
        Object[] row;
        TimeLogEntry tle;
        Enumeration filt = tl.filter (key, fd, td);
        currentLog.removeAllElements();
        model.setNumRows (0);
        while (filt.hasMoreElements()) {
            tle = (TimeLogEntry)filt.nextElement();
            currentLog.addElement (tle);
            row = new Object[]
                {tle.key.path(),
                 DateFormatter.formatDateTime (tle.createTime),
                 formatTime (tle.minutesElapsed),
                 formatTime (tle.minutesInterrupt)};
            model.addRow(row);
        }
        table.doResizeRepaint();
        if (resetTimes)
            setTimes();
    }

    // This method implements utilTableValidator
    public boolean validate (int        id,
                             int        row,
                             int        col,
                             String     newValue) {
        if (validateCell != null)
            return false;
        boolean rv = true;
        TimeLogEntry tle;
        try {
            tle = (TimeLogEntry)currentLog.elementAt (row);
        } catch (Exception e) { return false; }
        validateCell = "" + row + "," + col;
        switch (col) {
        case 0:                     //Logged To (key) (must exist in hierarchy)
            PropertyKey key = useProps.findExistingKey (newValue);
            if (key == null) {
                rv = false;
                table.table.setValueAt (tle.key.path(), row, col);
            } else {
                long deltaMinutes = parseTime((String)table.table.getValueAt (row, 2));
                postTimeChange (key, deltaMinutes);
                postTimeChange (tle.key, - deltaMinutes);
                tle.key = key;
            }
            break;
        case 1:                     //createTime (must be valid date)
            Date d = DateFormatter.parseDateTime (newValue);
            if (d == null) {
                rv = false;
                table.table.setValueAt (DateFormatter.formatDateTime (tle.createTime),
                                        row, col);
            } else
                tle.createTime = d;
            break;
        case 2:                     //minutesElapsed (must be number >= 0)
            try {
                long lv = parseTime (newValue);//Long.valueOf (newValue).longValue();
                long deltaMinutes = tle.minutesElapsed;
                if (lv >= 0)
                    tle.minutesElapsed = lv;
                else {
                    tle.minutesElapsed = 0;
                    rv = false;
                }
                postTimeChange (tle.key, tle.minutesElapsed - deltaMinutes);
                table.table.setValueAt (formatTime (tle.minutesElapsed), row, col);
            } catch (Exception e) { rv = false; }
            break;
        case 3:                     //minutesInterrupt (must be number >= 0)
            try {
                long lv = parseTime (newValue);//Long.valueOf (newValue).longValue();
                if (lv >= 0)
                    tle.minutesInterrupt = lv;
                else {
                    tle.minutesInterrupt = 0;
                    rv = false;
                }
                table.table.setValueAt (formatTime (tle.minutesInterrupt), row, col);
            } catch (Exception e) { rv = false; }
            break;
        }
        setTimes ();
        validateCell = null;
        return rv;
    }


    private JPanel constructFilterPanel () {
        JPanel  retPanel = new JPanel(false);
        JButton button;
        JLabel  label;

        label = new JLabel ("Filter: From ");
        retPanel.add (label);

        fromDate = new JTextField ("", 10);
        DateChangeAction l = new DateChangeAction (fromDate);
        fromDate.addActionListener (l);
        fromDate.addFocusListener (l);
        retPanel.add (fromDate);

        label = new JLabel (" To ");
        retPanel.add (label);

        toDate = new JTextField ("", 10);
        l = new DateChangeAction (toDate);
        toDate.addActionListener (l);
        toDate.addFocusListener (l);
        retPanel.add (toDate);

        button = new JButton ("Apply Filter");
        button.addActionListener (new ActionListener () {
            public void actionPerformed(ActionEvent e) { applyFilter (true); }
        });
        retPanel.add (button);

        return retPanel;
    }

    public void addRow (TimeLogEntry tle) {
        Object aRow[] = new Object[]
            {tle.key.path(),
             DateFormatter.formatDateTime (tle.createTime),
             String.valueOf (tle.minutesElapsed),
             String.valueOf (tle.minutesInterrupt)};

        ((VTableModel)table.table.getModel()).addRow(aRow);
        currentLog.addElement (tl.add (tle));
        setTimes ();
    }

    public void addRow () {
        JTable aTable = table.table;
        VTableModel model = (VTableModel)aTable.getModel();
        TimeLogEntry tle = null;
        int rowBasedOn;
        DefaultMutableTreeNode selected;

                            // try to base new row on the selected table row
        if ((rowBasedOn = aTable.getSelectedRow()) != -1)
            ; // nothing to do here .. just drop out of "else if" tree

                              // else try to base new row on current editing row
        else if ((rowBasedOn = aTable.getEditingRow()) != -1)
            aTable.editingStopped (new ChangeEvent("Adding row: Stop edit"));

                              // else try to base new row on current tree selection
        else if ((selected = getSelectedNode()) != null) {
            PropertyKey key = treeModel.getPropKey(useProps, selected.getPath());
            tle = new TimeLogEntry (key, new Date(), 0, 0);

        } else              // else try to base new row on last row of table
            rowBasedOn = currentLog.size() - 1;


        Object aRow[];
        if (tle == null) {
            if (rowBasedOn == -1) {   // create 'blank'
                tle = new TimeLogEntry (new PropertyKey (PropertyKey.ROOT),
                                        new Date(), 0, 0);
            } else {          // base it on rowBasedOn
                TimeLogEntry tle2 = (TimeLogEntry)currentLog.elementAt (rowBasedOn);
                tle = new TimeLogEntry (new PropertyKey (tle2.key),
                                        new Date (tle2.createTime.getTime()),
                                        tle2.minutesElapsed, tle2.minutesInterrupt);
            }
        }
        aRow = new Object[]
            {tle.key.path(),
             DateFormatter.formatDateTime (tle.createTime),
             String.valueOf (tle.minutesElapsed),
             String.valueOf (tle.minutesInterrupt)};
        model.addRow(aRow);
        currentLog.addElement (tl.add (tle));
        setTimes ();
        postTimeChange (tle.key, tle.minutesElapsed);
    }

    public void deleteSelectedRow () {
        JTable aTable = table.table;
        VTableModel model = (VTableModel)aTable.getModel();
        int rowBasedOn = aTable.getSelectedRow();
        if (rowBasedOn == -1) {
            if ((rowBasedOn = aTable.getEditingRow()) == -1)
                return;
            else
                aTable.editingStopped (new ChangeEvent("Deleting row: Stop edit"));
        }
        TimeLogEntry tle = (TimeLogEntry)currentLog.elementAt (rowBasedOn);
        model.removeRow (rowBasedOn);
        try {
            currentLog.removeElementAt (rowBasedOn);
        } catch (Exception e) {}
        tl.remove (tle);
        setTimes ();
        postTimeChange (tle.key, - tle.minutesElapsed);
    }

    protected void summarize() {
        TimeLogEntry tle, tle2;
        boolean      merged = false;
        for (int i = 0; i < currentLog.size(); i++) {
            tle = (TimeLogEntry)currentLog.elementAt(i);
            for (int j = currentLog.size() - 1; j > i; j--) {
                tle2 = (TimeLogEntry)currentLog.elementAt(j);
                if (tle.key.key().equals (tle2.key.key())) {
                    //merge into tle and delete tle2
                    merged = true;
                    tle.minutesElapsed   += tle2.minutesElapsed;
                    tle.minutesInterrupt += tle2.minutesInterrupt;
                    System.err.println("merging:"+tle+"+"+tle2);
                    tl.remove (tle2);
                    try {                 // make sure that we only merge once
                        currentLog.removeElementAt (j);
                    } catch (Exception e) {}
                }
            }
        }
        if (merged)
            applyFilter (true);
    }

    protected void summarizeWarning() {
        if (warnUser != null)
            warnUser.show();
        else
            warnUser = new UserWarning (frame, "User Warning");
    }

    private JPanel constructEditPanel () {
        JPanel  retPanel = new JPanel(false);
        JButton button;

        retPanel.setLayout(new BorderLayout());
        table = new ValidatingTable
            (new Object[] {"Logged To", "Start T", "Delta", "Int"},
             null,
             new int[] {250, 180, 45, 40},
             new String[] {"What the time is logged to",
                           "The start time(minutes)",
                           "The elapsed time(minutes)",
                           "The interrupt time(minutes)"},
             null, this, 0, true, null, null);
        retPanel.add ("Center", table);

        JPanel btnPanel = new JPanel(false);
        button = new JButton ("Add");
        button.addActionListener (new ActionListener () {
            public void actionPerformed(ActionEvent e) { addRow(); }
        });
        btnPanel.add (button);

        button = new JButton ("Delete");
        button.addActionListener (new ActionListener () {
            public void actionPerformed(ActionEvent e) { deleteSelectedRow(); }
        });
        btnPanel.add (button);

        button = new JButton ("Summarize");
        button.addActionListener (new ActionListener () {
            public void actionPerformed(ActionEvent e) { summarizeWarning(); }
        });
        btnPanel.add (button);

        retPanel.add ("South", btnPanel);

        return retPanel;
    }

    private JPanel constructControlPanel () {
        JPanel  retPanel = new JPanel(false);
        JButton button;

        button = new JButton ("Reload");
        retPanel.add (button);
        button.addActionListener (new ReloadAction ());

        button = new JButton ("Apply");
        retPanel.add (button);
        button.addActionListener (new SaveAction ());

        return retPanel;
    }

    public void reload() {
        try {                       // re-read time log
            tl.read (dashboard.getTimeLog());
        } catch (IOException ioe) {}
        applyFilter(true);
        cancelPostedChanges ();
    }

    public void show() {
        if (frame.isShowing())
            frame.toFront();
        else {
            reload();
            frame.show();
        }
    }

                                // make sure root is expanded
    public void expandRoot () { tree.expandRow (0); }


    // Returns the TreeNode instance that is selected in the tree.
    // If nothing is selected, null is returned.
    protected DefaultMutableTreeNode getSelectedNode() {
        TreePath   selPath = tree.getSelectionPath();

        if(selPath != null)
            return (DefaultMutableTreeNode)selPath.getLastPathComponent();
        return null;
    }

    /**
     * The next method implement the TreeSelectionListener interface
     * to deal with changes to the tree selection.
     */
    public void valueChanged (TreeSelectionEvent e) {
        TreePath tp = e.getNewLeadSelectionPath();

        if (tp == null) {           // deselection
            tree.clearSelection();
            applyFilter (false);
            return;
        }
        Object [] path = tp.getPath();
        PropertyKey key = treeModel.getPropKey (useProps, path);

        Prop val = useProps.pget (key);
        applyFilter (false);
    }


    // DateChangeAction responds to user input in a date field.
    class DateChangeAction implements ActionListener, FocusListener {
        JTextComponent widget;
        String         text = null;
        Color          background;

        public DateChangeAction (JTextComponent src) {
            widget = src;
            text = widget.getText();
            background = new Color (widget.getBackground().getRGB());
        }

        protected void validate () {
            String newText = widget.getText();

            Date d = DateFormatter.parseDate (newText);
            if (d == null) {
                if ((newText != null) && (newText.length() > 0))
                    widget.setBackground (Color.red);
                else {
                    widget.setBackground (background);
                    text = newText;
                    widget.setText (text);
                }
            } else {
                widget.setBackground (background);
                text = DateFormatter.formatDate (d);
                widget.setText (text);
            }
            widget.repaint(widget.getVisibleRect());
        }

        public void actionPerformed(ActionEvent e) { // hit return
            validate();
        }

        public void focusGained(FocusEvent e) {
            widget.selectAll();
        }

        public void focusLost(FocusEvent e) {
            validate();
        }

    } // End of TimeLogEditor.DateChangeAction


    //
    // ReloadAction responds to user clicking Reload button.
    //
    class ReloadAction extends Object implements ActionListener {
        public void actionPerformed(ActionEvent e) { reload(); }
    } // End of TimeLogEditor.ReloadAction


    //
    // SaveAction responds to user clicking Save button.
    //
    class SaveAction extends Object implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            //if editing, stop edits
            if (table.table.isEditing())
                table.table.editingStopped
                    (new ChangeEvent("Saving Data: Stop edit"));
            try {                     // save the time log
                tl.save (dashboard.getTimeLog());
            } catch (IOException ioe) {}
            applyPostedChanges ();
        }
    } // End of TimeLogEditor.SaveAction


    class UserWarning extends JDialog {
        public UserWarning (Frame f, String s) {
            super (f, s);
            Box b = Box.createVerticalBox();
            b.add (Box.createVerticalStrut(5));
            b.add (new JLabel
                ("Click OK to collapse visible fields with duplicate 'Logged To'   "));
            b.add (new JLabel
                ("fields into a single entry.  Otherwise click CANCEL.   "));
            b.add (Box.createVerticalStrut(5));
            b.add (Box.createVerticalGlue());
            JButton button;
            button = new JButton ("Cancel");
            button.addActionListener (new ActionListener () {
                public void actionPerformed(ActionEvent e) { warnUser.dispose(); }
            });

            Box b2 = Box.createHorizontalBox();
            b2.add (Box.createVerticalStrut(50));
            b2.add (Box.createHorizontalGlue());
            b2.add (button);
            b2.add (Box.createHorizontalGlue());
            button = new JButton ("OK");
            button.addActionListener (new ActionListener () {
                public void actionPerformed(ActionEvent e) {
                    warnUser.dispose();
                    summarize();
                }
            });
            b2.add (button);
            b2.add (Box.createHorizontalGlue());
            b.add (b2);
            getContentPane().add (b);
//      setResizable(false);
            pack();
            this.show();
        }
    }

}
