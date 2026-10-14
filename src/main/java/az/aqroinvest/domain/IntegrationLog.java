package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "integration_logs")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String applicationNumber;
    private String operation;
    @Lob
    private String requestJson;
    @Lob
    private String responseJson;
    @Enumerated(EnumType.STRING)
    private IntegrationStatus status;
    private String ektisRef;
    private long durationMs;
    private LocalDateTime at;
}
