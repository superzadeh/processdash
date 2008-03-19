// Copyright (C) 2008 Tuma Solutions, LLC
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

package net.sourceforge.processdash.tool.bridge.client;


import static net.sourceforge.processdash.tool.bridge.ResourceFilterFactory.INCLUDE_PARAM;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.sourceforge.processdash.tool.bridge.ResourceBridgeConstants;
import net.sourceforge.processdash.tool.bridge.ResourceCollection;
import net.sourceforge.processdash.tool.bridge.ResourceCollectionInfo;
import net.sourceforge.processdash.tool.bridge.ResourceFilterFactory;
import net.sourceforge.processdash.tool.bridge.ResourceListing;
import net.sourceforge.processdash.tool.bridge.report.ListingHashcodeCalculator;
import net.sourceforge.processdash.tool.bridge.report.ResourceCollectionDiff;
import net.sourceforge.processdash.tool.bridge.report.ResourceContentStream;
import net.sourceforge.processdash.tool.bridge.report.XmlCollectionListing;
import net.sourceforge.processdash.util.ClientHttpRequest;
import net.sourceforge.processdash.util.FileUtils;
import net.sourceforge.processdash.util.HTMLUtils;
import net.sourceforge.processdash.util.HTTPUtils;
import net.sourceforge.processdash.util.ProfTimer;
import net.sourceforge.processdash.util.StringUtils;
import net.sourceforge.processdash.util.lock.AlreadyLockedException;
import net.sourceforge.processdash.util.lock.LockFailureException;
import net.sourceforge.processdash.util.lock.LockUncertainException;
import net.sourceforge.processdash.util.lock.NotLockedException;

public class ResourceBridgeClient implements ResourceBridgeConstants {

    private static final String CLIENT_VERSION = "1.0";

    ResourceCollection localCollection;

    String remoteUrl;

    String userName;

    private static final Logger logger = Logger
            .getLogger(ResourceBridgeClient.class.getName());


    public ResourceBridgeClient(ResourceCollection localCollection,
            String remoteUrl) {
        this.localCollection = localCollection;
        this.remoteUrl = remoteUrl;
    }

    public boolean syncDown() throws IOException {
        ProfTimer pt = new ProfTimer(logger);
        // compare hashcodes to see if the local and remote directories have
        // identical contents
        if (hashesMatch()) {
            return false;
        }

        // make a complete comparison of local-vs-remote changes.
        ResourceCollectionDiff diff = getDiff();
        pt.click("Computed local-vs-remote diff");
        if (diff == null || diff.noDifferencesFound())
            return false;

        // if any files are present only in our local collection (but not in
        // the remote collection), delete the local files.
        for (String resourceName : diff.getOnlyInA()) {
            logger.fine("deleting local resource " + resourceName);
            localCollection.deleteResource(resourceName);
        }

        // copy down files that are only present in the remote collection
        List includes = new ArrayList();
        addMultiple(includes, INCLUDE_PARAM, diff.getOnlyInB());
        addMultiple(includes, INCLUDE_PARAM, diff.getDiffering());
        if (!includes.isEmpty())
            downloadFiles(makeGetRequest(DOWNLOAD_ACTION, includes));

        return true;
    }

    /**
     * Apply changes to the collection on the server to make it match the
     * contents of the local collection.
     * 
     * @return true if any changes were made, false if the collections were
     *         already in sync.
     * @throws LockFailureException
     *                 if this client does not own a write lock on the server
     *                 collection
     * @throws IOException
     *                 if any other problem prevents the sync from succeeding
     */
    public boolean syncUp() throws IOException, LockFailureException {
        if (userName == null)
            throw new NotLockedException();

        ResourceCollectionDiff diff = getDiff();
        if (diff == null || diff.noDifferencesFound()) {
            logger.finer("no changes to sync up");
            return false;
        }

        boolean madeChange = false;

        // if any files are present only in the remote collection (but not in
        // our local collection), delete the remote files.
        if (!diff.getOnlyInB().isEmpty()) {
            List<String> params = new ArrayList<String>();
            for (String resourceName : diff.getOnlyInB()) {
                logger.fine("deleting remote resource " + resourceName);
                params.add(DELETE_FILE_PARAM);
                params.add(resourceName);
            }
            doPostRequest(DELETE_ACTION, (Object[]) params.toArray());
            madeChange = true;
        }

        // upload files that need to be created or updated in the remote
        // collection
        if (!diff.getOnlyInA().isEmpty() || !diff.getDiffering().isEmpty()) {
            List params = new ArrayList();
            for (String resourceName : diff.getOnlyInA()) {
                logger.fine("uploading new resource " + resourceName);
                params.add(resourceName);
                params.add(localCollection.getInputStream(resourceName));
            }
            for (String resourceName : diff.getDiffering()) {
                logger.fine("uploading modified resource " + resourceName);
                params.add(resourceName);
                params.add(localCollection.getInputStream(resourceName));
            }
            doPostRequest(UPLOAD_ACTION, (Object[]) params.toArray());
            madeChange = true;
        }

        if (!madeChange) {
            logger.finer("no changes to sync up");
        }

        return madeChange;
    }

