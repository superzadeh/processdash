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

import pspdash.data.DataRepository;
import pspdash.data.ListData;
import pspdash.data.SimpleData;
import pspdash.data.StringData;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;

import com.oroinc.text.perl.Perl5Util;


/** Class for performing server-side includes and other preprocessing on
 *  HTML files.
 */
public class HTMLPreprocessor {

    TinyWebServer web;
    DataRepository data;
    Map env, params;
    String prefix;
    String defaultEchoEncoding = null;


    public HTMLPreprocessor(TinyWebServer web, DataRepository data, Map env) {
        this(web, data, (String) env.get("PATH_TRANSLATED"), env, null);
        QueryParser p = new QueryParser();
        try {
            p.parseInput((String) env.get("QUERY_STRING"));
        } catch (IOException ioe) {}
        params = p.parameters;
    }

    public HTMLPreprocessor(TinyWebServer web, DataRepository data,
                            String prefix, Map env, Map params) {
        this.web = web;
        this.data = data;
        this.prefix = (prefix == null ? "" : prefix);
        this.env = env;
        this.params = params;
    }


    /** preprocess the given content, and return the result. */
    public String preprocess(String content) throws IOException {
        StringBuffer text = new StringBuffer(content);
        cachedTestExpressions.clear();

        numberBlocks(text, "foreach", "endfor", null, null);
        numberBlocks(text, "if", "endif", "else", "elif");

        DirectiveMatch dir;
        int pos = 0;
        while ((dir = new DirectiveMatch(text, "", pos, true)).matches()) {
            if ("echo".equals(dir.directive))
                processEchoDirective(dir);
            else if ("include".equals(dir.directive))
                processIncludeDirective(dir);
            else if (blockMatch("foreach", dir.directive))
                processForeachDirective(dir);
            else if (blockMatch("if", dir.directive))
                processIfDirective(dir);
            else if ("incr".equals(dir.directive))
                processIncrDirective(dir);
            else if ("break".equals(dir.directive))
                processBreakDirective(dir);
            else
                dir.replace("");
            pos = dir.end;
        }
        return text.toString();
    }


    /** process an include directive within the buffer */
    private void processIncludeDirective(DirectiveMatch include)
        throws IOException
    {
        // what file do they want us to include?
        String url = include.getAttribute("file");
        if (isNull(url))
            include.replace(""); // no file specified - delete this directive.
        else {
            // fetch the requested url (relative to the current url) and
            // replace the include directive with its contents.
            String context = (String) env.get("REQUEST_URI");
            include.replace(new String(web.getRequest(context, url, true)));
        }
    }


    /** process an echo directive within the buffer */
    private void processEchoDirective(DirectiveMatch echo) {
        String var, value, encoding;

        // Was an explicit value specified? (This is used for performing
        // encodings on strings, and is especially useful when the string
        // in question came from interpolating a foreach statement.)
        value = echo.getAttribute("value");

        if (isNull(value)) {
            // was a variable name specified? If so, look up the associated
            // string value.
            var = echo.getAttribute("var");
            if (isNull(var)) var = echo.contents.trim();
            value = (isNull(var) ? "" : getString(var));
        }

        // Apply the requested encoding(s)
        String encodings = echo.getAttribute("encoding");
        if (encodings == null) encodings = defaultEchoEncoding;
        value = applyEncodings(value, encodings);

        // replace the echo directive with the resulting value.
        echo.replace(value);
    }
    public void setDefaultEchoEncoding(String enc) {
        defaultEchoEncoding = enc;
    }
    public static String applyEncodings(String value, String encodings) {
        if (encodings == null || "none".equalsIgnoreCase(encodings))
            return value;
        StringTokenizer tok = new StringTokenizer(encodings, ",");
        String encoding;
        while (tok.hasMoreTokens()) {
            encoding = tok.nextToken();

            if ("url".equalsIgnoreCase(encoding)) {
                // url encode the value
                value = URLEncoder.encode(value);
                value = StringUtils.findAndReplace(value, "%2F", "/");
                value = StringUtils.findAndReplace(value, "%2f", "/");
            } else if ("xml".equalsIgnoreCase(encoding))
                // encode the value as an xml entity
                value = XMLUtils.escapeAttribute(value);
            else if ("data".equalsIgnoreCase(encoding))
                value = AutoData.esc(value);
            else
                // default: HTML entity encoding
                value = HTMLUtils.escapeEntities(value);
        }

        return value;
    }


