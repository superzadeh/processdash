// Copyright (C) 2002-2011 Tuma Solutions, LLC
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

package teamdash.templates.setup;
import java.awt.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import net.sourceforge.processdash.BackgroundTaskManager;
import net.sourceforge.processdash.DashController;
import net.sourceforge.processdash.Settings;
import net.sourceforge.processdash.data.ListData;
import net.sourceforge.processdash.data.SimpleData;
import net.sourceforge.processdash.data.StringData;
import net.sourceforge.processdash.data.repository.DataRepository;
import net.sourceforge.processdash.hier.DashHierarchy;
import net.sourceforge.processdash.hier.PropertyKey;
import net.sourceforge.processdash.hier.HierarchyAlterer.HierarchyAlterationException;
import net.sourceforge.processdash.net.http.TinyCGIException;
import net.sourceforge.processdash.process.ui.TriggerURI;
import net.sourceforge.processdash.tool.bridge.client.TeamServerSelector;
import net.sourceforge.processdash.ui.UserNotificationManager;
import net.sourceforge.processdash.ui.web.TinyCGIBase;
import net.sourceforge.processdash.util.FileUtils;
import net.sourceforge.processdash.util.HTMLUtils;
import net.sourceforge.processdash.util.StringUtils;
import net.sourceforge.processdash.util.ThreadThrottler;
import net.sourceforge.processdash.util.XMLUtils;
import teamdash.FilenameMapper;

/** CGI script which synchonizes a dashboard hierarchy with a WBS description.
 * 
 * This script works on both the team and the inidividual sides of a
 * dashboard team project.
 * 
 * When called without parameters, this script:<ul>
 * <li>Checks entry criteria and displays an error page if it discovers a
 * problem.
 * <li>If the entry criteria for the script have been met, this performs a
 * trial (aka "whatIf") synchronization run to see if there is any work to do.
 * If it discovers that the hierarchy is already up to date, it displays a page
 * announcing that fact.
 * <li>If synchonization work is required, this displays a page
 * containing the message "synchronizing...please wait"; this includes an
 * HTTP_REFRESH instruction pointing to "sync.class?run"
 * </ul>
 * 
 * When called with the "run" parameter, this script:<ul>
 * <li>Checks entry criteria and displays an error page if it discovers a
 * problem.
 * <li>Performs the requested synchronization, and displays the results.
 * </ul>
 */
public class sync extends TinyCGIBase {

    /** The hierarchy path to the root of the enclosing team project */
    private String projectRoot;
    /** The unique ID assigned to the enclosing team project */
    private String projectID;
    /** The processID used by the enclosing team project */
    private String processID;
    /** The location of the wbs dump file */
    private URL wbsLocation;
    /** The location of the workflow dump file */
    private URL workflowLocation;
    /** The workflow dump file, written by a WBSDataWriter */
    private File workflowFile;
    /** The templates directory for the project */
    private File templatesDir;
    /** The initials of the current team member, if applicable */
    private String initials;
    /** True if this is the team rollup side of the project */
    private boolean isTeam;
    /** True if this is a master project rollup */
    private boolean isMaster;
    /** true if this is an old-style individual project that needs to be
     * upgraded to the new-style format */
    private boolean migrationNeeded;
    /** true if this is an individual project that needs to be converted
     * to a different metrics collection framework */
    private boolean conversionNeeded;
    /** true if the user wants us to copy all software component and document
     * nodes in the WBS, even if they aren't assigned to them */
    private boolean fullCopyMode;


    private static String ENABLE_SYNC_LOGGING = Settings
            .getVal("syncWBS.enableLogging", "forDelete");


    public sync() {
        charset = "UTF-8";
    }


    protected void doPost() throws IOException {
        parseFormData();
        super.doPost();
    }



