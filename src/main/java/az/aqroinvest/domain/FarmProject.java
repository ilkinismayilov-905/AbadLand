package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "farm_projects")
@Getter
@Setter
@NoArgsConstructor
public class FarmProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private Application application;
    @ManyToOne(optional = false)
    private Plot plot;
    @ManyToOne(optional = false)
    private User investor;
    @Enumerated(EnumType.STRING)
    private FarmStatus status;
    private LocalDate startDate;
    private long committedInvestment;
    private int committedJobs;
    private int committedMonths;
}