    /** process a foreach directive within the buffer */
    private void processForeachDirective(DirectiveMatch foreach) {
        StringBuffer text = foreach.buf;
        String blockNum = blockNum("foreach", foreach.directive);
        // find the matching endfor.
        DirectiveMatch endfor = new DirectiveMatch
            (text, blockNum + "endfor", foreach.end, true);

        if (!endfor.matches()) {
            // if the endfor is missing, delete this directive and abort.
            System.err.println
                ("foreach directive without matching endfor - aborting.");
            foreach.replace("");
            return;
        }

        // get the list of values that we should iterate over. This can be
        // specified either as a list literal using the "values" attribute,
        // or via a list variable using the "list" attribute.
        String values = foreach.getAttribute("values");
        ListData list;
        if (isNull(values)) {
            String listName = foreach.getAttribute("list");
            list = getList(listName);
        } else {
            list = new ListData(values);
        }

        // iterate over the list and calculate the resulting contents.
        String loopIndex = foreach.getAttribute("name");
        String loopContents = text.substring(foreach.end, endfor.begin);
        StringBuffer replacement = new StringBuffer();
        String iterResults;
        for (int i = 0;   i < list.size();   i++) {
            iterResults = StringUtils.findAndReplace
                (loopContents, loopIndex, (String)list.get(i));
            /*
            iterResults = StringUtils.findAndReplace
                (iterResults,
                 DIRECTIVE_START + "0",
                 DIRECTIVE_START + "0" + i + "-");
            */
            replacement.append(iterResults);
        }

        // replace the directive with the iterated contents.  Note
        // that we explicitly replace the initial foreach tag with an
        // empty string, so the overall processing loop (in the
        // preprocess method) will process these iterated contents.
        text.replace(foreach.end, endfor.end, replacement.toString());
        foreach.replace("");
    }


    /** process an if directive within the buffer */
    private void processIfDirective(DirectiveMatch ifdir) {
        processIfDirective(ifdir, blockNum("if", ifdir.directive));
    }
    private void processIfDirective(DirectiveMatch ifdir, String blockNum) {
        StringBuffer text = ifdir.buf;
        // find the matching endif.
        DirectiveMatch endif = new DirectiveMatch
            (text, blockNum + "endif", ifdir.end, true);

        if (!endif.matches()) {
            // if the endif is missing, delete this directive and abort.
            System.err.println
                ("if directive without matching endif - aborting.");
            ifdir.replace("");
            return;
        }

        // See if there was an elif or an else.
        DirectiveMatch elsedir = new DirectiveMatch
            (text, blockNum + "elif", ifdir.end, true);
        if (!elsedir.matches() || elsedir.begin > endif.begin)
            elsedir = new DirectiveMatch
                (text, blockNum + "else", ifdir.end, true);
        if (elsedir.matches() && elsedir.begin > endif.begin)
            elsedir.begin = -1;

                                // if this is an else clause
        if (blockMatch("else", ifdir.directive) ||
                                // or if the test expression was true,
            ifTest(ifdir.contents))
        {
            endif.replace("");  // delete the endif
            if (elsedir.matches()) // delete the entire else clause if present
                text.replace(elsedir.begin, endif.begin, "");
            ifdir.replace("");  // delete the if directive.
        } else if (elsedir.matches()) {
            // if the test was false, and there was an else clause, evaluate
            // the else clause as its own if statement.
            processIfDirective(elsedir, blockNum);
            // then delete the "true" clause (the text between the if
            // and the else)
            text.replace(ifdir.end, elsedir.begin, "");
            // finally, delete the if directive itself.
            ifdir.replace("");
        } else {
            // if the test was false and there was no else clause, delete
            // everything.
            text.replace(ifdir.end, endif.end, "");
            ifdir.replace("");
        }
    }