    public void writeContents() throws IOException {
        try {
            if (Settings.getBool("READ_ONLY", false))
                signalError("generalError", READ_ONLY_MODE_ERR_MESSAGE);

            // locate the root of the included project.
            findProject();

            // load data values from that project.
            loadValues();

            // possibly redirect to the migration/conversion page.
            if (migrationNeeded || conversionNeeded) {
                printMigrationRedirect();
                return;
            }

            // create a synchronization object.
            HierarchySynchronizer synch = new HierarchySynchronizer
                (projectRoot, processID, wbsLocation, workflowLocation,
                 initials, getOwner(), fullCopyMode, getPSPProperties(),
                 getDataRepository());

            // double-check individual initials
            if (checkForMismatchedIndivInitials(synch))
                return;

            // start the synchronization process.
            if (parameters.containsKey(RUN_PARAM))
                synchronize(synch);
            else if (parameters.containsKey(SAVE_PERMS))
                savePermissionData();
            else
                maybeSynchronize(synch);

        } catch (TinyCGIException e) {
            // the signalError() method uses a TinyCGIException to abort
            // processing;  that exception is caught here and used to draw
            // the error page.
            showErrorPage(e.getTitle(), e.getText());
        } catch (HierarchyAlterationException h) {
            showErrorPage("generalError", h.getMessage());
        } catch (IOException ioe) {
            showErrorPage("generalError", ioe.getMessage());
        }

        // certain operations (such as saving the WBS) trigger a sync to run
        // in the background.  For various reasons, that sync may require user
        // involvement to finish all the work needed.  After the code above
        // has finished as much work as possible, ask the SyncScanner to alert
        // the user if their involvement is needed to finish.
        if (projectRoot != null && parameters.containsKey(RUN_PARAM)
                && parameters.containsKey(BACKGROUND_PARAM))
            SyncScanner.requestScan(projectRoot);
    }



    /** Locates the enclosing team project, and sets the values of the
     * {@link #projectRoot} and {@link #processID} fields accordingly.
     * If there is no enclosing team project, both will be set to null.
     */
    private void findProject() {
        DashHierarchy hierarchy = getPSPProperties();
        PropertyKey key = hierarchy.findExistingKey(getPrefix());
        while (key != null) {
            String templateID = hierarchy.getID(key);

            if (templateID != null && templateID.endsWith(MASTER_ROOT)) {
                projectRoot = key.path();
                processID = templateID.substring
                    (0, templateID.length() - MASTER_ROOT.length());
                isTeam = isMaster = true;
                return;
            }
             if (templateID != null && templateID.endsWith(TEAM_ROOT)) {
                projectRoot = key.path();
                processID = templateID.substring
                    (0, templateID.length() - TEAM_ROOT.length());
                isTeam = true;
                isMaster = false;
                return;
            }
            if (templateID != null && templateID.endsWith(INDIV_ROOT)) {
                projectRoot = key.path();
                processID = templateID.substring
                    (0, templateID.length() - INDIV_ROOT.length());
                isTeam = isMaster = false;
                return;
            }
            if (templateID != null && templateID.endsWith(INDIV2_ROOT)) {
                projectRoot = key.path();
                processID = templateID.substring
                    (0, templateID.length() - INDIV2_ROOT.length());
                isTeam = isMaster = false;
                return;
            }

            key = key.getParent();
        }

        projectRoot = processID = null;
    }



