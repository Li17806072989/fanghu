package org.dromara.fanghu;

import cn.dev33.satoken.annotation.SaIgnore;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping("/api/health")
    @SaIgnore
    public Map<String, String> health() {
        return Map.of("service", "fanghu-platform", "status", "ok");
    }
}
