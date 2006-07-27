// Copyright (C) 2006 Tuma Solutions, LLC
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

package net.sourceforge.processdash.net.cms;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.sourceforge.processdash.data.DataContext;
import net.sourceforge.processdash.i18n.Resources;
import net.sourceforge.processdash.util.HTMLUtils;

/** Abstract base class for renderers that generate a single page of content.
 */
public abstract class AbstractSinglePageAssembler implements PageAssembler,
        Needs.Environment, Needs.Parameters, Needs.Prefix, Needs.Data {

    protected static final Resources resources = Resources
            .getDashBundle("CMS.Snippet");

    // collect information, via the Needs interfaces.

    protected Map environment;

    protected Map parameters;

    protected String prefix;

    protected DataContext dataContext;

    public void setData(DataContext dataContext) {
        this.dataContext = dataContext;
    }

    public void setEnvironment(Map environment) {
        this.environment = environment;
    }

    public void setParameters(Map parameters) {
        this.parameters = parameters;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    // methods that subclasses can override.

    protected abstract void writePage(Writer out, Set headerItems,
            PageContentTO page) throws IOException;

    private boolean shouldClobberForms() {
        return false;
    }

    protected void setSnippetNamespace(SnippetInstanceTO snippet,
            String namespace) {
        snippet.setNamespace(namespace);
    }

    protected void addPageSpecificHeaderItems(Set headerItems) {
        addStyleSheet(headerItems, "/style.css");
    }


    /**  Handle the default tasks associated with rendering a page.
     */
    public void service(Writer out, PageContentTO page) throws IOException {

        SnippetInvoker invoker = new SnippetInvoker(environment, parameters,
                prefix, dataContext);
        String selfURI = CmsContentDispatcher.getSimpleSelfUri(environment,
                false);
        Set headerItems = new LinkedHashSet();
        addPageSpecificHeaderItems(headerItems);

        int num = 0;
        for (Iterator i = page.getContentSnippets().iterator(); i.hasNext();) {
            SnippetInstanceTO snip = (SnippetInstanceTO) i.next();
            setSnippetNamespace(snip, "snip" + (num++) + "_");

            try {
                String snipContent = invoker.invoke(snip);
                if (snipContent != null) {
                    SnippetHtmlPostprocessor post = new SnippetHtmlPostprocessor(snip
                            .getNamespace(), selfURI, snip.getUri(),
                            shouldClobberForms(), snipContent);
                    headerItems.addAll(post.getHeaderItems());
                    snip.setGeneratedContent(post.getResults());
                } else {
                    snip.setGeneratedContent(null);
                }
            } catch (IOException ioe) {
                snip.setGeneratedContent(null);
            }
        }

        writePage(out, headerItems, page);
    }

    protected void addStyleSheet(Set headerItems, String uri) {
        String item = "<link href=\"" + uri
                + "\" rel=\"stylesheet\" type=\"text/css\">";
        headerItems.add(item);
    }

    protected void addScript(Set headerItems, String uri) {
        String item = "<script src=\"" + uri
                + "\" type=\"text/javascript\"></script>";
        headerItems.add(item);
    }

    /** Convience routine */
    protected static String esc(String s) {
        return HTMLUtils.escapeEntities(s);
    }

}