    Map cachedTestExpressions = new HashMap();
    private boolean ifTest(String expression) {
        expression = expression.replace('\n',' ').replace('\t',' ').trim();
        Boolean result = (Boolean) cachedTestExpressions.get(expression);
        if (result == null) {
            boolean test = false;
            boolean reverse = false;
            boolean containsIncrementedVar = false;

            // if the expression contains multiple OR clauses, evaluate
            // them individually and return true if one is true.
            int orPos = expression.indexOf(" || ");
            if (orPos != -1) {
                expression = expression + " || ";
                String subExpr;
                while (orPos != -1) {
                    subExpr = expression.substring(0, orPos);
                    if (ifTest(subExpr)) return true;
                    expression = expression.substring(orPos+4);
                    orPos = expression.indexOf(" || ");
                }
                return false;
            }

            RelationalExpression re = parseRelationalExpression(expression);
            if (re != null) {
                test = re.test();
                containsIncrementedVar = re.containsIncrementedVar;
            } else {
                String symbolName = cleanup(expression);
                if (symbolName.startsWith("not") &&
                    whitespacePos(symbolName) == 3) {
                    reverse = true;
                    symbolName = cleanup(symbolName.substring(4));
                }
                if (incrVariables.contains(symbolName))
                    containsIncrementedVar = true;

                if (!isNull(symbolName))
                    test = (symbolName.startsWith("[") ?
                            testDataElem(symbolName) :
                            !isNull(getString(symbolName)));
                if (reverse)
                    test = !test;
            }
            result = test ? Boolean.TRUE : Boolean.FALSE;
            if (!containsIncrementedVar)
                cachedTestExpressions.put(expression, result);
        }
        return result.booleanValue();
    }

    private class RelationalExpression {
        public String lhs, operator, rhs;
        public boolean containsIncrementedVar = false;
        public boolean test() {
            if (lhs.length() == 0 || rhs.length() == 0) {
                System.err.println
                    ("malformed relational expression - aborting.");
                return false;
            }
            String lhval = getVal(lhs);
            String rhval = getVal(rhs);

            if ("eq".equals(operator)) return eq(lhval, rhval);
            if ("ne".equals(operator)) return !eq(lhval, rhval);
            if ("=~".equals(operator)) return matches(lhval, rhval);
            if ("!~".equals(operator)) return !matches(lhval, rhval);
            if ("gt".equals(operator)) return gt(lhval, rhval);
            if ("lt".equals(operator)) return lt(lhval, rhval);
            if ("ge".equals(operator))
                return gt(lhval, rhval) || eq(lhval, rhval);
            if ("le".equals(operator))
                return lt(lhval, rhval) || eq(lhval, rhval);
            return false;
        }
        private String getVal(String t) {
            if (t.startsWith("'")) return cleanup(t);
            t = cleanup(t);
            if (incrVariables.contains(t)) containsIncrementedVar = true;
            return getString(cleanup(t));
        }
        private boolean eq(String l, String r) {
            return ((l == null && r == null) || (l != null && l.equals(r))); }
        private boolean lt(String l, String r) {
            return (l != null && r != null && l.compareTo(r) < 0); }
        private boolean gt(String l, String r) {
            return (l != null && r != null && l.compareTo(r) > 0); }
        private boolean matches(String l, String r) {
            if (l == null || r == null) return false;
            boolean result = false;
            Perl5Util perl = null;
            try {
                String re = "m\n" + r + "\n";
                perl = PerlPool.get();
                result = perl.match(re, l);
            } catch (Exception e) {
            } finally {
                PerlPool.release(perl);
            }
            return result;
        }
    }
    private RelationalExpression parseRelationalExpression(String expr) {
        if (expr == null) return null;
        expr = expr.replace('\n', ' ').replace('\t', ' ');
        int pos = expr.indexOf(" eq ");
        if (pos == -1) pos = expr.indexOf(" ne ");
        if (pos == -1) pos = expr.indexOf(" =~ ");
        if (pos == -1) pos = expr.indexOf(" !~ ");
        if (pos == -1) pos = expr.indexOf(" lt ");
        if (pos == -1) pos = expr.indexOf(" le ");
        if (pos == -1) pos = expr.indexOf(" gt ");
        if (pos == -1) pos = expr.indexOf(" ge ");
        if (pos == -1) return null;

        RelationalExpression result = new RelationalExpression();
        result.lhs = expr.substring(0, pos).trim();
        result.operator = expr.substring(pos+1, pos+3);
        result.rhs = expr.substring(pos+4).trim();
        return result;
    }

    private HashSet incrVariables = new HashSet();
    private void processIncrDirective(DirectiveMatch incrDir) {
        String varName = cleanup(incrDir.contents);
        int numberValue = 0;

        Object param = params.get(varName);
        if (param instanceof String) try {
            numberValue = Integer.parseInt((String) param) + 1;
        } catch (NumberFormatException nfe) {}
        params.put(varName, Integer.toString(numberValue));
        incrVariables.add(varName);

        incrDir.replace("");
    }

