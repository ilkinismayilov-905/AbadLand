package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "share_holdings")
@Getter
@Setter
@NoArgsConstructor
public class ShareHolding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private ShareOffering offering;
    @ManyToOne(optional = false)
    private User investor;
    private int shares;
    private LocalDateTime createdAt;
}