    /** Load values out of the data repository.  Redirect to an error page if
     * any of the required data is missing or invalid.
     */
    private void loadValues() throws IOException {
        // ensure we are within a team project.
        if (projectRoot == null)
            signalError(NOT_TEAM_PROJECT);

        DataRepository data = getDataRepository();
        workflowFile = null;
        templatesDir = null;

        wbsLocation = getWBSLocation(data);
        workflowLocation = getWorkflowLocation(wbsLocation);

        // look up the unique ID for this project.
        SimpleData d = data.getSimpleValue(DataRepository.createDataName
                (projectRoot, TeamDataConstants.PROJECT_ID));
        projectID = (d == null ? "" : d.format());

        if (isTeam) {
            initials = (isMaster ? HierarchySynchronizer.SYNC_MASTER
                    : HierarchySynchronizer.SYNC_TEAM);
            migrationNeeded = conversionNeeded = false;

            if (shouldRepairTeamImport())
                RepairImportInstruction.maybeRepairForTeam(getDataContext());

        } else {
            String initialsDataName = DataRepository.createDataName(
                projectRoot, TeamDataConstants.INDIV_INITIALS);

            // change the initials of the current team member, if requested.
            String newInitials = (String) parameters.get(CHANGE_INITIALS);
            if (StringUtils.hasValue(newInitials)) {
                if (UNLISTED_INITIALS.equals(newInitials)) {
                    signalError(UNLISTED_MEMBER);
                } else if (!newInitials.startsWith("-")) {
                    data.putValue(initialsDataName, StringData
                            .create(newInitials.trim()));
                }
            }

            // get the initials of the current team member.
            d = data.getSimpleValue(initialsDataName);
            if (d == null || !d.test() ||
                "tttt".equals(initials = d.format().trim()))
                signalError(INITIALS_MISSING);

            d = data.getSimpleValue(DataRepository.createDataName
                                    (projectRoot, MIGRATE_DATA_NAME));
            migrationNeeded = (d != null && d.test());

            d = data.getSimpleValue(DataRepository.createDataName
                                    (projectRoot, CONVERT_DATA_NAME));
            conversionNeeded = (d != null && d.test());

            RepairImportInstruction.maybeRepairForIndividual(getDataContext());
        }

        // we no longer check to see if the user wants us to perform a full wbs
        // sync;  this functionality is obsolete and only causes problems now.
        fullCopyMode = false;
    }



    private URL getWBSLocation(DataRepository data)
            throws MalformedURLException, TinyCGIException {

        String urlDataName = DataRepository.createDataName(projectRoot,
            TeamDataConstants.TEAM_DATA_DIRECTORY_URL);

        URL wbsLocation = getWBSLocationFromUrlDataElement(data, urlDataName);

        if (wbsLocation == null) {
            // find the data directory for this team project.
            String teamDirectoryLocation = null;
            File teamDirectory = null;

            SimpleData d = data.getSimpleValue(DataRepository.createDataName(
                projectRoot, TeamDataConstants.TEAM_DATA_DIRECTORY));

            if (d == null || !d.test() ||
                 "Enter network directory path".equals(teamDirectoryLocation = d.format()))
                signalError(TEAM_DIR_MISSING);

            teamDirectoryLocation = FilenameMapper.remap(teamDirectoryLocation);
            teamDirectory = new File(teamDirectoryLocation);

            if (!teamDirectory.isDirectory())
                signalError(TEAM_DIR_MISSING);

            URL serverURL = TeamServerSelector.getServerURL(teamDirectory);

            if (serverURL != null) {
                data.putValue(urlDataName, StringData.create(serverURL.toString()));
                wbsLocation = new URL(serverURL.toString() + "/" + HIER_FILENAME);

                // if the physical directory is now obsolete, remove the pointers
                // to that directory so we never attempt to look there again.
                File obsoleteDirMarkerFile = new File(teamDirectory,
                        TeamDataConstants.OBSOLETE_DIR_MARKER_FILENAME);
                if (obsoleteDirMarkerFile.isFile()) {
                    data.putValue(DataRepository.createDataName(projectRoot,
                        TeamDataConstants.TEAM_DIRECTORY), null);
                    data.putValue(DataRepository.createDataName(projectRoot,
                        TeamDataConstants.TEAM_DIRECTORY_UNC), null);
                }
            }
            else {
                // locate the wbs file in the team data directory.
                File wbsFile = new File(teamDirectoryLocation, HIER_FILENAME);

                if (!wbsFile.exists())
                    signalError(WBS_FILE_MISSING + "&wbsFile", wbsFile.toString());
                if (!wbsFile.canRead())
                    signalError(WBS_FILE_INACCESSIBLE + "&wbsFile", wbsFile.toString());

                // locate the workflow file and the templates directory
                workflowFile = new File(teamDirectoryLocation, WORKFLOW_FILENAME);
                templatesDir = new File(teamDirectory.getParentFile().getParentFile(),
                                        "Templates");

                wbsLocation = wbsFile.toURI().toURL();
            }
        }

        return wbsLocation;
    }

