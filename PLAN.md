# Plan — AgroInvest Qarabağ MVP

**Format:** bir Spring Boot tətbiqi həm backend-i, həm də HTML səhifələri verir (Thymeleaf + Tailwind CDN + Leaflet). Node, React və Docker yoxdur. Baza H2-dir: hər açılışda sınaq məlumatı təzədən yüklənir, yəni restart = demo sıfırlama. Bu, TZ-nin tövsiyəsinə (Java + HTML/CSS) birbaşa uyğundur.

**Müddət:** əsas hissə təxminən **7,5 saat**. İstəyə bağlı pay sistemi (Step 8) ilə birlikdə təxminən 8 saat.

## Vaxt cədvəli

| Saat | Developer (Claude Code) | Layihə rəhbəri | Dizayner | Data / QA |
|---|---|---|---|---|
| 0:00–0:45 | **Step 1** — skelet, bütün model, seed, giriş | Pitch strukturu, problem slaydları | Rəng + loqo | Sahə siyahısı və bitki parametrlərini yoxla; **Anthropic API açarını al**, Console-da xərc limiti qoy |
| 0:45–2:10 | **Step 2** — xəritə, kataloq, meyvə filtri, pasport, ROI + proqnoz, müqayisə | Proses xəritəsi (müraciətdən yığıma qədər) | Deck şablonu | Step 1 testi, nümunə PDF-lər ("NÜMUNƏ" yazılı) |
| 2:10–2:50 | **Step 3** — uyğunluq testi | Q&A bankı (AI, pay, iş yerləri, gəlir modeli) | Həll + arxitektura slaydları | Step 2 testi: meyvə filtri, proqnoz |
| 2:50–4:20 | **Step 4** — müraciət, baxış dövrü, bildirişlər | Demo ssenarisi | Ekran görüntüləri | Step 3 testi |
| 4:20–5:10 | **Step 5** — təsdiq, EKTİS mock, dashboard, API | 1-ci prova | Biznes modeli slaydı | Step 4 testi — **ən vacib** |
| 5:10–6:10 | **Step 6** — peyk, iqlim, AI analizi | Yol xəritəsi slaydı | AI ekranının slaydı | Laçın və Füzuli üçün AI nəticəsi ağlabatandırmı? |
| 6:10–7:00 | **Step 7** — təsərrüfat idarəetməsi + əkin təqvimi | 2-ci prova | Son vizual düzəlişlər | Step 5–6 testi |
| 7:00–7:35 | **Step 8** (yalnız vaxt varsa) — pay sistemi | — | — | Step 7 testi |
| 7:35–8:05 | **Step 9** — cilalama, README, test | 3-cü prova, vaxt ölçümü | — | Tam demo testi, ehtiyat video |

**7:00-dan sonra yeni funksiya yalnız Step 8 ola bilər, o da vaxt varsa.** Gecikmə varsa, Step 8-i atın və pay ideyasını slaydda göstərin.

## Nə var / nə yoxdur

| ✅ MVP-də işləyir | 🟡 Simulyasiya / sadə | ❌ Yol xəritəsi |
|---|---|---|
| Xəritə (+peyk), kataloq, filtrlər, pasport, müqayisə | Pay sistemi (real ödəniş yoxdur) | Real ödəniş, pay reyestri, lisenziya |
| Meyvə təyinatı və uyğunluq faizləri | Proqnozlar (sınaq parametrləri) | NDVI ilə peykdən əkin monitorinqi |
| Uyğunluq testi + izahlı bal | EKTİS (mock adapter) | Real EKTİS, ASAN Login / İmza |
| ROI + proqnoz (3 ssenari, məhsul, risk) | Mock FİN/VÖEN yoxlaması | SMS, e-poçt, mobil tətbiq |
| Peyk görüntüsü + real iqlim + AI analizi | — | Məşğulluq Agentliyi, bank inteqrasiyası |
| Müraciət, status dövrü, bildirişlər, komissiya qərarı | — | Ekspert təsdiqi paneli (meyvə etiketləri) |
| Təsərrüfat idarəetməsi, əkin təqvimi, monitorinq | — | Məhsul satışı bazarı |
| Dashboard, audit, REST API + Swagger | — | PostgreSQL ilə istehsal (profil hazırdır) |

