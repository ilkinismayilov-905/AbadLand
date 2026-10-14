# Step 9 — Polish, README, demo test (≈ 30 min)
No new features.

1. Walk the demo path in SPEC end to end; fix anything broken.
2. Every page: no English leaks, correct Azerbaijani letters, empty states ("Hələ müraciət yoxdur"), custom 403/404/500 pages in Azerbaijani, 375px width.
3. Security: other investor's application/farm/files → 404; staff lists mask FİN/VÖEN; no FİN or API key in logs; `ANTHROPIC_API_KEY` only from env.
4. One `@SpringBootTest` for the demo path through services (submit → review → info → respond → decide → integration SUCCESS → FarmProject → calendar tasks). Mock the AI client.
5. `README.md`: what it is, run (`./mvnw spring-boot:run`), env vars (`ANTHROPIC_API_KEY`, `ai.model`), demo accounts, pages, short mermaid architecture, switching to PostgreSQL and real EKTİS, roadmap.
6. Update `PROGRESS.md`.
