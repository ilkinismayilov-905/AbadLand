Rebrand "AqroInvest" → "AgroInvest" and apply the logo (files in src/main/resources/static/img/).
1. Rename all user-visible text and docs (all case variants), Swagger title, EKTİS payload source → "AGROINVEST_QARABAG". Do NOT rename Java package az.aqroinvest, artifactId or DB names.
2. Header: WHITE background, 1px #DDE3DC border, agroinvest-logo.png (44px desktop / 36px mobile, alt "AgroInvest Qarabağ"), nav ink #1F2933, active green pill. Dark mode: agroinvest-logo-ondark.png.
3. Footer: #1E4A28 with agroinvest-logo-white.png (40px). Login/register: centered logo 64px.
4. <head>: favicon.ico, favicon-32.png, apple-touch-icon favicon-180.png, site.webmanifest (name "AgroInvest Qarabağ", short_name "AgroInvest", icons 192/512, theme #2E6B3A).
5. Empty states: agroinvest-icon.png 64px, reduced opacity.
Done when grep finds no "Aqro" in user-facing code/docs (except Azerbaijani words like "Aqrar", "Aqronom", "Aqroturizm").
