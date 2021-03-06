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

package teamdash.wbs.excel;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumnModel;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import teamdash.wbs.DataJTable;
import teamdash.wbs.WBSTabPanel;

public class SaveAsExcelAction extends AbstractAction {

    private JFileChooser fileChooser;
    private File lastFileSelected;

    public SaveAsExcelAction() throws Throwable {
        super("Export Snapshot to Excel...");

        // throw an exception if the POI classes aren't available.  Our caller
        // will catch the exception and skip this menu item.
        Class.forName(HSSFWorkbook.class.getName());
    }

    public void actionPerformed(ActionEvent e) {
        File f = getOutputFile();
        if (f != null) {
            writeData(f);
        }
    }

    private File getOutputFile() {
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Snapshot");
            fileChooser.setFileFilter(new ExcelFileFilter());
        }

        if (lastFileSelected != null)
            fileChooser.setSelectedFile(lastFileSelected);

        Component parent = SwingUtilities.getWindowAncestor(getWBSTabPanel());
        int userOption = fileChooser.showSaveDialog(parent);
        if (userOption != JFileChooser.APPROVE_OPTION)
            return null;

        File result = fileChooser.getSelectedFile();
        if (result == null)
            return null;

        String filename = result.getName();
        if (filename.indexOf('.') == -1) {
            filename = filename + EXCEL_SUFFIX;
            result = new File(result.getParentFile(), filename);
        }

        lastFileSelected = result;
        return result;
    }

    private static final String EXCEL_SUFFIX = ".xls";

    private static class ExcelFileFilter extends FileFilter {
        @Override
        public boolean accept(File f) {
            return (f.isDirectory()
                    || f.getName().toLowerCase().endsWith(EXCEL_SUFFIX));
        }

        @Override
        public String getDescription() {
            return "Excel Spreadsheets (.xls)";
        }
    }



    private void writeData(File f) {
        DataJTable dataTable = getDataJTable();
        WBSTabPanel tabPanel = getWBSTabPanel();

        WBSExcelWriter writer = new WBSExcelWriter(dataTable);
        LinkedHashMap<String, TableColumnModel> tabs = tabPanel.getTabData();
        for (Map.Entry<String, TableColumnModel> e : tabs.entrySet()) {
            writer.addData(e.getKey(), e.getValue());
        }

        try {
            writer.save(f);
        } catch (IOException ioe) {
            Object message = new Object[] {
                "The Work Breakdown Structure Editor was unable to write to the file:",
                "      " + f.getAbsolutePath(),
                "Make certain you have permission to write to that location, then",
                "try again."
            };
            JOptionPane.showMessageDialog(tabPanel, message, "Export Failed",
                JOptionPane.ERROR_MESSAGE);
        }

    }

    private WBSTabPanel getWBSTabPanel() {
        return (WBSTabPanel) getValue(WBSTabPanel.class.getName());
    }

    private DataJTable getDataJTable() {
        return (DataJTable) getValue(DataJTable.class.getName());
    }

}
