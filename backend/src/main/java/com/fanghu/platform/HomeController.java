package com.fanghu.platform;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class HomeController {
    @GetMapping("/")
    public RedirectView home() {
        return new RedirectView("http://localhost:5173/");
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("service", "fanghu-platform", "status", "ok");
    }
}
