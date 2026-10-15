Upgrade the AI land analysis to src/main/resources/prompts/land-analysis.txt (system) and land-analysis-user.txt (user template).
1. Draw the plot polygon on the satellite JPEG as a 4px YELLOW outline (Java2D, same bbox projection as the Esri export); cache the annotated image.
2. Fill the template from Plot + PlotClimate; missing → "məlum deyil"; {cropTable} = "CODE | nameAz | frostToleranceC | maxElevationM | waterNeed" per line.
3. Image block first, then text; temperature 0.2; max_tokens 1500; model from ai.model.
4. Parse/validate JSON (keys, scores 0–100, levels LOW|MEDIUM|HIGH, crop codes allowed else null). Invalid → retry once with "Cavab etibarlı JSON deyildi. Yalnız sxemə uyğun JSON qaytar."; still invalid → FAILED + polite message.
5. Passport UI: observations, risks with level badges, "Sahədə yoxlayın" checklist (recommendedChecks), summary, crop bars, notRecommended, irrigationAdvice, confidence, dataGaps, disclaimer, annotated image.
6. Keep writing AI crops into PlotCropSuitability (source AI). 7. Unit tests for validation (valid, broken, unknown code) with mocked HTTP. 8. Update SPEC.
