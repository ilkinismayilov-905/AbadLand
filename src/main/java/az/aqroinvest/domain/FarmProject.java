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

    /** Competition route; null for a lease won at auction. */
    @OneToOne
    private Application application;
    /** Auction route (LEASE won at an open auction). */
    @ManyToOne
    private Auction auction;
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

    /** "QAR-2026-000007" or "HRC-2026-00" — where the project came from. */
    public String sourceLabel() {
        return application != null ? application.getNumber() : auction != null ? auction.getCode() : "—";
    }
}
