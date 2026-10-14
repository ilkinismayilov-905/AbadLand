package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "crop_calendar_templates")
@Getter
@Setter
@NoArgsConstructor
public class CropCalendarTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Crop crop;
    @Enumerated(EnumType.STRING)
    private CalendarStage stage;
    private int startMonth;
    private int endMonth;
    /** 0 = planting year; HARVEST uses firstHarvestYear - 1 */
    private int yearOffset;
}
