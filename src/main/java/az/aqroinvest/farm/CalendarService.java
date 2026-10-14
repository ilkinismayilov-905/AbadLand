package az.aqroinvest.farm;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.CalendarTaskRepository;
import az.aqroinvest.repo.CropCalendarTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CropCalendarTemplateRepository templates;
    private final CalendarTaskRepository tasks;

    /** Creates CalendarTasks for a crop plan item from its crop's templates (seasonYear + yearOffset). */
    public List<CalendarTask> generate(CropPlanItem item) {
        List<CalendarTask> created = templates.findByCrop(item.getCrop()).stream().map(t -> {
            int year = item.getSeasonYear() + t.getYearOffset();
            CalendarTask task = new CalendarTask();
            task.setProject(item.getProject());
            task.setCropPlanItem(item);
            task.setStage(t.getStage());
            task.setPlannedStart(LocalDate.of(year, t.getStartMonth(), 1));
            task.setPlannedEnd(YearMonth.of(year, t.getEndMonth()).atEndOfMonth());
            task.setStatus(TaskStatus.PLANNED);
            return task;
        }).toList();
        return tasks.saveAll(created);
    }

    /** Status is computed on read: DONE if actual date set, DELAYED if 14+ days past planned end. */
    public static TaskStatus statusOf(CalendarTask t, LocalDate today) {
        if (t.getActualDate() != null) return TaskStatus.DONE;
        return today.isAfter(t.getPlannedEnd().plusDays(14)) ? TaskStatus.DELAYED : TaskStatus.PLANNED;
    }
}
