# CLAUDE.md — AqroInvest Qarabağ (hackathon MVP)

Web portal for agricultural investment in Azerbaijan's liberated territories (Qarabağ, Şərqi Zəngəzur). Investors find plots (map, catalog, fruit suitability, matching quiz), evaluate them (passport, ROI + prediction, satellite + AI analysis), apply to competitions and track status. Operators review, the commission approves, decisions go to a mocked EKTİS (Ministry system). After approval the investor manages the farm (crop plan, planting calendar, workforce, reports), and can optionally open a co-investment share offering (simulation).

**Time budget is the #1 constraint (core ≈ 7 h).** Choose the simplest thing that works.

## Rules
1. Work step by step (`steps/STEP-N-*.md`). Only the current step. Short plan (≤ 15 lines), then implement; ask only if truly ambiguous.
2. One Spring Boot app serves backend and HTML pages (server-side rendering). **No SPA, no Node/npm, no Docker required.**
3. All UI text in **Azerbaijani** (ə, ı, ö, ü, ğ, ş, ç). Code and comments in English.
4. The demo path in `docs/SPEC.md` must always work.
5. After each step: `./mvnw spring-boot:run` starts with no errors; update `PROGRESS.md` (3–5 lines); suggest a commit message.
6. All seed data is fictional. ROI, prediction and crop parameters are labelled "Sınaq dəyəri". AI output is labelled "AI tövsiyəsidir".
7. `docs/SPEC.md` is the source of truth for enums, rules, formulas and URLs. If you must deviate, update SPEC in the same change.

## Stack (fixed)
- Java 21, Spring Boot 3.x, Maven wrapper.
- Starters: web, thymeleaf, security, data-jpa, validation; plus `thymeleaf-extras-springsecurity6`, `h2`, `postgresql` (profile `pg`), `springdoc-openapi-starter-webmvc-ui`, Lombok, Jackson.
- DB: **H2 in-memory** default (fresh seed every start = demo reset), `ddl-auto=create`, no Flyway.
- UI: Thymeleaf + **Tailwind CDN** (`https://cdn.tailwindcss.com`) + **Leaflet CDN** (unpkg). One `layout.html` fragment. Small inline JS only for maps. Charts = CSS bars (no chart library).
- Auth: Spring Security form login + session, BCrypt, CSRF on.
- Outbound HTTP (`java.net.http.HttpClient`, 10 s timeouts): Esri World Imagery, Open-Meteo, Anthropic API only.

## Code layout
```
src/main/java/az/aqroinvest/
  domain/        entities + enums
  repo/          Spring Data repositories
  service/       PlotService, SuitabilityService, MatchingService, RoiService (incl. prediction),
                 ApplicationService (state machine), CompetitionService, NotificationService,
                 AuditService, DashboardService
  ai/            AiClient, ClaudeAiClient, AiAnalysisService, SatelliteImageService, ClimateService
  farm/          FarmService, CalendarService, ShareService
  integration/   EktisClient, MockEktisClient, HttpEktisClient (stub), EktisService
  web/           Thymeleaf controllers (Public, Auth, Investor, Farm, Share, Admin)
  api/           REST controllers under /api/v1 (DTO records)
  config/        SecurityConfig, DataSeeder, WebConfig
src/main/resources/templates/   pages
src/main/resources/prompts/     land-analysis.txt
src/main/resources/static/      css/app.css, js/map.js
uploads/  (gitignored)
```

## Conventions
- Application status changes **only** in `ApplicationService`; every change → StatusHistory + AuditLog + Notification.
- Thin controllers; `jakarta.validation`; errors shown in the form in Azerbaijani; POST → redirect → flash.
- Money `410 000 AZN`, dates `dd.MM.yyyy HH:mm` (Asia/Baku).
- Enum labels + badge colors only via `StatusLabels`.
- Mask FİN/VÖEN in staff lists (first 3 chars + `****`); show share holders by initials.
- Ownership: other users' applications, farms, files → 404.

## Secrets
- `ANTHROPIC_API_KEY` from environment only — never in templates, JS, logs, git or `application.properties`. `.env` in `.gitignore`.
- `ai.model` property: `claude-haiku-5-5` while developing, `claude-sonnet-5-5` for the demo.
- Cache satellite images, climate and AI results per plot.

## Commands
```bash
./mvnw spring-boot:run          # http://localhost:8080
./mvnw test
```

## Don'ts
No React/Vue, no JWT, no Docker requirement, no microservices, no Flyway, no async/event bus, no chart libraries, no real payments, no pagination unless a list > 50 rows.
