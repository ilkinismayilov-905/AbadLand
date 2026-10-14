# AqroInvest Qarabağ — hakaton dəsti (son versiya)

## Başlamaq
1. Bu qovluğu boş repo kimi açın: `git init && git add . && git commit -m "chore: kit"`.
2. VS Code-da açın və Claude Code-u işə salın. Tələb olunan yeganə alət **JDK 21**-dir, Maven wrapper layihə ilə gəlir.
3. AI analizi üçün açarı **yalnız mühit dəyişəni** kimi verin:
   - macOS/Linux: `export ANTHROPIC_API_KEY=...`
   - Windows: `setx ANTHROPIC_API_KEY "..."`, sonra VS Code-u yenidən açın.
   - Açarı heç vaxt fayla və ya git-ə yazmayın. `.env.example` yalnız nümunədir.
4. Claude Code-da addımları ardıcıl işə salın: `/step 1` … `/step 9`. Step 8 istəyə bağlıdır.
5. Hər addımdan sonra yoxlayın və `git commit` edin. Nəsə pozulsa, `git reset --hard HEAD` ilə son commit-ə qayıdırsınız.
6. Xəta olanda `/fix <xəta mətni>` yazın. Demodan əvvəl `/demo-check` işə salın.
7. Addımlar arasında `/clear` edin. Kontekst itmir: `CLAUDE.md`, `PROGRESS.md` və `docs/SPEC.md` onu daşıyır.

## Fayllar
| Fayl | Nə üçün |
|---|---|
| `PLAN.md` | Saat-saat plan, komanda tapşırıqları, kəsilmə siyahısı, demo ssenarisi |
| `CLAUDE.md` | Claude Code üçün qaydalar, stack, kod quruluşu |
| `docs/SPEC.md` | Tam spesifikasiya: model, statuslar, bal, proqnoz, AI, təqvim, pay, səhifələr, seed |
| `steps/STEP-1…9` | Addımlar, hər birinin sonunda "Done when" yoxlaması |
| `PROGRESS.md` | Claude hər addımdan sonra yeniləyir |
| `.claude/commands/` | `/step`, `/fix`, `/demo-check` əmrləri |
| `docs/pitch/` | İdeya PDF-i (2 səhifə) və biznes modeli PDF-i (3 səhifə) |

## İşə salma
`./mvnw spring-boot:run` → http://localhost:8080 · Swagger: `/swagger-ui.html`

Demo hesablar (şifrə `Demo1234`): `elvin@demo.az`, `rashad@demo.az`, `operator@demo.az`, `komissiya@demo.az`, `admin@demo.az`.
