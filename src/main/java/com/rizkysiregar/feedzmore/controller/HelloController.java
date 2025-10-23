package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.service.HelloService;
import org.apache.catalina.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final HelloService service;

    public HelloController(HelloService service) {
        this.service = service;
    }

    @GetMapping("/hello")
    public String greet() {
        return service.greet();
    }
}
