package com.reder.order.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope
public class EnvController {
    @Value("${env}")
    private String env;
    @GetMapping("/env")
    public String print(){
        return env;
    }
}
