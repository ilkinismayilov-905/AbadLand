package az.aqroinvest.farm;

import az.aqroinvest.domain.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FarmProgressTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    static FarmProject farm(LocalDate start, long inv, int jobs, int months) {
        FarmProject f = new FarmProject();
        f.setStartDate(start);
        f.setCommittedInvestment(inv);
        f.setCommittedJobs(jobs);
        f.setCommittedMonths(months);
        return f;
    }

    static WorkforceItem crew(int n, boolean local) {
        WorkforceItem w = new WorkforceItem();
        w.setCount(n);
        w.setLocal(local);
        return w;
    }

    static ProgressReport report(long invested, LocalDate at) {
        ProgressReport r = new ProgressReport();
        r.setInvestedToDate(invested);
        r.setSubmittedAt(at.atStartOfDay());
        return r;
    }

    static CalendarTask task(LocalDate end, LocalDate actual) {
        CalendarTask t = new CalendarTask();
        t.setPlannedStart(end.withDayOfMonth(1));
        t.setPlannedEnd(end);
        t.setActualDate(actual);
        return t;
    }

    @Test
    void onTrackProjectIsActiveWithPercentages() {
        FarmProject f = farm(TODAY.minusMonths(8), 264_000, 12, 36);
        var p = FarmService.progress(f, List.of(crew(10, true), crew(2, false)), List.of(report(175_000, TODAY.minusDays(11))),
                List.of(task(TODAY.plusMonths(2), null)), TODAY);
        assertThat(p.investmentPct()).isEqualTo(66);
        assertThat(p.jobsPct()).isEqualTo(100);
        assertThat(p.localPct()).isEqualTo(83);
        assertThat(p.timePct()).isEqualTo(22);
        assertThat(p.risk()).isEqualTo(FarmStatus.ACTIVE);
        assertThat(p.delayedTasks()).isZero();
    }

    @Test
    void laggingInvestmentOldReportAndLongDelayMeanRisk() {
        FarmProject f = farm(TODAY.minusMonths(14), 108_000, 25, 24);
        var p = FarmService.progress(f, List.of(crew(5, true)), List.of(report(20_000, TODAY.minusDays(120))),
                List.of(task(TODAY.minusDays(40), null), task(TODAY.minusDays(60), TODAY.minusDays(70))), TODAY);
        assertThat(p.timePct()).isGreaterThanOrEqualTo(25);
        assertThat(p.risk()).isEqualTo(FarmStatus.AT_RISK);
        assertThat(p.riskReasons()).hasSize(4); // investment, jobs, no report 100 days, task delayed > 30 days
        assertThat(p.delayedTasks()).isEqualTo(1); // the done one does not count
    }

    @Test
    void newProjectWithoutReportsIsNotAtRisk() {
        var p = FarmService.progress(farm(TODAY, 410_000, 24, 36), List.of(), List.of(), List.of(), TODAY);
        assertThat(p.risk()).isEqualTo(FarmStatus.ACTIVE);
        assertThat(p.jobsPct()).isZero();
        assertThat(p.localPct()).isZero();
    }

    @Test
    void taskDelayedOnlyAfter14DaysGrace() {
        assertThat(CalendarService.statusOf(task(TODAY.minusDays(14), null), TODAY)).isEqualTo(TaskStatus.PLANNED);
        assertThat(CalendarService.statusOf(task(TODAY.minusDays(15), null), TODAY)).isEqualTo(TaskStatus.DELAYED);
        assertThat(CalendarService.statusOf(task(TODAY.minusDays(15), TODAY), TODAY)).isEqualTo(TaskStatus.DONE);
    }
}
