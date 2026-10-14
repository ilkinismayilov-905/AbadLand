package az.aqroinvest.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class WebErrorAdvice {

    /** Upload over spring.servlet.multipart limits → back to the form with a flash message. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest req, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Fayl 5 MB-dan böyük ola bilməz.");
        String referer = req.getHeader("Referer");
        String host = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort();
        return "redirect:" + (referer != null && referer.startsWith(host) ? referer.substring(host.length()) : "/");
    }
}
