package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Application application;
    @ManyToOne
    private ProgressReport report;
    /** Photo (ordered by sortOrder, first = cover), demining certificate or other file of a plot listing. */
    @ManyToOne
    private Plot plot;
    private int sortOrder;
    @Enumerated(EnumType.STRING)
    private DocType type;
    private String originalName;
    private String storedName;
    private long size;
    private LocalDateTime uploadedAt;
}