## Gecikmə olsa — bu sıra ilə atın
1. Step 8 (pay sistemi) → slayd
2. Müqayisə səhifəsi
3. Step 7-də rüblük hesabat (öhdəliklər, əkin təqvimi və işçi heyəti qalsın)
4. Proqnozda ssenari cədvəli (tək "Baza" rəqəmi qalsın)
5. Audit səhifəsi (jurnal bazada qalır)
6. REST API / Swagger
7. EKTİS retry düyməsi

**Heç vaxt atılmayanlar:** uyğunluq testi, pasport, meyvə filtri, müraciət + nömrə, status dövrü + bildiriş, komissiya qərarı, EKTİS jurnalı, AI analizi, əkin təqvimi.

## Demo (6 dəqiqə)

**Hazırlıq:**
- Tətbiqi yenidən başladın (təmiz məlumat) və `ai.model=claude-sonnet-5-5` qoyun.
- **LCN-001 üçün AI analizini əvvəlcədən bir dəfə işə salın** ki, keşə düşsün.
- 3 pəncərə açın: normal (Elvin), incognito 1 (operator), incognito 2 (komissiya). Zoom 110–125%.

**Ssenari:**
1. **Elvin** → uyğunluq testi (Bağçılıq, 500 000, 20–50 ha, Füzuli, 3–10 il) → FZL-001 birinci gəlir → "Bal necə hesablanır?"
2. Kataloq → "Hansı meyvə üçün?" = Nar.
3. FZL-001 pasportu: mina, su, yol; meyvə faizləri; ROI + 3 ssenari + risklər. Müqayisə: FZL-001 / FZL-002.
4. LCN-001 (Laçın) → Peyk → AI analizi: iqlim kartları, uyğun bitkilər, risklər.
5. FZL-001 → "Müraciət et" → form + PDF → nömrə alır.
6. **Operator** → Baxışa başla → Əlavə məlumat istə: "Torpaq analizi".
7. **Elvin** → bildiriş → PDF + cavab göndərir.
8. **Komissiya** → Müsbət qərar + səbəb → İnteqrasiya jurnalında SUCCESS + EKTİS ref görünür.
9. **Elvin** → Təsərrüfatım → əkin planı: Fındıq 40 ha → əkin təqvimi yaranır → 12 işçi əlavə edir (9-u yerli).
10. **Operator** → Monitorinq: vəd edilən iş yerləri faktiki ilə müqayisədə, gecikən tapşırıq (seed layihəsində qırmızı).
11. (Step 8 varsa) Pay layihələri → Rəşadın layihəsi, 40% satılıb.

**Bağlanış:** "Əksər həllər müraciət formasından başlayır. Biz investorun ilk sualından başladıq və məhsul yığımına qədər getdik. Portal əsas funksiyalarda pulsuzdur. Biz yalnız investor qazananda qazanırıq."

## Komanda üçün qaydalar
- Developer-ə yalnız hazır şey verin: fayl, rəng kodu və ya konkret bug (addımlar, gözlənilən nəticə, baş verən nəticə, ekran görüntüsü).
- Demonu bloklayan bug-ı dərhal deyin. Qalanlarını siyahıya yazın, onlara Step 9-da baxılacaq.
- Bütün rəqəmlər sınaq məlumatıdır, AI nəticələri tövsiyədir, pay sistemi simulyasiyadır. Təqdimatda bunları açıq deyin.
- Pitch materialları: `docs/pitch/` qovluğunda ideya PDF-i və biznes modeli PDF-i.
