package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "progress_reports")
@Getter
@Setter
@NoArgsConstructor
public class ProgressReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private FarmProject project;
    /** e.g. "2026-Q4" */
    @Column(name = "period_label")
    private String period;
    private long investedToDate;
    @Column(length = 2000)
    private String note;
    @Enumerated(EnumType.STRING)
    private ReportStatus status;
    @Column(length = 1000)
    private String reviewerComment;
    private LocalDateTime submittedAt;
}