    private URL getWBSLocationFromUrlDataElement(DataRepository data,
            String urlDataName) throws MalformedURLException {
        // Check to see if we have a URL stored in the data repository.
        // If not, we can't proceed.
        SimpleData d = data.getSimpleValue(urlDataName);
        if (d == null)
            return null;

        // Test the URL we found, to see if we can find a valid server.
        String lastServerUrlStr = d.format();
        URL serverUrl;
        try {
            serverUrl = TeamServerSelector.resolveServerURL(lastServerUrlStr);
        } catch (Throwable t) {
            // if the user is running an older version of the dashboard, the
            // resolveServerURL() method may not exist. In that case, fall
            // back to the older testServerURL() method.
            serverUrl = TeamServerSelector.testServerURL(lastServerUrlStr);
        }
        if (serverUrl == null)
            return null;

        // Has the server URL changed since our last sync?  If so, write the
        // new URL into the data repository.
        String serverUrlStr = serverUrl.toString();
        if (!serverUrlStr.equals(lastServerUrlStr)) {
            data.putValue(urlDataName, StringData.create(serverUrlStr));
        }

        // Construct the WBS URL from the server URL, and return it.
        URL wbsLocation = new URL(serverUrlStr + "/" + HIER_FILENAME);
        return wbsLocation;
    }

    private URL getWorkflowLocation(URL wbsUrl) {
        if (wbsUrl != null) {
            try {
                String wbsUrlStr = wbsUrl.toString();
                String workflowUrlStr = StringUtils.findAndReplace(wbsUrlStr,
                    HIER_FILENAME, WORKFLOW_FILENAME);
                if (!workflowUrlStr.equals(wbsUrlStr))
                    return new URL(workflowUrlStr);
            } catch (MalformedURLException e) {}
        }
        return null;
    }

    private boolean shouldRepairTeamImport() {
        SimpleData d = getDataContext().getSimpleValue(
            DISABLE_TEAM_IMPORT_REPAIR_DATA_NAME);
        if (d != null && d.test())
            return false;

        return !Settings.getBool(DISABLE_TEAM_IMPORT_REPAIR_SETTING, false);
    }

    private boolean checkForMismatchedIndivInitials(HierarchySynchronizer synch)
            throws TinyCGIException {
        Map<String, String> mismatch = synch.checkIndivInitials();
        if (mismatch == null)
            return false;
        else if (mismatch.isEmpty())
            signalError(EMPTY_TEAM);

        StringBuilder select = new StringBuilder();
        select.append("<select name='" + CHANGE_INITIALS + "'>");
        select.append("<option value='-'>Select your name...</option>");
        for (Map.Entry<String, String> indiv : mismatch.entrySet()) {
            String initialsHtml = HTMLUtils.escapeEntities(indiv.getKey());
            String nameHtml = HTMLUtils.escapeEntities(indiv.getValue());
            select.append("<option value='").append(initialsHtml).append("'>")
                    .append(nameHtml).append("</option>");
        }
        select.append("<option value='" + UNLISTED_INITIALS
                + "'>My name is not listed...</option>");
        select.append("</select>");

        String dataName = DataRepository.createDataName(projectRoot,
            "setup//Member_List");
        getDataRepository().putValue(dataName,
            StringData.create(select.toString()));

        out.write("<!-- SYNC-IS-NEEDED -->");
        out.write("<html><head>");
        out.write("<meta http-equiv='Refresh' CONTENT='0;URL="
                + "syncFixInitials.shtm'>");
        out.write("</head><body></body></html>");
        return true;
    }



    /** Redirect to the team project migration page */
    private void printMigrationRedirect() {
        out.write("<!-- SYNC-IS-NEEDED -->");
        out.write("<html><head>");
        out.write("<meta http-equiv='Refresh' CONTENT='0;URL="
                + "migrateIndivConfirm.shtm'>");
        out.write("</head><body></body></html>");
    }