    public void saveLogFile() throws IOException, LockFailureException {
        if (userName == null)
            throw new NotLockedException();

        logger.fine("uploading log.txt file");
        doPostRequest(UPLOAD_ACTION, "log.txt", localCollection
                .getInputStream("log.txt"));
    }

    public URL doBackup(String qualifier) throws IOException {
        try {
            doPostRequest(BACKUP_ACTION, BACKUP_QUALIFIER_PARAM, qualifier);
            logger.fine("Backed up data, qualifer = " + qualifier);
        } catch (LockFailureException e) {
            // shouldn't happen
            logger.log(Level.SEVERE, "Received unexpected exception", e);
        }
        StringBuffer result = new StringBuffer(remoteUrl);
        HTMLUtils.appendQuery(result, VERSION_PARAM, CLIENT_VERSION);
        HTMLUtils.appendQuery(result, ACTION_PARAM, GET_BACKUP_ACTION);
        return new URL(result.toString());
    }

    public void acquireLock(String userName) throws LockFailureException {
        try {
            this.userName = userName;
            doPostRequest(ACQUIRE_LOCK_ACTION);
            logger.fine("Acquired bridged lock");
        } catch (Exception e) {
            this.userName = null;
            throw new LockFailureException(e);
        }
    }

    public void pingLock() throws LockFailureException {
        if (userName == null)
            throw new NotLockedException();

        try {
            doPostRequest(PING_LOCK_ACTION);
            logger.fine("Pinged bridged lock");
        } catch (LockFailureException lfe) {
            throw lfe;
        } catch (Exception e) {
            throw new LockUncertainException(e);
        }
    }

    public void assertLock() throws LockFailureException {
        if (userName == null)
            throw new NotLockedException();

        try {
            doPostRequest(ASSERT_LOCK_ACTION);
            logger.fine("Asserted bridged lock");
        } catch (LockFailureException lfe) {
            throw lfe;
        } catch (Exception e) {
            throw new LockUncertainException(e);
        }
    }

    public void releaseLock() {
        if (userName == null)
            return;

        try {
            doPostRequest(RELEASE_LOCK_ACTION);
            logger.fine("Released bridged lock");
        } catch (Exception e) {
            // We don't throw any error here, because if we fail to release
            // the bridged lock, the worst case scenario is that it will time
            // out on the server after 5 minutes or so.  Log a message for
            // posterity.
            logger.log(Level.FINE, "Unable to release bridged lock", e);
        }
    }

    private boolean hashesMatch() throws IOException {
        // start by initiating the HTTP connection to the server
        URLConnection conn = makeGetRequest(HASHCODE_ACTION);
        conn.connect();
        // now, while the server is thinking, do our calculations locally.
        long localHash = ListingHashcodeCalculator.getListingHashcode(
            localCollection, ResourceFilterFactory.DEFAULT_FILTER);
        // finally, retrieve the value from the server and compare the two.
        String hashResult = HTTPUtils.getResponseAsString(conn);
        long remoteHash = Long.valueOf(hashResult);
        return (localHash == remoteHash);
    }

    private ResourceCollectionDiff getDiff() throws IOException {
        // start by initiating the HTTP connection to the server
        URLConnection conn = makeGetRequest(LIST_ACTION);
        conn.connect();
        // now, while the server is thinking, do our calculations locally.
        ResourceCollectionInfo localList = new ResourceListing(localCollection,
                ResourceFilterFactory.DEFAULT_FILTER);
        // finally, retrieve the list from the server and compare the two.
        ResourceCollectionInfo remoteList = XmlCollectionListing
                .parseListing(new BufferedInputStream(conn.getInputStream()));
        return new ResourceCollectionDiff(localList, remoteList);
    }

