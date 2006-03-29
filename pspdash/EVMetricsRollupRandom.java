// PSP Dashboard - Data Automation Tool for PSP-like processes
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
// OO-ALC/TISHD
// Attn: PSP Dashboard Group
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC:  processdash-devel@lists.sourceforge.net

package pspdash;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class EVMetricsRollupRandom extends EVMetricsRollup {

    EVMetrics[] metrics;

    public EVMetricsRollupRandom(EVScheduleRollup rollupSched) {
        super(false);
        metrics = new EVMetrics[rollupSched.subSchedules.size()];
        actualTime = 0;
        for (int i = 0;   i < metrics.length;   i++) {
            EVSchedule s = (EVSchedule) rollupSched.subSchedules.get(i);
            metrics[i] = s.getMetrics();
            actualTime += metrics[i].actualTime;
        }
    }

    public double independentForecastCost() {
        double result = 0;
        for (int i = 0;   i < metrics.length;   i++)
            result += metrics[i].independentForecastCost();

        return result;
    }

    public Date independentForecastDate() {
        Date result = null;
        for (int i = 0;   i < metrics.length;   i++)
            result = EVScheduleRollup.maxDate
                (result, metrics[i].independentForecastDate());

        return result;
    }

}