    /** Check to see if a synchronization operation is needed. If so,
     * display a "please wait" page, then initiate the operation.
     */
    private void maybeSynchronize(HierarchySynchronizer synch)
        throws HierarchyAlterationException
    {
        synch.setWhatIfMode(true);
        if (parameters.containsKey(TriggerURI.IS_TRIGGERING)
                || parameters.containsKey(BRIEF_PARAM))
            synch.setWhatIfBrief(true);
        else if (ENABLE_SYNC_LOGGING != null)
            synch.enableDebugLogging();

        synch.sync();
        syncTemplates(synch);

        if (synch.getChanges().isEmpty()) {
            printChanges(synch.getChanges());

        } else if (isTeam == false
                && (synch.getTaskDeletions().isEmpty() == false ||
                    synch.getTaskCompletions().isEmpty() == false)) {

            printPermissionsPage(synch.getTaskDeletions(),
                    synch.getTaskCompletions());

        } else {
            printWaitPage();
        }

        if (synch.getDebugLogInfo() != null)
            maybeDumpDebugLog(synch);
    }



    /** Synchronize the hierarchy and display the results.
     */
    private void synchronize(HierarchySynchronizer synch)
        throws HierarchyAlterationException, IOException
    {
        // ensure that the hierarchy editor is not open.
        if (DashController.isHierarchyEditorOpen())
            signalError(HIER_EDITOR_OPEN);

        if (!isTeam)
            loadPermissionData(synch);

        boolean bg = parameters.containsKey(BACKGROUND_PARAM);
        try {
            if (bg) ThreadThrottler.beginThrottling(0.2);
            synch.setWhatIfMode(false);
            synch.sync();
            syncTemplates(synch);
        } finally {
            if (bg) ThreadThrottler.endThrottling();
        }

        if (synch.isFollowOnWorkNeeded()) {
            saveChangeList(synch);
            parameters.remove(RUN_PARAM);
            parameters.remove(SAVE_PERMS);
            writeContents();

        } else {
            if (printChanges(synch.getChanges())
                    && !parameters.containsKey("noExport")
                    && !Settings.getBool("export.disableAutoExport", false))
                startBackgroundExport(projectRoot);

            UserNotificationManager.getInstance().removeNotification(
                    SyncScanner.getScanTaskID(projectRoot));
        }
    }


    private void syncTemplates(HierarchySynchronizer sync) {
        if (isTeam && workflowFile != null && templatesDir != null) {
            String templateURI = "/" + processID + "-template.xml";
            TemplateSynchronizer tSync = new TemplateSynchronizer(projectRoot,
                    processID, projectID, templateURI, workflowFile,
                    templatesDir);
            tSync.setWhatIfMode(sync.isWhatIfMode());
            tSync.sync();
            sync.getChanges().addAll(tSync.getChanges());
        }
    }



    private void saveChangeList(HierarchySynchronizer synch) {
        ListData changes = (ListData) getDataContext().getSimpleValue(
                CHANGES_DATANAME);
        if (changes == null)
            changes = new ListData();

        for (Iterator i = synch.getChanges().iterator(); i.hasNext();)
            changes.add(String.valueOf(i.next()));

        getDataContext().putValue(CHANGES_DATANAME, changes);
    }



