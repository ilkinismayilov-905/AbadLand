package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "workforce_items")
@Getter
@Setter
@NoArgsConstructor
public class WorkforceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private FarmProject project;
    @Enumerated(EnumType.STRING)
    private WorkRole role;
    private int count;
    @Column(name = "is_local")
    private boolean local;
}