    private ResourceCollectionInfo downloadFiles(URLConnection conn)
            throws IOException {
        ResourceCollectionInfo info = null;

        InputStream response = new BufferedInputStream(conn.getInputStream());
        ZipInputStream zipIn = new ZipInputStream(response);
        ZipEntry e;
        while ((e = zipIn.getNextEntry()) != null) {
            String name = e.getName();
            if (ResourceContentStream.MANIFEST_FILENAME.equals(name)) {
                InputStream infoIn = new ByteArrayInputStream(FileUtils
                        .slurpContents(zipIn, false));
                info = XmlCollectionListing.parseListing(infoIn);
                continue;
            }

            logger.fine("downloading resource " + name);
            OutputStream out = localCollection.getOutputStream(name);
            if (out == null)
                // this would occur if we receive a file we don't recognize as
                // a member of our collection. Discard it.
                continue;
            FileUtils.copyFile(zipIn, out);
            out.close();
            zipIn.closeEntry();
        }
        zipIn.close();
        return info;
    }

    private HttpURLConnection makeGetRequest(String action, List parameters)
            throws IOException {
        return makeGetRequest(action, parameters.toArray());
    }

    private HttpURLConnection makeGetRequest(String action,
            Object... parameters) throws IOException {
        StringBuffer request = new StringBuffer(remoteUrl);
        HTMLUtils.appendQuery(request, VERSION_PARAM, CLIENT_VERSION);
        HTMLUtils.appendQuery(request, ACTION_PARAM, action);
        for (int i = 0; i < parameters.length; i += 2) {
            HTMLUtils.appendQuery(request, String.valueOf(parameters[i]),
                String.valueOf(parameters[i + 1]));
        }
        String requestStr = request.toString();

        URLConnection conn;
        if (requestStr.length() < MAX_URL_LENGTH) {
            URL url = new URL(requestStr);
            conn = url.openConnection();

        } else {
            int queryPos = requestStr.indexOf('?');
            byte[] query = requestStr.substring(queryPos + 1).getBytes(
                HTTPUtils.DEFAULT_CHARSET);
            URL url = new URL(requestStr.substring(0, queryPos));
            conn = url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", Integer
                    .toString(query.length));
            OutputStream outputStream = new BufferedOutputStream(conn
                    .getOutputStream());
            outputStream.write(query);
            outputStream.close();
        }

        return (HttpURLConnection) conn;
    }

    private void doPostRequest(String action, Object... params)
            throws IOException, LockFailureException {
        ClientHttpRequest request = new ClientHttpRequest(remoteUrl);
        request.setParameter(VERSION_PARAM, CLIENT_VERSION);
        request.setParameter(ACTION_PARAM, action);
        request.setParameter(EXTRA_INFO_PARAM, userName);
        try {
            InputStream in = request.post(params);
            FileUtils.slurpContents(in, true);
        } catch (IOException ioe) {
            checkForLockException(request.getConnection());
            throw ioe;
        }
    }

    /**
     * Look at the headers from an HTTP response, and see if they specify a lock
     * failure exception. If so, create an appropriate exception and throw it.
     * 
     * If the response headers do <b>not</b> specify a lock failure, this
     * method will do nothing and return.
     * 
     * @param conn
     *                a connection to a URL
     * @throws LockFailureException
     *                 if the connection was an HTTP connection and if the
     *                 response headers indicate a lock failure
     * @throws IOException
     *                 if an error occurred when attempting to examine the
     *                 response headers
     */
    private void checkForLockException(URLConnection conn) throws IOException,
            LockFailureException {
        if (conn instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) conn;
            int code = http.getResponseCode();
            if (code != HttpURLConnection.HTTP_CONFLICT)
                return;

            String exceptionClass = http.getHeaderField(LOCK_EXCEPTION_HEADER);
            if (!StringUtils.hasValue(exceptionClass))
                return;

            if (exceptionClass.equals(AlreadyLockedException.class.getName())) {
                String extraInfo = http.getHeaderField(ALREADY_LOCKED_HEADER);
                throw new AlreadyLockedException(extraInfo);
            }

            LockFailureException lfe;
            try {
                Class clazz = Class.forName(exceptionClass);
                lfe = (LockFailureException) clazz.newInstance();
            } catch (Exception e) {
                lfe = new LockFailureException(http.getResponseMessage());
            }
            throw lfe;
        }
    }

    private static List addMultiple(List l, String paramName, List values) {
        if (l == null)
            l = new ArrayList();
        for (Object value : values) {
            l.add(paramName);
            l.add(value);
        }
        return l;
    }

    // when constructing a URL to retrieve via the HTTP GET method, this
    // is our limit on the maximum allowed length of that URL. If the URL
    // is more than this many characters, it will be converted into a POST
    // request instead.
    private static final int MAX_URL_LENGTH = 512;

}
