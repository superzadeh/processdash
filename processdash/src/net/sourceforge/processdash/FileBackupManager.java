// Copyright (C) 2003-2008 Tuma Solutions, LLC
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


package net.sourceforge.processdash;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.sourceforge.processdash.tool.bridge.client.WorkingDirectory;
import net.sourceforge.processdash.tool.export.mgr.ExternalResourceManager;
import net.sourceforge.processdash.ui.ConsoleWindow;
import net.sourceforge.processdash.util.DashboardBackupFactory;
import net.sourceforge.processdash.util.FileUtils;
import net.sourceforge.processdash.util.ProfTimer;
import net.sourceforge.processdash.util.StringUtils;
import net.sourceforge.processdash.util.ThreadThrottler;


/** Backup data and other files automatically.
 *
 * We want to back up data files (*.dat), defect logs (*.def), the time log
 * (time.log), the state file (state), user settings (pspdash.ini), and
 * the error log (log.txt).
 *
 * Do this each time the dashboard starts or shuts down.
 * Also do it at periodically, as configured by the user.
 */
public class FileBackupManager {

    public static final int STARTUP = 0;
    public static final int RUNNING = 1;
    public static final int SHUTDOWN = 2;

    public static final String BACKUP_TIMES_SETTING = "backup.timesOfDay";

    private WorkingDirectory workingDirectory;
    private OutputStream logFile = null;

    private static final String LOG_FILE_NAME = "log.txt";
    private static final Logger logger = Logger
            .getLogger(FileBackupManager.class.getName());

    public FileBackupManager(WorkingDirectory workingDirectory) {
        this.workingDirectory = workingDirectory;

        DashboardBackupFactory.setMaxHistLogSize(Settings.getInt(
            "logging.maxHistLogSize", 500000));
        DashboardBackupFactory.setKeepBackupsNumDays(Settings.getInt(
            "backup.keepBackupsNumDays", 21));
    }

    public void maybeRun(int when, String who) {
        if (Settings.isReadOnly())
            return;

        if (loggingEnabled() && when == SHUTDOWN)
            stopLogging();

        if (Settings.getBool("backup.enabled", true)) {
            ProfTimer pt = new ProfTimer(FileBackupManager.class,
                    "FileBackupManager.run");
            try {
                runImpl(when, who, false);
            } catch (Throwable t) {}
            pt.click("Finished backup");
        }

        if (loggingEnabled() && when == STARTUP)
            startLogging(workingDirectory.getDirectory());
    }


    public synchronized File run() {
        ProfTimer pt = new ProfTimer(FileBackupManager.class,
            "FileBackupManager.run");
        File result = runImpl(RUNNING, null, true);
        pt.click("Finished backup");

        return result;
    }


    private File runImpl(int when, String who, boolean externalCopyDesired) {

        boolean needExternalCopy = externalCopyDesired;

        String[] extraBackupDirs = getExtraBackupDirs();
        if (StringUtils.hasValue(who) && extraBackupDirs != null)
            needExternalCopy = true;

        File result = null;
        try{
            URL backupURL = workingDirectory.doBackup(WHEN_STR[when]);
            if (needExternalCopy)
                result = createExternalizedBackupFile(backupURL);
        } catch (IOException ioe) {
            printError(ioe);
            return null;
        }

        if (result != null && who != null && extraBackupDirs != null) {
            makeExtraBackupCopies(result, who, extraBackupDirs);
        }

        return result;
    }

    private static final String[] WHEN_STR = { "startup", "checkpoint",
            "shutdown" };


    private File createExternalizedBackupFile(URL backup) throws IOException {
        // open the existing backup file as a ZIP stream
        ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(
                backup.openStream()));

        // create a temporary file for externalizing purposes
        File result = File.createTempFile("pdash-backup", ".zip");
        result.deleteOnExit();
        ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(result)));

        ExternalResourceManager extMgr = ExternalResourceManager.getInstance();

        // copy all the files from the existing backup into the externalized
        // backup (but skip any files that appear to be externalized)
        ZipEntry e;
        while ((e = zipIn.getNextEntry()) != null) {
            String filename = e.getName();
            if (extMgr.isArchivedItem(filename))
                continue;

            ZipEntry eOut = new ZipEntry(filename);
            eOut.setTime(e.getTime());
            zipOut.putNextEntry(eOut);
            FileUtils.copyFile(zipIn, zipOut);
            zipOut.closeEntry();
        }
        zipIn.close();

        // now, ask the external resource manager to augment the ZIP.
        extMgr.addExternalResourcesToBackup(zipOut);
        zipOut.finish();
        zipOut.close();

        return result;
    }



    private void stopLogging() {
        if (logFile != null) try {
            ConsoleWindow.getInstalledConsole().setCopyOutputStream(null);
            logFile.flush();
            logFile.close();
        } catch (IOException ioe) { printError(ioe); }
    }

    private void startLogging(File dataDir) {
        try {
            File out = new File(dataDir, LOG_FILE_NAME);
            logFile = new FileOutputStream(out);
            ConsoleWindow.getInstalledConsole().setCopyOutputStream(logFile);
            System.out.println("Process Dashboard - logging started at " +
                               new Date());
            System.out.println(System.getProperty("java.vendor") +
                               " JRE " + System.getProperty("java.version") +
                               "; " + System.getProperty("os.name"));
            System.out.println("Using " + workingDirectory);
        } catch (IOException ioe) { printError(ioe); }
    }

    private static boolean loggingEnabled() {
        return Settings.getBool("logging.enabled", true);
    }

    private static String[] getExtraBackupDirs() {
        String extraBackupDirs = InternalSettings.getExtendableVal(
            "backup.extraDirectories", ";");
        if (!StringUtils.hasValue(extraBackupDirs))
            return null;
        else
            return extraBackupDirs.replace('/', File.separatorChar).split(";");
    }

    private static void makeExtraBackupCopies(File backupFile, String who,
            String[] dirNames) {
        if (backupFile == null
                || who == null || who.length() == 0
                || dirNames == null || dirNames.length == 0)
            return;

        String filename = "backup-" + FileUtils.makeSafe(who) + ".zip";
        for (int i = 0; i < dirNames.length; i++) {
            ThreadThrottler.tick();
            File copy = new File(dirNames[i], filename);
            try {
                FileUtils.copyFile(backupFile, copy);
            } catch (Exception e) {
                System.err.println("Warning: unable to make extra backup to '"
                        + copy + "'");
            }
        }
    }

    public static boolean inBackupSet(File dir, String name) {
        return DashboardBackupFactory.DASH_FILE_FILTER.accept(dir, name);
    }

    private static void printError(Throwable t) {
        printError("Unexpected error in FileBackupManager", t);
    }

    private static void printError(String msg, Throwable t) {
        System.err.println(msg);
        t.printStackTrace();
    }


    public static class BGTask implements Runnable {

        private DashboardContext context;

        public void setDashboardContext(DashboardContext context) {
            this.context = context;
        }

        public void run() {
            try {
                ProcessDashboard dash = (ProcessDashboard) context;
                String ownerName = ProcessDashboard.getOwnerName(context
                        .getData());
                dash.fileBackupManager.maybeRun(FileBackupManager.RUNNING,
                    ownerName);
            } catch (Exception e) {
                logger.log(Level.SEVERE,
                        "Encountered exception when performing auto backup", e);
            }
        }

    }

}
