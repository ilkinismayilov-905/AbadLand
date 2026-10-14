package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;
    private String passwordHash;
    private String fullName;
    private String phone;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Enumerated(EnumType.STRING)
    private InvestorType investorType;
    private String fin;
    private String voen;
    private String companyName;
    private Integer experienceYears;
    /** false = deactivated by admin: cannot log in, open sessions are expired. */
    private boolean active = true;
    private java.time.LocalDateTime lastLoginAt;
}
