package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "status_history")
@Getter
@Setter
@NoArgsConstructor
public class StatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Application application;
    @Enumerated(EnumType.STRING)
    private ApplicationStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private ApplicationStatus toStatus;
    @Column(length = 2000)
    private String comment;
    private String actorName;
    @Enumerated(EnumType.STRING)
    private Role actorRole;
    private LocalDateTime at;
}
