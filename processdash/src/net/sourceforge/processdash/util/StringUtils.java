/*
StringUtils.java : A stand alone utility class.
Copyright (C) 2000 Justin P. McCarthy

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

To further contact the author please email jpmccar@gjt.org

*/
/*
    changes:
        Sep 18, created
*/
//package com.justinsbrain.gnu.util;
package net.sourceforge.processdash.util;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
/**
    Just some utility methods for working with Strings.
    @author Justin P. McCarthy
*/
public class StringUtils
{
    /**
        So no one can create one of me.
    */
    private StringUtils()
    {
    }

    /**
        This uses the Character.isLetterOrDigit method to
        determine whether the character needs to be escaped.
        If it does then it is precedded by a slash('\').

        @param toEscape The string to escape characters in.
        @return The new, escaped, string.
    */
    public static final String escapeString(String toEscape)
    {
        StringBuffer toReturn = new StringBuffer();

        for (int i = 0; i < toEscape.length(); i++)
        {
            if (!Character.isLetterOrDigit(toEscape.charAt(i)))
            {
                toReturn.append('\\');
            }
            toReturn.append(toEscape.charAt(i));
        }

        return toReturn.toString();
    }


    /**
        A simpl find and replace.
        @param text The original string.
        @param find The text to look for.
        @param replace What to replace the found text with.
        @return The new string.
    */
    public static final String findAndReplace(
        String text,
        String find,
        String replace)
    {
        if ((text == null) ||
            (find == null) ||
            (replace == null))
        {
            throw new NullPointerException(
                "findAndReplace doesn't work on nulls.");
        }

        // handle degenerate case: if no replacements need to be made,
        // return the original text unchanged.
        int replaceStart = text.indexOf(find);
        if (replaceStart == -1) return text;

        int findLength = find.length();
        StringBuffer toReturn = new StringBuffer();

        while (replaceStart != -1)
        {
            toReturn.append(text.substring(0, replaceStart)).append(replace);
            text = text.substring(replaceStart+findLength);
            replaceStart = text.indexOf(find);
        }

        toReturn.append(text);
        return toReturn.toString();

    }

    public static final void findAndReplace(StringBuffer text,
                                            String find,
                                            String replace) {

        if ((text == null) ||
            (find == null) ||
            (replace == null))
        {
            throw new NullPointerException(
                "findAndReplace doesn't work on nulls.");
        }

        int findLength = find.length(), replaceLength = replace.length();
        int pos = 0, replaceStart = indexOf(text, find);
        while (replaceStart != -1) {
            text.replace(replaceStart, replaceStart+findLength, replace);
            pos = replaceStart + replaceLength;
            replaceStart = indexOf(text, find, pos);
        }
    }

    /**
        Breaks a string down to chunks of a given size or less.
        It will try and break chunks on standard symbols like the space,
        tab, and enter.  If it can't do it there it will try other
        characters frequently found but not usually in a word like a colon,
        semi-colon, parens, comma, etc...
        @param toBreakApart The big String.
        @param breakPoint The number of characters not to exceed on a line.
        @param shouldTrim Whether this should trim beginning and ending
            whitespace
        @return The array of little strings.
        @throws NullPointerException if the string to break apart is null.
        @throws IllegalArgumentException if the int break point is less than 1
    */
    public static final String[] breakDownString(
        String toBreakApart,
        int breakPoint,
        boolean shouldTrim)
    {
        // can't break apart a String that doesn't exist!
        if (toBreakApart == null)
        {
            throw new NullPointerException(
                "breakDownString can't break down nulls!");
        }

        // can't break strings on smaller than one character chunks.
        if (breakPoint < 1)
        {
            throw new IllegalArgumentException(
                "breakDownString can't break them smaller than one character!");
        }

        // if the string to break apart is smaller than what is needed
        // stop processing now
        if (toBreakApart.length() <= breakPoint)
        {
            return new String[] { toBreakApart };
        }

        StringBuffer sb = new StringBuffer(toBreakApart);
        ArrayList workArray = new ArrayList();
        int pointer = 0;

        // forever...
        while (true)
        {
            // if the string buffer is now less than the break point
            // add what's left to the working array and break
            // out of it all..
            if (sb.length() < breakPoint)
            {
                if (shouldTrim)
                {
                    workArray.add(sb.toString().trim());
                }
                else
                {
                    workArray.add(sb.toString());
                }
                break;
            }
            else
            {
                // else run through the string from the break point
                // down to zero and look for something good to break on
                // I use java.lang.Character's isLetterOrDigit(char) method
                // do determine if it is a good place to break.
                for (pointer = breakPoint; pointer > 0; pointer--)
                {
                    if (!Character.isLetterOrDigit(sb.charAt(pointer)))
                    {
                        // found something good to break on.
                        // add everything before it to the array,
                        // delete everything added from the stringbuffer
                        // and break out of this inner loop.
                        if (shouldTrim)
                        {
                            workArray.add(sb.substring(0, pointer).trim());
                        }
                        else
                        {
                            workArray.add(sb.substring(0, pointer));
                        }
                        sb.delete(0,pointer);
                        break;
                    }
                }

                // if pointer is 0 then we got all the way through without
                // find a good character to break on.  we'll go ahead
                // and take a full length line.
                if (pointer == 0)
                {
                    workArray.add(sb.substring(0, breakPoint));
                    sb.delete(0,breakPoint);
                }
            }
        }

        // if the last element of the array is nothing but blanks remove it...
        if (((String)workArray.get(workArray.size() - 1 )).trim().length() == 0)
        {
            workArray.remove(workArray.size() - 1);
        }

        // this is a strange method call in ArrayList.
        // you pass it the run-time type of array you want back from your
        // array list and if the objects can be cast correctly.  voila!
        return (String[])workArray.toArray(new String[0]);
    }

