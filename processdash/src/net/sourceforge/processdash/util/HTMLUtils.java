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

package net.sourceforge.processdash.util;


import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;


public class HTMLUtils {

    public static String escapeEntities(String value) {
        StringTokenizer tok = new StringTokenizer(value, "<>&\"", true);
        StringBuffer result = new StringBuffer();
        String token;
        while (tok.hasMoreTokens()) {
            token = tok.nextToken();
            if      ("<".equals(token))  result.append("&lt;");
            else if (">".equals(token))  result.append("&gt;");
            else if ("&".equals(token))  result.append("&amp;");
            else if ("\"".equals(token)) result.append("&quot;");
            else                         result.append(token);
        }
        return result.toString();
    }

    public static final StringMapper ESC_ENTITIES = new StringMapper() {
        public String getString(String str) {
            return escapeEntities(str);
        }
    };

    public static String unescapeEntities(String value) {
        value = StringUtils.findAndReplace(value, "&lt;",   "<");
        value = StringUtils.findAndReplace(value, "&gt;",   ">");
        value = StringUtils.findAndReplace(value, "&quot;", "\"");
        value = StringUtils.findAndReplace(value, "&apos;", "'");
        value = StringUtils.findAndReplace(value, "&amp;",  "&");
        return value;
    }

    public static final StringMapper UNESC_ENTITIES = new StringMapper() {
        public String getString(String str) {
            return unescapeEntities(str);
        }
    };

    public static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // can't happen
            return null;
        }
    }

    public static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // can't happen
            return null;
        }
    }

    /** parse the inner contents of a tag as a set of
     * attrName=attrValue pairs
     * @param contents the text beginning after the tag name and ending
     *        before the closing ">" or "/>"
     */
    public static Map parseAttributes(String contents) {
        HashMap result = new HashMap();
        if (contents == null || contents.length() == 0) return result;

        String attrs = contents, name, value;
        int equalsPos, spacePos;
        while ((equalsPos = attrs.indexOf('=')) != -1) {
            name = attrs.substring(0, equalsPos).trim();
            attrs = attrs.substring(equalsPos+1).trim();
            if (attrs.length() == 0) break;
            if (name.startsWith(">") || name.startsWith("/>")) break;

            int endPos;
            if (attrs.charAt(0) == '\'' || attrs.charAt(0) == '"') {
                endPos = attrs.indexOf(attrs.charAt(0), 1);
                if (endPos == -1) {
                    value = attrs; attrs = "";
                } else {
                    value = attrs.substring(1, endPos);
                    attrs = attrs.substring(endPos+1);
                }
            } else if (attrs.charAt(0) == '[') {
                endPos = attrs.indexOf(']', 1);
                if (endPos == -1) {
                    value = attrs; attrs = "";
                } else {
                    value = attrs.substring(0, endPos+1);
                    attrs = attrs.substring(endPos+1);
                }
            } else {
                endPos = whitespacePos(attrs);
                if (endPos == -1) endPos = attrs.length();
                value = attrs.substring(0, endPos);
                attrs = attrs.substring(endPos);
            }
            value = unescapeEntities(value);
            result.put(name, value);
        }
        return result;
    }

    private static int whitespacePos(String t) {
        int result = t.indexOf(' ');
        if (result == -1) result = t.indexOf('\t');
        if (result == -1) result = t.indexOf('\r');
        if (result == -1) result = t.indexOf('\n');
        return result;
    }
}