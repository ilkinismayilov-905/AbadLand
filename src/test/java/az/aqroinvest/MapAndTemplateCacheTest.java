package az.aqroinvest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** /xerite?qat=peyk starts on the satellite layer; production caches templates, the dev profile does not. */
@SpringBootTest
@AutoConfigureMockMvc
class MapAndTemplateCacheTest {

    @Autowired MockMvc mvc;
    @Autowired ThymeleafProperties thymeleaf;

    String html(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void satellitePreselectionOnlyWithTheParameter() throws Exception {
        String plain = html("/xerite");
        assertThat(plain).contains("createMap('map', false)", "drawPlots(map,", "addLegend(map)", "/js/plot3d.js");
        assertThat(html("/xerite?qat=peyk")).contains("createMap('map', true)", "drawPlots(map,", "addLegend(map)");
        assertThat(html("/xerite?qat=xerite")).contains("createMap('map', false)");
        // Filters still work with the parameter present
        assertThat(html("/xerite?qat=peyk&fruit=POMEGRANATE")).contains("createMap('map', true)");
    }

    @Test
    void templatesCachedExceptInDevProfile() throws Exception {
        assertThat(thymeleaf.isCache()).isTrue(); // default profile = Render
        Properties dev = new Properties();
        dev.load(new java.io.InputStreamReader(new ClassPathResource("application-dev.properties").getInputStream(), StandardCharsets.UTF_8));
        assertThat(dev.getProperty("spring.thymeleaf.cache")).isEqualTo("false");
        assertThat(dev.getProperty("spring.thymeleaf.prefix")).isEqualTo("file:src/main/resources/templates/");
    }
}
