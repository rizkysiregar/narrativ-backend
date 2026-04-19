package com.rizkysiregar.feedzmore.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/hello")
public class HelloController {

    @GetMapping("version")
    public String helloVersion() {
        return "Hello version 3";
    }

    @GetMapping("/whoami")
    public String whoami(Authentication authentication) {
        return Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities().toString()
        ).toString();
    }


}
