# Plan — AgroInvest Qarabağ (son versiya)

**Format:** bir Spring Boot tətbiqi həm backend-i, həm də HTML səhifələri verir (Thymeleaf + Tailwind CDN + Leaflet + three.js). H2 bazası yaddaşda işləyir: restart demonu sıfırlayır. Deploy Render-də Docker ilə edilir.

## Modullar
1. **Dövlət sahələri:** müsabiqə → müraciət → operator → komissiya → EKTİS (Step 1–5)
2. **Kəşf və analiz:** xəritə, kataloq, meyvə filtri, uyğunluq testi, ROI + proqnoz, peyk + AI, 3D (Step 2, 3, 6, 6b)
3. **İcarədən sonra:** təsərrüfat idarəetməsi, əkin təqvimi, monitorinq (Step 7)
4. **Açıq hərrac** (Step 7b)

## Qalan ardıcıllıq
| Növbə | Step | Vaxt | Qeyd |
|---|---|---|---|
| 1 | 7 — təsərrüfat + əkin təqvimi | 0:50 | |
| 2 | 7b — hərrac | 1:00 | Hüquqi riskə görə ən sona; vaxt yoxdursa slayda keçir |
| 3 | 9 — cilalama, README, test | 0:30 | Həmişə sonuncu |
| — | 8 — pay sistemi | — | Atılır |

Əlavə promptlar (`prompts-for-claude-code/`) lazım olduqca, step-lərin arasında icra olunur.

## Gecikmə olsa — bu sıra ilə atın
1. Step 7b (hərrac) → slayd
2. Müqayisə səhifəsi, audit səhifəsi

**Heç vaxt atılmayanlar:** uyğunluq testi, pasport, AI analizi, müraciət dövrü + komissiya qərarı, EKTİS jurnalı.

## Demo (7 dəqiqə)
**Hazırlıq:**
- Tətbiqi yenidən başladın, `ai.model=claude-sonnet-5-5` qoyun.
- LCN-001 üçün AI analizini əvvəlcədən bir dəfə işə salın.
- 3 pəncərə açın: Elvin (investor), operator, komissiya.

**A. Dövlət sahəsi (3 dəq)**
1. Elvin → uyğunluq testi → FZL-001 birinci gəlir → "Bal necə hesablanır?"
2. Kataloq → "Hansı meyvə üçün?" = Nar → FZL-001 pasportu: ROI, 3 ssenari, risklər.
3. LCN-001 → peyk + AI analizi → 3D quşbaxışı.
4. FZL-001-ə müraciət edir → operator əlavə məlumat istəyir → Elvin cavab verir → komissiya müsbət qərar verir → EKTİS jurnalında SUCCESS.

**B. (vaxt varsa)** Hərrac canlı paneli, demo-bot ilə.

**Bağlanış:** "İnvestor uyğun dövlət torpağını tapır, riskini əvvəlcədən görür, müraciət edir və icarədən sonra təsərrüfatı izləyir. Hər addım şəffafdır və EKTİS-ə hazırdır."

## Komanda üçün
- Bütün rəqəmlər sınaq məlumatıdır, AI tövsiyədir, ödənişlər simulyasiyadır. Təqdimatda bunları açıq deyin.
- Tərəfdaşlar bölməsi "potensial"dır, rəsmi tərəfdaşlıq kimi təqdim etməyin.
- Pitch materialları `docs/pitch/` qovluğundadır.
