package xlike.top.cloudflare.controller;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author xlike
 */
@Controller
@AllArgsConstructor
public class IndexController {

    @GetMapping("/")
    public String toLogin() {
        return "cloud";
    }


    @GetMapping("/cloud")
    public String cloud() {
        return "cloud";
    }

}
