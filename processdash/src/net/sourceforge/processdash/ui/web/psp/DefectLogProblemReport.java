// Copyright (C) 2009 Tuma Solutions, LLC
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

package net.sourceforge.processdash.ui.web.psp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.sourceforge.processdash.log.defects.Defect;

public class DefectLogProblemReport extends PspForEngDefectBase {

    private List defectsWithNoFixTime;

    private List defectsWithNoDescription;

    private List failureDefectsWithNoFixNumber;

    private List samePhaseDefects;

    private List misorderedDefects;

    @Override
    protected void writeContents() throws IOException {
        defectsWithNoFixTime = new ArrayList();
        defectsWithNoDescription = new ArrayList();
        failureDefectsWithNoFixNumber = new ArrayList();
        samePhaseDefects = new ArrayList();
        misorderedDefects = new ArrayList();

        runDefectAnalysis();

        out.println(HEADER);

        boolean foundMissingData = false;

        if (!defectsWithNoFixTime.isEmpty()) {
            printStudentDataError("You did not capture the fix time for "
                    + defects(defectsWithNoFixTime) + ".");
            foundMissingData = true;
        }

        if (!defectsWithNoDescription.isEmpty()) {
            printStudentDataError("You did not enter a description for "
                    + defects(defectsWithNoDescription) + ".");
            foundMissingData = true;
        }

        if (!failureDefectsWithNoFixNumber.isEmpty()) {
            printStudentDataError("Were you fixing "
                    + plural(failureDefectsWithNoFixNumber, "another defect",
                        "other defects") + " when you injected "
                    + defects(failureDefectsWithNoFixNumber)
                    + "? If so, you should record the number of the "
                    + "original defect in the 'Fix Defect' field.");
            foundMissingData = true;
        }

        if (!foundMissingData)
            printStudentDataOK("All required data was entered for each defect.");

        boolean foundOrderingProblem = false;

        if (!misorderedDefects.isEmpty()) {
            printStudentDataError("It is impossible to remove a defect before "
                    + "you inject it. Please recheck the injection and "
                    + "removal phases for " + defects(misorderedDefects) + ".");
            foundOrderingProblem = true;
        }

        if (!samePhaseDefects.isEmpty()) {
            printStudentDataError("PSP does not require you to record defects "
                    + "that were found and removed all in the same phase "
                    + "(such as " + defects(samePhaseDefects) + ").");
            foundOrderingProblem = true;
        }

        if (!foundOrderingProblem)
            printStudentDataOK("All defects were injected before they were removed.");

        out.print(FOOTER);
    }


    private String defects(List l) {
        return plural(l, "defect ", "defects ") + joinList(l, "and");
    }

    public void analyze(String path, Defect d) {
        if (fixTime(d) <= 0) {
            defectsWithNoFixTime.add(d.number);
        }

        if (!hasValue(d.description)) {
            defectsWithNoDescription.add(d.number);
        }

        if (!hasValue(d.fix_defect)
                && FAILURE_PHASES.contains(d.phase_injected)) {
            failureDefectsWithNoFixNumber.add(d.number);
        }

        int injPhasePos = PHASES.indexOf(d.phase_injected);
        int remPhasePos = PHASES.indexOf(d.phase_removed);
        int diff = remPhasePos - injPhasePos;
        if (diff < 0)
            misorderedDefects.add(d.number);
        else if (diff == 0)
            samePhaseDefects.add(d.number);
    }

    private static final List PHASES = Collections.unmodifiableList(Arrays
            .asList("Before Development", "Planning", "Design",
                "Design Review", "Code", "Code Review", "Compile", "Test",
                "Postmortem", "After Development"));

    private static final List FAILURE_PHASES = Collections
            .unmodifiableList(Arrays.asList("Compile", "Test"));

}
