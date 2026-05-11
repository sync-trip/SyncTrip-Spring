package com.sync.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String helloString() {
        return "Hello, SyncTrip!";
    }

    @GetMapping("/api/test")
    public Map<String, String> helloJson() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "안녕! ");
        response.put("status", "success");
        return response;
    }
}
