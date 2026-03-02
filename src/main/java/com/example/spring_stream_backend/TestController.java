package com.example.spring_stream_backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController{
    @GetMapping("/health")
    public String test(){
        return "test";
    }
}












