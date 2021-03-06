// Copyright (C) 2008-2009 Tuma Solutions, LLC
// Process Dashboard - Data Automation Tool for high-maturity processes
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

package net.sourceforge.processdash.net.http;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class DashboardHelpURLConnection extends DashboardURLConnection {

    public static final String DASHHELP_PROTOCOL = WebServer.DASHBOARD_PROTOCOL
            + "-help";

    private static int MAX_WIDTH = 555;

    private static boolean ENABLE_RESIZING = true;

    public DashboardHelpURLConnection(WebServer webServer, URL url) {
        super(webServer, url);
    }

    @Override
    protected InputStream createResponseStream(final byte[] rawData,
            final int headerLen) {
        InputStream result = null;

        String contentType = getHeaderField("Content-Type");
        if (ENABLE_RESIZING && "image/png".equals(contentType)) {
            result = AccessController.doPrivileged(
                new PrivilegedAction<InputStream>() {
                    public InputStream run() {
                        try {
                            return maybeResizeImage(rawData, headerLen);
                        } catch (IOException ioe) {
                            return null;
                        } catch (Throwable t) {
                            // Some versions of Java on Solaris are ill-behaved,
                            // and throw Errors from the ImageIO class. Don't
                            // let those crash the Process Dashboard.
                            ENABLE_RESIZING = false;
                            return null;
                        }
                    }});
        }

        if (result == null)
            result = super.createResponseStream(rawData, headerLen);

        return result;
    }

    private InputStream maybeResizeImage(byte[] rawData, int headerLen)
            throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(rawData,
                headerLen, rawData.length - headerLen));
        // if we couldn't understand the image, resend the raw data.
        if (image == null)
            return null;

        // if the image is already small enough. Just resend the raw data.
        if (image.getWidth() <= MAX_WIDTH)
            return null;

        // resize the image to fit within the desired width.
        Image resizedImage = image.getScaledInstance(MAX_WIDTH, -1,
            Image.SCALE_SMOOTH);

        // force the image resize operation to finish before continuing.
        ImageIcon icon = new ImageIcon(resizedImage);
        int rWidth = icon.getIconWidth();
        int rHeight = icon.getIconHeight();

        // paint the image into a buffer
        BufferedImage resizedBuf = new BufferedImage(rWidth, rHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = resizedBuf.createGraphics();
        g2.setColor(Color.white);
        g2.fillRect(0, 0, rWidth, rHeight);
        g2.drawImage(icon.getImage(), 0, 0, null);
        g2.dispose();

        // Compute the binary PNG representation of the image
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ImageIO.write(resizedBuf, "PNG", outStream);

        // return an input stream containing the generated bytes.
        return new ByteArrayInputStream(outStream.toByteArray());
    }

    /** Set the maximum width of a PNG image served by this protocol.
     * 
     * Any PNG files that are larger than this will be resized on the fly.
     * 
     * @param width the maximum image width, in pixels
     */
    public static void setMaxImageWidth(int width) {
        MAX_WIDTH = width;
    }

    /** Get the maximum width of a PNG image served by this protocol.
     *
     * @return the maximum image width, in pixels
     */
    public static int getMaxImageWidth() {
        return MAX_WIDTH;
    }

}
