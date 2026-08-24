package com.zuhlke.tradenet.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// No self registration check DataSeeder
@Controller
public class AuthController {

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }
}
