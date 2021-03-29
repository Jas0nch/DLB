package com.dlb.dlb.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@Controller
public class StatusController {
  @GetMapping("/hello")
  public String hello() {
    return "hello";
  }

  static LinkedList<String> data = new LinkedList<>();
  int capacity = 10;

  @GetMapping("/status")
  public String getStatus(Model model){
    System.out.println("call status");
    return "chart";
  }

  @GetMapping("/refresh")
  @ResponseBody
  public String[] refresh() throws ExecutionException, InterruptedException {
    CompletableFuture<String> completableFuture = CompletableFuture.supplyAsync(() -> {
      URL url = null;
      String res = "";
      try {
        url = new URL("http://3.235.98.92:8080/cpu");
      } catch (MalformedURLException e) {
        e.printStackTrace();
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
        for (String line; (line = reader.readLine()) != null;) {
          System.out.println(line);
          res += line + " ";
        }
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return res;
    });

    while (!completableFuture.isDone()) {
//      System.out.println("CompletableFuture is not finished yet...");
    }

    String result = completableFuture.get();
    System.out.println("call refresh");
    String cpu = result.split(" ")[1];

    if (data.size() >= capacity){
      data.removeFirst();
    }
    data.addLast(cpu);

    return parseDataToStringArray();
  }

  String[] parseDataToStringArray(){
    String[] res = new String[data.size()];
    int idx = 0;
    for (String s:data){
      res[idx++] = s;
    }

    return res;
  }

}
