package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "competitions")
@Getter
@Setter
@NoArgsConstructor
public class Competition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String code;
    private String title;
    @Column(length = 3000)
    private String requirements;
    private long minInvestment;
    private LocalDate deadline;
    @Enumerated(EnumType.STRING)
    private CompetitionStatus status;
    @ManyToOne
    private User createdBy;
    @ManyToOne
    private User approvedBy;
    @Column(length = 1000)
    private String comment;
    @ManyToMany
    @JoinTable(name = "competition_plots")
    private Set<Plot> plots = new LinkedHashSet<>();
}
