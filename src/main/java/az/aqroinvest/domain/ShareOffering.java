package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "share_offerings")
@Getter
@Setter
@NoArgsConstructor
public class ShareOffering {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private FarmProject project;
    @ManyToOne(optional = false)
    private User leadInvestor;
    private int totalShares;
    private long pricePerShare;
    private int leadShares;
    private LocalDate deadline;
    @Enumerated(EnumType.STRING)
    private OfferingStatus status;
    private LocalDateTime createdAt;
}
