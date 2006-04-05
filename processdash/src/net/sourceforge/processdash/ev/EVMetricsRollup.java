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

package net.sourceforge.processdash.ev;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.sourceforge.processdash.ev.ci.ConfidenceInterval;
import net.sourceforge.processdash.ev.ci.TargetedConfidenceInterval;


public class EVMetricsRollup extends EVMetrics {

    /** The date the project is forecast to complete */
    Date independentForecastDate = null;

    /** The forecast total cost of the project */
    double independentForecastCost;

    /** A confidence interval predicting the optimized forecast
     * completion date. */
    protected ConfidenceInterval optCompletionDateInterval = null;

    /** A flag indicating whether this metrics object is rolling up
     * other EVMetricsRollup objects. */
    protected boolean isRollupOfRollups = false;



    public EVMetricsRollup() {
        super();
    }

    public EVMetricsRollup(boolean supportFormatting) {
        super(supportFormatting);
    }

    public void reset(Date effectiveDate) {
        totalPlanTime = earnedValueTime = actualTime = planTime = 0.0;
        totalSchedulePlanTime = totalScheduleActualTime = 0.0;
        independentForecastCost = 0;
        currentDate = effectiveDate;
        startDate = independentForecastDate = null;
        errors = null;
        isRollupOfRollups = false;
    }

    public void addMetrics(EVMetrics that) {
        this.totalPlanTime   += that.totalPlanTime;
        this.earnedValueTime += that.earnedValueTime;
        this.actualTime      += that.actualTime;
        this.planTime        += that.planTime;
        this.totalSchedulePlanTime += that.totalSchedulePlanTime;
        this.totalScheduleActualTime += that.totalScheduleActualTime;
        this.independentForecastCost += that.independentForecastCost();
        this.startDate =
            EVScheduleRollup.minDate(this.startDate, that.startDate);
        this.independentForecastDate =
            EVScheduleRollup.maxDate(this.independentForecastDate,
                                     that.independentForecastDate());

        if (that instanceof EVMetricsRollup)
            this.isRollupOfRollups = true;

        if (that.errors != null) {
            if (this.errors == null)
                this.errors = new TreeMap();
            String qualifier = that.errorQualifier;
            if (qualifier == null || qualifier.length() == 0)
                this.errors.putAll(that.errors);
            else {
                Iterator i = that.errors.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry e = (Map.Entry) i.next();
                    this.errors.put(qualifier + e.getKey(),
                                    e.getValue());
                }
            }
        }
    }
    public void recalcComplete(EVSchedule s) {
        recalcOptimizedPlanDate(s);
        super.recalcComplete(s);
    }
    protected void recalcViability(EVSchedule s) {
        super.recalcViability(s);
        if (costInterval == null)
            optCompletionDateInterval = null;

        else {
            if (optCompletionDateInterval instanceof TargetedConfidenceInterval)
                retargetViability
                    (s, (TargetedConfidenceInterval)optCompletionDateInterval,
                     optimizedForecastDate());
            if (unviable(optCompletionDateInterval)) {
                // System.out.println("opt date interval is not viable");
                optCompletionDateInterval = null;
            }
        }
    }
    protected void recalcScheduleTime(EVSchedule s) {
        // do nothing - it is already calculated.
    }
    protected void recalcOptimizedPlanDate(EVSchedule s) {
        if (isRollupOfRollups) {
            optimizedPlanDate = null;
        } else {
            optimizedPlanDate = s.getHypotheticalDate(totalPlan(), false);
            if (optimizedPlanDate == EVSchedule.NEVER)
                optimizedPlanDate = null;
        }
    }

    public void setOptimizedDateConfidenceInterval
        (ConfidenceInterval optCompletionDate)
    {
        optCompletionDateInterval = optCompletionDate;
    }


    public double independentForecastCost() {
        return independentForecastCost;
    }


    public double independentForecastDuration() {
        return calcDuration(startDate(), independentForecastDate());
    }
    public Date independentForecastDate() {
        return independentForecastDate;
    }

    public double optimizedForecastDuration() {
        if (isRollupOfRollups)
            return Double.NaN;
        else if (useSimpleForecastDateFormula)
            return super.independentForecastDuration();
        else
            return calcDuration(startDate(), super.independentForecastDate());
    }

//    private Date optimizedForecastDate = null;
    public Date optimizedForecastDate() {
        if (isRollupOfRollups) {
            return null;
        } else if (useSimpleForecastDateFormula) {
            // use the extrapolation algorithm that EVMetrics uses to
            // calculate the independentForecastDate.
            Date s;
            if ((s = startDate()) == null) return null;
            double duration = optimizedForecastDuration();
            if (badDouble(duration)) return null;
            return new Date(s.getTime() + (long) (duration * MINUTE_MILLIS));
        } else
            return super.independentForecastDate();
    }
    public Date optimizedForecastDateLPI() {
        if (optCompletionDateInterval == null)
            return null;
        else
            return convertToDate(optCompletionDateInterval.getLPI(0.70));
    }
    public Date optimizedForecastDateUPI() {
        if (optCompletionDateInterval == null)
            return null;
        else
            return convertToDate(optCompletionDateInterval.getUPI(0.70));
    }


    public Date optimizedPlanDate() {
        return optimizedPlanDate;
    }
    private Date optimizedPlanDate = null;
    void setOptimizedPlanDate(Date d) { optimizedPlanDate = d; }

    public boolean isRollupOfRollups() {
        return isRollupOfRollups;
    }

    protected String getResourcePrefix() { return "Rollup_"; }

    protected List buildFormatters() {
        List result = super.buildFormatters();
        result.add(new DurationMetricFormatter
            ("Optimized_Forecast_Duration") {
                double val() { return optimizedForecastDuration(); } } );
        result.add(new DateMetricFormatter("Optimized_Forecast_Date") {
                Date val() { return optimizedForecastDate(); } } );
        result.add(new DateRangeMetricFormatter
            ("Optimized_Forecast_Date_Range") {
                Date lpi() { return optimizedForecastDateLPI(); }
                Date upi() { return optimizedForecastDateUPI(); } } );
        result.add(new DateMetricFormatter("Optimized_Plan_Date") {
                Date val() { return optimizedPlanDate(); } } );
        return result;
    }

}