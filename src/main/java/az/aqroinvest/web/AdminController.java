package az.aqroinvest.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    @GetMapping("/idare")
    public String dashboard() {
        return "idare";
    }
}