    /** Print a page that will ask the user for permission to make
     * destructive changes to their hierarchy.
     * @param taskDeletions a list of tasks we would like to delete
     * @param taskCompletions a list of tasks we would like to mark complete
     */
    private void printPermissionsPage(List taskDeletions, List taskCompletions) {
        out.print("<!-- SYNC-IS-NEEDED -->\n");
        out.print("<html><head>\n");
        out.print("<title>Synchronizing Work Breakdown Structure</title>\n");
        out.print("<style> .important { color: #800; font-weight: bold; }</style>\n");
        out.print("</head><body>\n");
        out.print("<h1>Synchronizing Work Breakdown Structure</h1>\n");
        out.print("<form action='sync.class' method='post'>\n");
        out.print("<input type='hidden' name='"+SAVE_PERMS+"' value='1'/>\n");
        out.print("<p>Several of the tasks in your hierarchy have been "
                + "deleted from the project's work breakdown structure, "
                + "have been reassigned to other individuals, or have "
                + "been deferred until future iterations.  These tasks "
                + "can be removed from your project automatically.</p>\n");

        if (!taskDeletions.isEmpty()) {
            out.print("<h2>Tasks to Delete</h2>\n");
            out.print("<p>You have not collected any actual metrics against "
                + "the following tasks, so the synchronization operation can "
                + "delete them from your hierarchy entirely.  If you wish to "
                + "keep any of these tasks, <b>uncheck the boxes</b> next to "
                + "them.  <span class='important'>Any tasks with checkmarks "
                + "next to them will be deleted when you press the OK "
                + "button.</span></p>");
            for (Iterator i = taskDeletions.iterator(); i.hasNext();) {
                String path = (String) i.next();
                printPermissionItem(DELETE_PREFIX, path);
            }
        }

        if (!taskCompletions.isEmpty()) {
            out.print("<h2>Tasks to Mark Complete</h2>");
            out.print("<p>Actual time and/or defects have been collected "
                + "against the following tasks, so they cannot be deleted "
                + "outright.  However, the synchronization operation can "
                + "mark the tasks complete so they no longer affect your "
                + "earned value.  If you wish to keep any of these tasks "
                + "open, <b>uncheck the boxes</b> next to them.  <span "
                + "class='important'>Any tasks with checkmarks next to them "
                + "will be marked complete when you press the OK "
                + "button.</span></p>");
            for (Iterator i = taskCompletions.iterator(); i.hasNext();) {
                String path = (String) i.next();
                printPermissionItem(COMPLETE_PREFIX, path);
            }
        }

        out.print("<p><input type='submit' name='OK' value='OK'/></p>");
        out.print("</form></body></html>\n");
    }

    private void printPermissionItem(String attr, String path) {
        path = HTMLUtils.escapeEntities(path);
        out.print("<input type=\"checkbox\" checked=\"true\" name=\"");
        out.print(attr);
        out.print(path);
        out.print("\"/>&nbsp;");
        out.print(path);
        out.print("<br/>\n");
    }



    /** Parse data from the permissions form, and save it to the repository.
     */
    private void savePermissionData() {
        ListData delete = new ListData();
        ListData complete = new ListData();
        for (Iterator i = parameters.entrySet().iterator(); i.hasNext();) {
            Map.Entry e = (Map.Entry) i.next();
            if (e.getValue() == null  || "".equals(e.getValue()))
                continue;
            String name = (String) e.getKey();
            if (name.startsWith(DELETE_PREFIX))
                delete.add(name.substring(DELETE_PREFIX.length()));
            else if (name.startsWith(COMPLETE_PREFIX))
                complete.add(name.substring(COMPLETE_PREFIX.length()));
        }
        getDataRepository().putValue(getDataName(DELETE_DATANAME), delete);
        getDataRepository().putValue(getDataName(COMPLETE_DATANAME), complete);
        printWaitPage();
    }


    /** Load permission data from the repository, and configure the given
     * HierarchySynchronizer.
     */
    private void loadPermissionData(HierarchySynchronizer synch) {
        Object list = getDataRepository().getSimpleValue(
                getDataName(DELETE_DATANAME));
        if (list instanceof ListData)
            synch.setDeletionPermissions(((ListData) list).asList());
        list = getDataRepository().getSimpleValue(
                getDataName(COMPLETE_DATANAME));
        if (list instanceof ListData)
            synch.setCompletionPermissions(((ListData) list).asList());
    }

    private String getDataName(String name) {
        return DataRepository.createDataName(getPrefix(), name);
    }



    /** Print a page asking the user to wait. This page includes an
     * HTTP "refresh" instruction that will initiate the synchronization
     * operation.
     */
    private void printWaitPage() {
        out.print("<!-- SYNC-IS-NEEDED -->\n");
        out.print("<html><head>");
        out.print("<title>Synchronizing Work Breakdown Structure</title>");
        out.print("<meta http-equiv='Refresh' content='1;URL=sync.class?run'>");
        out.print("</head>");
        out.print("<body><h1>Synchronizing...</h1>");
        out.print("Please wait.");
        out.print("</body></html>");
    }



