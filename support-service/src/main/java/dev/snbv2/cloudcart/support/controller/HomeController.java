package dev.snbv2.cloudcart.support.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Spring MVC controller that serves the main Thymeleaf index page.
 *
 * <p>Maps the application root path ({@code /}) to the {@code index} view template,
 * providing the entry point for the web-based chat user interface.
 */
@Controller
public class HomeController {

    /**
     * Handles GET requests to the application root and returns the index view.
     *
     * @return the name of the Thymeleaf template to render ({@code "index"})
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