    /** process a break directive within the buffer */
    private void processBreakDirective(DirectiveMatch breakDir) {
        String label = cleanup(breakDir.contents);
        DirectiveMatch breakEnd = new DirectiveMatch
            (breakDir.buf, "endbreak "+label, breakDir.end, true);

        if (!breakEnd.matches()) {
            // if the endbreak is missing, delete this directive and abort.
            System.err.println
                ("break directive without matching endbreak - aborting.");
            breakDir.replace("");
            return;
        }
        breakDir.buf.replace(breakDir.end, breakEnd.end, "");
        breakDir.replace("");
    }

    /** search for blocks created by matching start and end directives, and
     * give them unique numerical prefixes so it will be easy to figure out
     * which start directive goes with which end directive.  This handles
     * nested blocks correctly.
     */
    private void numberBlocks(StringBuffer text,
                              String blockStart, String blockFinish,
                              String blockMid1, String blockMid2) {
        DirectiveMatch start, finish, mid;
        int blockNum = 0;
        String prefix;

        while ((finish = new DirectiveMatch(text, blockFinish)).matches()) {
            start = new DirectiveMatch(text, blockStart, finish.begin, false);
            if (!start.matches()) break;
            prefix = "0" + blockNum++;

            finish.rename(prefix + blockFinish);

            int end = finish.begin;
            if (!isNull(blockMid1))
                end = renameDirectives(text, start.end, end,
                                       blockMid1, prefix + blockMid1);
            if (!isNull(blockMid2))
                end = renameDirectives(text, start.end, end,
                                       blockMid2, prefix + blockMid2);

            start.rename(prefix + blockStart);
        }
    }

    /** Find all directives with the given name in text, starting at
     * position <code>from</code> and going to position <code>to</code>,
     * and rename them to newname.
     */
    private int renameDirectives(StringBuffer text, int from, int to,
                                 String name, String newName) {
        DirectiveMatch dir;
        int delta = newName.length() - name.length();

        while ((dir = new DirectiveMatch(text, name, from, true)).matches() &&
               dir.begin < to) {
            dir.rename(newName);
            from = dir.end;
            to += delta;
        }
        return to;
    }


    /** trim whitespace and unimportant delimiters from t */
    private static String cleanup(String t) {
        t = t.trim();
        if (t.length() == 0) return t;
        if (t.charAt(0) == '"' || t.charAt(0) == '\'') {
            int endPos = t.indexOf(t.charAt(0), 1);
            if (endPos != -1) t = t.substring(1, endPos);
        }
        return t;
    }


    /** Lookup the named list and return it.
     *
     * if the name is enclosed in braces [] it refers to a data
     * element - otherwise it refers to a cgi environment variable or
     * a query/post parameter.
     *
     * If no list is found by that name, will return an empty list.
     */
    private ListData getList(String listName) {
        if (listName.startsWith("[")) {
            // listName names a data element
            listName = trimDelim(listName);
            SimpleData d = getSimpleValue(listName);
            if (d instanceof ListData)   return (ListData) d;
            if (d instanceof StringData) return ((StringData) d).asList();
            if (d instanceof SimpleData) {
                ListData result = new ListData();
                result.add(d.format());
            }
            return EMPTY_LIST;
        } else {
            // listName names an environment variable or parameter
            ListData result = new ListData();

                                // try for an environment variable first.
            Object envVal = env.get(listName);
            if (envVal instanceof String) {
                result.add((String) envVal);
                return result;
            }
                                // look for a parameter value.
            String[] param = (String[]) params.get(listName + "_ALL");
            if (param != null)
                for (int i = 0;   i < param.length;   i++)
                    result.add(param[i]);
            else if (params.get(listName) instanceof String)
                result.add((String) params.get(listName));

            return result;
        }
    }
    private static ListData EMPTY_LIST = new ListData();


