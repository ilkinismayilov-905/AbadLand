package az.aqroinvest.ai;

import az.aqroinvest.domain.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AiViewAndOutlineTest {

    final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesCurrentPromptShape() throws Exception {
        AiView v = json.readValue("""
                {"summary":"s","landCover":"l","terrain":"t","waterSigns":"w","observations":["Kanal görünür"],
                 "suitableCrops":[{"code":"GRAPE","name":"Üzüm","score":84,"reason":"r"}],"notRecommended":[],
                 "risks":[{"title":"Şaxta","level":"MEDIUM","reason":"-9.8 °C"},{"title":"Yağıntı","level":"weird","reason":"az"}],
                 "irrigationAdvice":"i","recommendedChecks":["ANAMA sertifikatı"],"confidence":"MEDIUM","dataGaps":[],"extra":1}""", AiView.class);
        assertThat(v.observations()).containsExactly("Kanal görünür");
        assertThat(v.recommendedChecks()).containsExactly("ANAMA sertifikatı");
        assertThat(v.risks().get(0).levelEnum()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(v.risks().get(0).text()).isEqualTo("Şaxta — -9.8 °C");
        assertThat(v.risks().get(1).levelEnum()).isNull(); // unknown level → plain bullet, no crash
    }

    @Test
    void stillReadsOlderAnalysesWithStringRisks() throws Exception {
        AiView v = json.readValue("""
                {"summary":"s","risks":["Yay quraqlığı — suvarma mütləqdir"],"suitableCrops":[],"confidence":"LOW"}""", AiView.class);
        assertThat(v.risks()).hasSize(1);
        assertThat(v.risks().get(0).title()).isEqualTo("Yay quraqlığı — suvarma mütləqdir");
        assertThat(v.risks().get(0).levelEnum()).isNull();
        assertThat(v.observations()).isNull();
    }

    @Test
    void squareExtentAndPixelMapping() {
        List<double[]> ring = List.of(new double[]{0, 0}, new double[]{200, 0}, new double[]{200, 100}, new double[]{0, 100});
        double[] e = SatelliteImageService.squareExtent(ring, 0.15);
        assertThat(e[2] - e[0]).isCloseTo(e[3] - e[1], within(1e-9));      // square → Esri does not re-fit it
        assertThat(e[2] - e[0]).isCloseTo(200 * 1.3, within(1e-9));        // longer side + 15 % each side
        assertThat(SatelliteImageService.pixel(new double[]{100, 50}, e, 1024)).containsExactly(512.0, 512.0); // centre
        double[] topLeft = SatelliteImageService.pixel(new double[]{0, 100}, e, 1024);
        assertThat(topLeft[0]).isCloseTo(1024 * 30 / 260.0, within(1e-6));
        assertThat(topLeft[1]).isCloseTo(1024 * 80 / 260.0, within(1e-6)); // y axis flipped
    }

    @Test
    void outlineIsDrawnInYellow() throws Exception {
        BufferedImage blank = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(blank, "jpg", out);
        List<double[]> ring = List.of(new double[]{0, 0}, new double[]{100, 0}, new double[]{100, 100}, new double[]{0, 100});
        double[] e = SatelliteImageService.squareExtent(ring, 0.15);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(SatelliteImageService.withOutline(out.toByteArray(), ring, e)));
        double[] edge = SatelliteImageService.pixel(new double[]{50, 0}, e, 1024); // middle of the bottom edge
        int rgb = img.getRGB((int) Math.round(edge[0]), (int) Math.round(edge[1]));
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        assertThat(r).isGreaterThan(200);
        assertThat(g).isGreaterThan(170);
        assertThat(b).isLessThan(90);
        assertThat(img.getRGB(512, 512) & 0xFFFFFF).isLessThan(0x202020); // inside stays untouched (black)
    }
}
