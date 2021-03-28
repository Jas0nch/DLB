package com.dlb.dlb.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Status {
  @GetMapping("/hello")
  public String hello() {
    return "hello";
  }
}
