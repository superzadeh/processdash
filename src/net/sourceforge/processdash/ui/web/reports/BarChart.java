// Copyright (C) 2001-2008 Tuma Solutions, LLC
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

package net.sourceforge.processdash.ui.web.reports;


import net.sourceforge.processdash.ui.web.CGIChartBase;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;



public class BarChart extends CGIChartBase {

    /** Create a bar chart. */
    public JFreeChart createChart() {
        //if (data.numCols() == 1) data = data.transpose();
        JFreeChart chart;
        boolean vertical = true; // default
        String direction = getParameter("dir");
        if ((direction != null && direction.toLowerCase().startsWith("hor"))
            || parameters.get("horizontal") != null)
            vertical = false;
        chart = ChartFactory.createBarChart3D
            (null, null, null, data.catDataSource(),
             (vertical ? PlotOrientation.VERTICAL : PlotOrientation.HORIZONTAL),
             true, true, false);

        setupCategoryChart(chart);

        return chart;
    }

}
