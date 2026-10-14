package az.aqroinvest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling // auction status tick (SPEC "Open auction")
public class AqroInvestApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Baku"));
        SpringApplication.run(AqroInvestApplication.class, args);
    }
}
