/*
 *  $Id$
 *  IzPack
 *  Copyright (C) 2001-2003 Julien Ponge
 *  Changes Copyright (C) 2003-2011 David Tuma
 *
 *  File :               DataDirPanel.java
 *  Description :        A panel to select the personal data directory.
 *  Author's email :     julien@izforge.com
 *  Author's Website :   http://www.izforge.com
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.izforge.izpack.panels;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import com.izforge.izpack.adaptator.IXMLElement;
import com.izforge.izpack.adaptator.impl.XMLElementImpl;
import com.izforge.izpack.gui.ButtonFactory;
import com.izforge.izpack.installer.InstallData;
import com.izforge.izpack.installer.InstallerFrame;
import com.izforge.izpack.installer.IzPanel;
import com.izforge.izpack.installer.ScriptParser;
import com.izforge.izpack.pdash.DashboardInstallConstants;
import com.izforge.izpack.pdash.ExternalConfiguration;
import com.izforge.izpack.util.VariableSubstitutor;

/**
 *  The taget directory selection panel.
 *
 * @author     Julien Ponge
 * @created    November 1, 2002
 */
public class DataDirPanel extends IzPanel implements ActionListener,
        DashboardInstallConstants
{
    /**  The default directory. */
    private String defaultDir;

    /**  The info label. */
    private JLabel infoLabel;

    /**  The text field. */
    private JTextField textField;

    /**  The 'browse' button. */
    private JButton browseButton;

    /**  The layout . */
    private GridBagLayout layout;

    /**  The layout constraints. */
    private GridBagConstraints gbConstraints;


    /**
     *  The constructor.
     *
     * @param  parent  The parent window.
     * @param  idata   The installation data.
     */
    public DataDirPanel(InstallerFrame parent, InstallData idata)
    {
        super(parent, idata);

        // We initialize our layout
        layout = new GridBagLayout();
        gbConstraints = new GridBagConstraints();
        setLayout(layout);

        // load the default directory info (if present)
        loadDefaultDir();
        if (defaultDir == null)
            createDefaultDirValue();
        if (defaultDir != null)
            // override the system default
            idata.setVariable(DATA_PATH, defaultDir);

        // We create and put the components

        infoLabel = new JLabel
            (parent.langpack.getString("DataDirPanel.info"),
             parent.icons.getImageIcon("home"), JLabel.TRAILING);
        parent.buildConstraints(gbConstraints, 0, 0, 2, 1, 3.0, 0.0);
        gbConstraints.insets = new Insets(5, 5, 5, 5);
        gbConstraints.fill = GridBagConstraints.NONE;
        gbConstraints.anchor = GridBagConstraints.SOUTHWEST;
        layout.addLayoutComponent(infoLabel, gbConstraints);
        add(infoLabel);

        textField = new JTextField(idata.getVariable(DATA_PATH), 40);
        textField.addActionListener(this);
        parent.buildConstraints(gbConstraints, 0, 1, 1, 1, 3.0, 0.0);
        gbConstraints.fill = GridBagConstraints.HORIZONTAL;
        gbConstraints.anchor = GridBagConstraints.WEST;
        layout.addLayoutComponent(textField, gbConstraints);
        add(textField);

        browseButton = ButtonFactory.createButton(parent.langpack.getString("TargetPanel.browse"),
            parent.icons.getImageIcon("open"),
            idata.buttonsHColor);
        browseButton.addActionListener(this);
        parent.buildConstraints(gbConstraints, 1, 1, 1, 1, 1.0, 0.0);
        gbConstraints.fill = GridBagConstraints.HORIZONTAL;
        gbConstraints.anchor = GridBagConstraints.EAST;
        layout.addLayoutComponent(browseButton, gbConstraints);
        add(browseButton);
    }


    /**
     *  Loads up the "dir" resource associated with DataDirPanel. Acceptable dir
     *  resource names: <code>
     *   DataDirPanel.dir.macosx
     *   DataDirPanel.dir.mac
     *   DataDirPanel.dir.windows
     *   DataDirPanel.dir.unix
     *   DataDirPanel.dir.xxx,
     *     where xxx is the lower case version of System.getProperty("os.name"),
     *     with any spaces replace with underscores
     *   DataDirPanel.dir (generic that will be applied if none of above is found)
     *   </code> As with all IzPack resources, each the above ids should be
     *  associated with a separate filename, which is set in the install.xml file
     *  at compile time.
     */
    public void loadDefaultDir()
    {
        // We check to see if user settings exists for the default dir.
        Preferences prefs = Preferences.userRoot().node(USER_VALUES_PREFS_NODE);
        String userDataDir = prefs.get(DATA_PATH, null);
        if (userDataDir != null) {
            defaultDir = userDataDir;
            return;
        }

        Properties p = ExternalConfiguration.getConfig();
        String extDefault = p.getProperty("dir.pspdata.default");
        if (extDefault != null) {
            defaultDir = extDefault;
            return;
        }

        BufferedReader br = null;
        try
        {
            String os = System.getProperty("os.name");
            InputStream in = null;

            if (os.regionMatches(true, 0, "windows", 0, 7))
                in = parent.getResource("DataDirPanel.dir.windows");

            else if (os.regionMatches(true, 0, "mac os x", 0, 8))
                in = parent.getResource("DataDirPanel.dir.macosx");

            else if (os.regionMatches(true, 0, "mac", 0, 3))
                in = parent.getResource("DataDirPanel.dir.mac");

            else
            {
                // first try to look up by specific os name
                os.replace(' ', '_');// avoid spaces in file names
                os = os.toLowerCase();// for consistency among DataDirPanel res files
                in = parent.getResource("DataDirPanel.dir.".concat(os));
                // if not specific os, try getting generic 'unix' resource file
                if (in == null)
                    in = parent.getResource("DataDirPanel.dir.unix");

                // if all those failed, try to look up a generic dir file
                if (in == null)
                    in = parent.getResource("DataDirPanel.dir");

            }

            // if all above tests failed, there is no resource file,
            // so use system default
            if (in == null)
                return;

            // now read the file, once we've identified which one to read
            InputStreamReader isr = new InputStreamReader(in);
            br = new BufferedReader(isr);
            String line = null;
            while ((line = br.readLine()) != null)
            {
                line = line.trim();
                // use the first non-blank line
                if (!line.equals(""))
                    break;
            }
            VariableSubstitutor vs = new VariableSubstitutor(idata.getVariables());
            defaultDir = vs.substitute(line, "plain");
        }
        catch (Exception e)
        {
            defaultDir = null;// leave unset to take the system default set by Installer class
        }
        finally
        {
            try
            {
                if (br != null)
                    br.close();
            }
            catch (IOException ignored)
            {}
        }
    }

    private void createDefaultDirValue() {
        String userdir = System.getProperty("user.home");
        File userDirFile = new File(userdir);
        File defaultFile = new File(userDirFile, ".pspdata");
        defaultDir = defaultFile.getAbsolutePath();
    }


    /**
     *  Indicates wether the panel has been validated or not.
     *
     * @return    Wether the panel has been validated or not.
     */
    public boolean isValidated()
    {
        String dataPath = textField.getText();
        boolean ok = true;

        // We put a warning if the specified target is nameless
        if (dataPath.length() == 0)
        {
            JOptionPane.showMessageDialog(this,
                parent.langpack.getString("DataDirPanel.empty_datadir"),
                parent.langpack.getString("installer.error"),
                JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // Normalize the path, only if it's local
        if (!dataPath.startsWith("http")) {
            File path = new File(dataPath);
            dataPath = path.toString();
        }


        /* We put a warning if the direZctory exists else we warn that it will be created
        if (path.exists())
        {
            int res = JOptionPane.showConfirmDialog(this,
                parent.langpack.getString("DataDirPanel.warn"),
                parent.langpack.getString("installer.warning"),
                JOptionPane.YES_NO_OPTION);
            ok = (res == JOptionPane.YES_OPTION);
        }
        else
            JOptionPane.showMessageDialog(this, parent.langpack.getString("DataDirPanel.createdir") +
                "\n" + dataPath);
        */

        String normalizedPath = dataPath;
        String userHome = idata.getVariable(ScriptParser.USER_HOME);
        if (normalizedPath.startsWith(userHome))
            normalizedPath = "~" + normalizedPath.substring(userHome.length());

        idata.setVariable(DATA_PATH, dataPath);
        idata.setVariable(DATA_PATH_NORMALIZED, normalizedPath);
        return ok;
    }


    /**
     *  Actions-handling method.
     *
     * @param  e  The event.
     */
    public void actionPerformed(ActionEvent e)
    {
        Object source = e.getSource();

        if (source == textField)
        {
            parent.navigateNext();
        }
        else
        {
            // The user wants to browse its filesystem

            // Prepares the file chooser
            JFileChooser fc = new JFileChooser();
            fc.setCurrentDirectory(new File(textField.getText()));
            fc.setMultiSelectionEnabled(false);
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.addChoosableFileFilter(fc.getAcceptAllFileFilter());

            // Shows it
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                textField.setText(fc.getSelectedFile().getAbsolutePath());

        }
    }


    /**
     *  Asks to make the XML panel data.
     *
     * @param  panelRoot  The tree to put the data in.
     */
    public void makeXMLData(IXMLElement panelRoot)
    {
        // Data path markup
        IXMLElement ipath = new XMLElementImpl("datapath", panelRoot);
        ipath.setContent(idata.getVariable(DATA_PATH));
        panelRoot.addChild(ipath);
    }


    /**
     *  Asks to run in the automated mode.
     *
     * @param  panelRoot  The XML tree to read the data from.
     */
    public void runAutomated(IXMLElement panelRoot)
    {
        // We set the data path
        IXMLElement ipath = panelRoot.getFirstChildNamed("datapath");
        idata.setVariable(DATA_PATH, ipath.getContent());
    }
}
