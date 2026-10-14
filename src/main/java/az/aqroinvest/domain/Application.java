package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String number;
    @ManyToOne(optional = false)
    private User investor;
    @ManyToOne(optional = false)
    private Competition competition;
    @ManyToOne(optional = false)
    private Plot plot;
    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;
    private String plannedCrop;
    private long investmentAzn;
    private int jobs;
    private int months;
    @Column(length = 3000)
    private String businessPlan;
    @Enumerated(EnumType.STRING)
    private Decision decision;
    @Column(length = 1000)
    private String decisionReason;
    @Column(length = 2000)
    private String infoRequest;
    @Column(length = 2000)
    private String infoResponse;
    private String ektisRef;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