    /** Lookup the named string and return it.
     *
     * if the name is enclosed in braces [] it refers to a data
     * element - otherwise it refers to a cgi environment variable or
     * a query/post parameter.
     *
     * If no string is found by that name, will return an empty string.
     */
    private String getString(String name) {
        if (name.startsWith("[")) {
            // listName names a data element
            name = trimDelim(name);
            SimpleData d = getSimpleValue(name);
            return (d == null ? "" : d.format());
        } else {
                                // try for an environment variable first.
            Object result = env.get(name);
            if (result instanceof String) return (String) result;

                                // look for a parameter value.
            result = params.get(name);
            if (result instanceof String) return (String) result;
            if (result != null) return result.toString();

            return "";
        }
    }

    /** lookup a named value in the data repository. */
    private SimpleData getSimpleValue(String name) {
        if (data == null) return null;
        return data.getSimpleValue(data.createDataName(prefix, name));
    }

    /** trim the first and last character from a string */
    private String trimDelim(String str) {
        return str.substring(1, str.length() - 1);
    }

    /** look up a named data element and perform a test() on it. */
    private boolean testDataElem(String name) {
        if (name.startsWith("[")) {
            // listName names a data element
            name = trimDelim(name);
            SimpleData d = getSimpleValue(name);
            return (d == null ? false : d.test());
        }
        return false;
    }

    /** Class which can locate and parse directives of the form <!--#foo -->
     */
    private class DirectiveMatch {
        StringBuffer buf;
        public int begin, end;
        public String directive, contents;
        private Map attributes = null;

        /** Find the first occurrence of the named directive in buf. */
        public DirectiveMatch(StringBuffer buf, String directive) {
            this(buf, directive, 0, true); }

        /** Find a directive in buf */
        public DirectiveMatch(StringBuffer buf, String directive,
                              int pos, boolean after) {
            this.buf = buf;
            this.directive = directive;
            String dirStart = DIRECTIVE_START + directive;
            if (after)
                begin = StringUtils.indexOf(buf, dirStart, pos);
            else
                begin = StringUtils.lastIndexOf(buf, dirStart, pos);

            if (begin == -1) return;

            end = StringUtils.indexOf(buf, DIRECTIVE_END, begin);
            if (end == -1) {
                begin = -1;
                return;
            }
            contents = buf.substring(begin + dirStart.length(), end);
            end += DIRECTIVE_END.length();

            if (contents.endsWith("#")) {
                contents = contents.substring(0, contents.length()-1);
                while (end < buf.length() &&
                       Character.isWhitespace(buf.charAt(end)))
                    end++;
            }

            if (directive.length() == 0) {
                StringTokenizer tok = new StringTokenizer(contents);
                if (tok.hasMoreTokens())
                    this.directive = tok.nextToken();
                if (tok.hasMoreTokens())
                    contents = tok.nextToken("\u0000");
                else
                    contents = "";
            }
        }

        /** @return true if a directive was found */
        public boolean matches() { return begin != -1; }

        /** replace the directive found with the given text. */
        public void replace(String text) {
            buf.replace(begin, end, text);
            end = begin + text.length();
        }

        /** parse the inner contents of the directive as a set of
         * attrName=attrValue pairs, and return the value associated
         * with the named attribute. returns null if the attribute was
         * not present. */
        public String getAttribute(String attrName) {
            if (attributes == null)
                attributes = parseAttributes();
            return (String) attributes.get(attrName);
        }

        /** parse the inner contents of the directive as a set of
         * attrName=attrValue pairs */
        private Map parseAttributes() {
            return HTMLUtils.parseAttributes(contents);
        }

        public void rename(String newName) {
            int from = begin + DIRECTIVE_START.length();
            int to   = from + directive.length();
            buf.replace(from, to, newName);
            end = end + newName.length() - directive.length();
            directive = newName;
        }
    }


    private static class QueryParser extends TinyCGIBase {
        protected boolean supportQueryFiles() { return false; }
    }

    /** @return true if t is null or the empty string */
    private boolean isNull(String t) {
        return (t == null || t.length() == 0);
    }
    private int whitespacePos(String t) {
        int result = t.indexOf(' ');
        if (result == -1) result = t.indexOf('\t');
        if (result == -1) result = t.indexOf('\r');
        if (result == -1) result = t.indexOf('\n');
        return result;
    }

    private boolean blockMatch(String name, String directive) {
        return (directive != null && directive.endsWith(name));
    }
    private String blockNum(String name, String directive) {
        return directive.substring(0, directive.length() - name.length());
    }

    private static final String DIRECTIVE_START = "<!--#";
    private static final String DIRECTIVE_END   = "-->";
}