    /** Print a list of changes made by a synchronization operation.
     */
    private boolean printChanges(List changeList) {
        ListData oldChanges = (ListData) getDataContext().getSimpleValue(
                CHANGES_DATANAME);
        if (oldChanges != null) {
            changeList.addAll(0, oldChanges.asList());
            getDataContext().putValue(CHANGES_DATANAME, null);
        }

        out.print("<html><head><title>Synchronization Complete</title></head>");
        out.print("<body><h1>Synchronization Complete</h1>");
        if (changeList.isEmpty()) {
            out.print("<p>Your hierarchy is up to date - no changes "+
                      "were necessary.");

            if (parameters.containsKey(TriggerURI.IS_TRIGGERING)) {
                out.print(TriggerURI.NULL_DOCUMENT_MARKER);
                JOptionPane.showMessageDialog(getParentComponent(),
                        "Your hierarchy is up to date - no changes were necessary.",
                        "Synchronization Complete",
                        JOptionPane.PLAIN_MESSAGE);
            }

        } else {
            out.print("<p>The following changes were made to your hierarchy:");
            out.print("<ul>");
            Iterator i = changeList.iterator();
            while (i.hasNext()) {
                out.print("<li>");
                out.print(HTMLUtils.escapeEntities(String.valueOf(i.next())));
            }
            out.print("</ul>");
        }
        out.print("</body></html>");

        return !changeList.isEmpty();
    }

    private Component getParentComponent() {
        Object result = getDashboardContext();
        if (result instanceof Component)
            return (Component) result;
        else
            return null;
    }



