package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "plot_climates")
@Getter
@Setter
@NoArgsConstructor
public class PlotClimate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private Plot plot;
    private Integer elevationM;
    private Double meanTempC;
    private Double absMinTempC;
    private Integer frostDays;
    private Double precipMm;
    private LocalDateTime fetchedAt;
}
