package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "calendar_tasks")
@Getter
@Setter
@NoArgsConstructor
public class CalendarTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private FarmProject project;
    @ManyToOne(optional = false)
    private CropPlanItem cropPlanItem;
    @Enumerated(EnumType.STRING)
    private CalendarStage stage;
    private LocalDate plannedStart;
    private LocalDate plannedEnd;
    private LocalDate actualDate;
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    @Column(length = 1000)
    private String note;
}
