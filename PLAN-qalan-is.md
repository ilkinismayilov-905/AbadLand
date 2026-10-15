# Plan — AgroInvest Qarabağ (son versiya)

**Format:** bir Spring Boot tətbiqi həm backend-i, həm də HTML səhifələri verir (Thymeleaf + Tailwind CDN + Leaflet + three.js). H2 bazası yaddaşda işləyir: restart demonu sıfırlayır. Deploy Render-də Docker ilə edilir.

## Modullar
1. **Dövlət sahələri:** müsabiqə → müraciət → operator → komissiya → EKTİS (Step 1–5)
2. **Kəşf və analiz:** xəritə, kataloq, meyvə filtri, uyğunluq testi, ROI + proqnoz, peyk + AI, 3D (Step 2, 3, 6, 6b)
3. **İcarədən sonra:** təsərrüfat idarəetməsi, əkin təqvimi, monitorinq (Step 7)
4. **Açıq hərrac** (Step 7b)
5. **Sahibkar modulu:** qeydiyyat, elan, investisiya təklifləri, sahibkar paneli, aylıq hesabat, investorun layihə görünüşü (Step 10a–10d)

## Qalan ardıcıllıq
| Növbə | Step | Vaxt | Qeyd |
|---|---|---|---|
| 1 | 7 — təsərrüfat + əkin təqvimi | 0:50 | 10c və 10d bundan asılıdır |
| 2 | 10a — sahibkar qeydiyyatı, elan, moderasiya | 0:45 | |
| 3 | 10b — elanlar və investisiya təklifləri | 0:35 | |
| 4 | 10c — sahibkar paneli, aylıq hesabat | 0:45 | |
| 5 | 10d — investorun layihə görünüşü | 0:35 | |
| 6 | 7b — hərrac | 1:00 | Hüquqi riskə görə ən sona; vaxt yoxdursa slayda keçir |
| 7 | 9 — cilalama, README, test | 0:30 | Həmişə sonuncu |
| — | 8 — pay sistemi | — | Atılır: 10b investisiya təkliflərini artıq əhatə edir |

Əlavə promptlar (`prompts-for-claude-code/`) lazım olduqca, step-lərin arasında icra olunur.

## Gecikmə olsa — bu sıra ilə atın
1. Step 7b (hərrac) → slayd
2. 10d-də sual-cavab və foto qalereya
3. 10c-də rəqib müqayisəsi (AZN/ha benchmark)
4. 10b-də əks-təklif (yalnız "göndər" və "qəbul et" qalsın)
5. Müqayisə səhifəsi, audit səhifəsi

**Heç vaxt atılmayanlar:** uyğunluq testi, pasport, AI analizi, müraciət dövrü + komissiya qərarı, EKTİS jurnalı, sahibkar elanı + aylıq hesabat, investorun layihə görünüşü.

## Demo (7 dəqiqə)
**Hazırlıq:**
- Tətbiqi yenidən başladın, `ai.model=claude-sonnet-5-5` qoyun.
- LCN-001 üçün AI analizini əvvəlcədən bir dəfə işə salın.
- 4 pəncərə açın: Elvin (investor), operator, komissiya, `sahibkar@demo.az`.

**A. Dövlət sahəsi (3 dəq)**
1. Elvin → uyğunluq testi → FZL-001 birinci gəlir → "Bal necə hesablanır?"
2. Kataloq → "Hansı meyvə üçün?" = Nar → FZL-001 pasportu: ROI, 3 ssenari, risklər.
3. LCN-001 → peyk + AI analizi → 3D quşbaxışı.
4. FZL-001-ə müraciət edir → operator əlavə məlumat istəyir → Elvin cavab verir → komissiya müsbət qərar verir → EKTİS jurnalında SUCCESS.

**B. Sahibkar (3 dəq)**
5. `sahibkar@demo.az` paneli: elan statistikası, gələn təklif, **gecikmiş aylıq hesabat** xəbərdarlığı.
6. Sahibkar aylıq hesabatı göndərir: işlər, tapşırıqlar, xərclər, fotolar → operator qəbul edir.
7. Elvin → "İnvestisiyalarım" → layihə: payı, "Proses harada?", pul necə xərclənir, hesabatlar, fotolar. Sual verir.
8. Operator → Monitorinq: Dövlət / Özəl filtri, gecikmələr, risk nişanları.

**C. (vaxt varsa)** Hərrac canlı paneli, demo-bot ilə.

**Bağlanış:** "Torpaq dövlətin də olsa, sahibkarın da olsa: investor uyğun torpağı tapır, riskini əvvəlcədən görür və pulunun necə xərcləndiyini hər ay izləyir. Hər addım şəffafdır və EKTİS-ə hazırdır."

## Komanda üçün
- Bütün rəqəmlər sınaq məlumatıdır, AI tövsiyədir, ödənişlər simulyasiyadır. Təqdimatda bunları açıq deyin.
- Tərəfdaşlar bölməsi "potensial"dır, rəsmi tərəfdaşlıq kimi təqdim etməyin.
- Pitch materialları `docs/pitch/` qovluğundadır.