    private void maybeDumpDebugLog(HierarchySynchronizer synch) {
        String prefix = null;

        if ("always".equalsIgnoreCase(ENABLE_SYNC_LOGGING)) {
            prefix = "sync-debug-";

        } else if ("forDelete".equalsIgnoreCase(ENABLE_SYNC_LOGGING)) {
            int delTaskCount = synch.getTaskDeletions().size()
                    + synch.getTaskCompletions().size();
            if (delTaskCount > 5)
                prefix = "sync-delete-";
        }

        if (prefix != null) {
            try {
                dumpDebugLog(synch, prefix);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    private void dumpDebugLog(HierarchySynchronizer synch, String dumpPrefix)
            throws Exception {
        // determine the location of the data directory
        File dataDir = null;
        String settingsFile = DashController.getSettingsFileName();
        if (StringUtils.hasValue(settingsFile))
            dataDir = new File(settingsFile).getParentFile();
        if (dataDir == null || !dataDir.isDirectory())
            dataDir = new File(".");

        // save debug info to the data directory
        List<String> debugInfo = synch.getDebugLogInfo();
        File debugInfoFile = new File(dataDir, "syncDebugInfo.dat");
        if (debugInfo != null) {
            PrintWriter out = new PrintWriter(debugInfoFile);
            for (String line : debugInfo)
                out.println(line);
            out.close();
        }

        // copy the wbs dump file into the data directory
        InputStream wbsIn = wbsLocation.openStream();
        File wbsDumpFile = new File(dataDir, "wbsDump.dat");
        FileUtils.copyFile(wbsIn, wbsDumpFile);
        wbsIn.close();

        // copy the wbs xml file into the data directory
        wbsIn = new URL(wbsLocation, "wbs.xml").openStream();
        File wbsXmlFile = new File(dataDir, "wbsXml.dat");
        FileUtils.copyFile(wbsIn, wbsXmlFile);
        wbsIn.close();

        // copy the modified wbs xml data into the data directory
        File wbsModFile = new File(dataDir, "wbsDumpPruned.dat");
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(
                wbsModFile), "UTF-8");
        w.write(XMLUtils.getAsText(synch.getProjectXML()));
        w.close();

        // perform a backup of the data directory (which will include the
        // two files created above)
        File backup = DashController.backupData();
        File backupDir = new File(dataDir, "backup");
        if (!backupDir.isDirectory())
            backupDir = dataDir;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd-HHmmss'.zip'");
        String newFilename = dumpPrefix + fmt.format(new Date());
        File saved = new File(backupDir, newFilename);
        FileUtils.copyFile(backup, saved);

        // cleanup the temporary dump files we created
        debugInfoFile.delete();
        wbsDumpFile.delete();
        wbsXmlFile.delete();
        wbsModFile.delete();

        // cleanup older dump files
        Date cutoffDate = new Date(System.currentTimeMillis() - DUMP_CLEANUP_AGE);
        String oldName = dumpPrefix + fmt.format(cutoffDate);
        File[] files = backupDir.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith(dumpPrefix) && name.compareTo(oldName) < 0)
                    f.delete();
            }
        }
    }
    private static final long DUMP_CLEANUP_AGE = 14L /*days*/ * 24 /*hours*/
            * 60 /*minutes*/ * 60 /*seconds*/ * 1000 /*milliseconds*/;



    /** Asynchronously export the user's data.
     */
    private static class AsyncExporter implements Runnable {

        private String projectRoot;

        public AsyncExporter(String projectRoot) {
            this.projectRoot = projectRoot;
        }

        public void run() {
            DashController.exportData(projectRoot);
        }

        public String toString() {
            return "AsyncExporter:" + projectRoot;
        }

    }

    static void startBackgroundExport(String projectRoot) {
        BackgroundTaskManager.getInstance().addTask(
                new AsyncExporter(projectRoot));
    }



    /** Throw an exception that will stop processing and redirect to an
     * error page.
     */
    private void signalError(String reason) throws TinyCGIException {
        signalError(reason, null);
    }
    /** Throw an exception that will stop processing and redirect to an
     * error page.
     */
    private void signalError(String reason, String value) throws TinyCGIException {
        throw new TinyCGIException(500, reason, value);
    }

    /** Redirect to an error page.
     */
    private void showErrorPage(String reason, String value) throws IOException {
        out.write("<html><head>");
        out.write("<meta http-equiv='Refresh' CONTENT='0;URL=syncError.shtm?");
        out.write(reason);
        if (value != null)
            out.write("=" + HTMLUtils.urlEncode(value));
        if (isTeam)
            out.write("&isTeam");
        if (isMaster)
            out.write("&isMaster");
        out.write("'></head><body></body></html>");
    }

    static final String MASTER_ROOT = "/MasterRoot";
    static final String TEAM_ROOT = "/TeamRoot";
    private static final String INDIV_ROOT = "/IndivRoot";
    private static final String INDIV2_ROOT = "/Indiv2Root";
    private static final String DISABLE_TEAM_IMPORT_REPAIR_DATA_NAME = "Disable_Team_Import_Repairs";
    private static final String DISABLE_TEAM_IMPORT_REPAIR_SETTING = "disableTeamImportRepairs";
    private static final String MIGRATE_DATA_NAME = "Team_Project_Migration_Needed";
    private static final String CONVERT_DATA_NAME = "Team_Project_Conversion_Needed";
    static final String HIER_FILENAME = "projDump.xml";
    private static final String WORKFLOW_FILENAME = "workflowDump.xml";

    private static final String NOT_TEAM_PROJECT = "notTeamProject";
    private static final String TEAM_DIR_MISSING = "teamDirMissing";
    private static final String WBS_FILE_MISSING = "wbsFileMissing";
    private static final String WBS_FILE_INACCESSIBLE = "wbsFileInaccessible";
    private static final String INITIALS_MISSING = "initialsMissing";
    private static final String EMPTY_TEAM = "emptyTeam";
    private static final String UNLISTED_MEMBER = "unlistedMember";
    private static final String HIER_EDITOR_OPEN = "hierEditorOpen";

    private static final String BACKGROUND_PARAM = "bg";
    private static final String BRIEF_PARAM = "brief";
    private static final String RUN_PARAM = "run";
    private static final String SAVE_PERMS = "savePerms";
    private static final String CHANGE_INITIALS = "changeInitials";
    private static final String UNLISTED_INITIALS = "-NL";
    private static final String COMPLETE_PREFIX = "COMPLETE:";
    private static final String DELETE_PREFIX = "DELETE:";
    private static final String COMPLETE_DATANAME = "complete_ //list";
    private static final String DELETE_DATANAME = "delete_ //list";
    private static final String CHANGES_DATANAME = "changes_ //list";

    private static final String READ_ONLY_MODE_ERR_MESSAGE =
        "You are currently running the dashboard in read-only mode, so " +
        "no changes can be made.";
}