    /** Performs the equivalent of buf.toString().indexOf(str), but is
     *  much more memory efficient. */
    public static int indexOf(StringBuffer buf, String str) {
        return indexOf(buf, str, 0);
    }
    /** Performs the equivalent of buf.toString().indexOf(str, fromIndex),
     *  but is much more memory efficient. */
    public static int indexOf(StringBuffer buf, String str, int fromIndex) {
        return indexOf(buf, str, fromIndex, false);
    }
    public static int indexOf(StringBuffer buf, String str, int fromIndex,
                              boolean ignoreCase) {
        int count = buf.length();
        int max = count - str.length();
        if (fromIndex >= count) {
            if (count == 0 && fromIndex == 0 && str.length() == 0)
                return 0;
            else
                return -1;
        }
        if (fromIndex < 0) fromIndex = 0;
        if (str.length() == 0) return fromIndex;

        if (ignoreCase) str = str.toLowerCase();
        char first = str.charAt(0);
        int i = fromIndex;

    startSearchForFirstChar:
        while (true) {
            /* Look for first character. */
            while (i <= max &&
                   (ignoreCase ? lower_(buf.charAt(i)) : buf.charAt(i)) != first)
                i++;
            if (i > max)
                return -1;

            /* Found first character, now look at the rest of str */
            int j = i + 1;
            int end = j + str.length() - 1;
            int k = 1;
            while (j < end) {
                if ((ignoreCase ? lower_(buf.charAt(j)) : buf.charAt(j))
                      != str.charAt(k++)) {
                    i++;
                    /* Look for str's first char again. */
                    continue startSearchForFirstChar;
                }
                j++;
            }
            return i; /* Found whole string. */
        }
    }
    private static final char lower_(char c) { return Character.toLowerCase(c); }

    /** Performs the equivalent of buf.toString().lastIndexOf(str), but is
     *  much more memory efficient. */
    public static int lastIndexOf(StringBuffer buf, String str) {
        return lastIndexOf(buf, str, buf.length());
    }

    /** Performs the equivalent of buf.toString().lastIndexOf(str, fromIndex),
     *  but is much more memory efficient. */
    public static int lastIndexOf(StringBuffer buf, String str, int fromIndex) {
        int count = buf.length();
        int rightIndex = count - str.length();
        if (fromIndex < 0) return -1;
        if (fromIndex > rightIndex) fromIndex = rightIndex;

        /* Empty string always matches. */
        if (str.length() == 0) return fromIndex;

        int strLastIndex = str.length() - 1;
        char strLastChar = str.charAt(strLastIndex);
        int min = str.length() - 1;
        int i = min + fromIndex;

    startSearchForLastChar:
        while (true) {
            /* Look for the last character */
            while (i >= min && buf.charAt(i) != strLastChar)
                i--;
            if (i < min) return -1;

            /* Found last character, now look at the rest of str. */
            int j = i - 1;
            int start = j - (str.length() - 1);
            int k = strLastIndex - 1;

            while (j > start) {
                if (buf.charAt(j--) != str.charAt(k--)) {
                    i--;
                    /* Look for str's last char again. */
                    continue startSearchForLastChar;
                }
            }

            return start + 1;    /* Found whole string. */
        }
    }


    public static String[] split(String s, String delims) {
        ArrayList result = new ArrayList();
        StringTokenizer tok = new StringTokenizer(s, delims);
        while (tok.hasMoreTokens())
            result.add(tok.nextToken());
        return (String[]) result.toArray(new String[0]);
    }

    public static String join(Collection c, String delim) {
        if (c == null || c.isEmpty())
            return "";
        else if (c.size() == 1)
            return String.valueOf(c.iterator().next());

        StringBuffer result = new StringBuffer();
        for (Iterator i = c.iterator(); i.hasNext();)
            result.append(delim).append(i.next());
        return result.substring(delim.length());
    }


    public static final String VAR_START_PAT = "${";
    public static final String VAR_END_PAT = "}";
    public static String interpolate(StringMapper map, String s) {
        int max_nesting = 1000;
        while (max_nesting-- > 0) {
            int beg = s.indexOf(VAR_START_PAT);
            if (beg == -1) return s;

            int end = s.indexOf(VAR_END_PAT, beg);
            if (end == -1) return s;

            String var = s.substring(beg+VAR_START_PAT.length(), end);
            String replacement = map.getString(var);
            s = s.substring(0, beg) + replacement +
                s.substring(end+VAR_END_PAT.length());
        }
        throw new IllegalArgumentException
            ("Infinite recursion when interpolating.");
    }
    public static String interpolate(Map map, String s) {
        return interpolate(stringMapper(map), s);
    }

    public static StringMapper stringMapper(Map m) {
        return new MapStringMapper(m);
    }
    private static final class MapStringMapper implements StringMapper {
        private Map m;
        public MapStringMapper(Map m) {
            this.m = m;
        }
        public String getString(String str) {
            str = (String) m.get(str);
            return str == null ? "" :  str;
        }
    }

    public static StringMapper concat(StringMapper outerMap,
                                      StringMapper innerMap) {
        return new ConcatStringMapper(outerMap, innerMap);
    }

    private static final class ConcatStringMapper implements StringMapper {

        private StringMapper outerMap, innerMap;

        public ConcatStringMapper(StringMapper outerMap, StringMapper innerMap) {
            this.outerMap = outerMap;
            this.innerMap = innerMap;
        }

        public String getString(String str) {
            return outerMap.getString(innerMap.getString(str));
        }
    }
}
