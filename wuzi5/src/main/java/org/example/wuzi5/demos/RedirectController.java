package org.example.wuzi5.demos;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;


@Controller
public class RedirectController {
    @GetMapping("/")
    public String redirectToIndex(Model model) {
        return "index";
    }
}