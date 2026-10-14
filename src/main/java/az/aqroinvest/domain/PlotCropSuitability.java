package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "plot_crop_suitability")
@Getter
@Setter
@NoArgsConstructor
public class PlotCropSuitability {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Plot plot;
    @ManyToOne(optional = false)
    private Crop crop;
    private int score;
    @Enumerated(EnumType.STRING)
    private SuitabilitySource source;
    @Column(length = 1000)
    private String reason;
}
