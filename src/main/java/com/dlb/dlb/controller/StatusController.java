package com.dlb.dlb.controller;

import com.dlb.dlb.service.MonitoringService;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class StatusController {
  @Autowired MonitoringService monitoringService;
  String clientUrl = "http://152.7.99.38:8080";

  @GetMapping("/hello")
  public String hello() {
    return "hello";
  }

  @GetMapping("/status")
  public String getStatus(Model model) {
    System.out.println("call status");
    return "chart";
  }

  //  http://152.7.99.38:8000/search_by_title/distributed
  @GetMapping("/refresh")
  @ResponseBody
  public String[] refresh() throws ExecutionException, InterruptedException {
    monitoringService.addUrl(clientUrl);
    String[] cpuData = monitoringService.getCpuData(clientUrl);
    String[] ret = new String[cpuData.length + 1];
    ret[0] = String.valueOf(monitoringService.onlineStatus(clientUrl));
    int i = 1;
    for (String s:cpuData){
      ret[i++] = s;
    }
    return ret;
  }

  @GetMapping("/online")
  @ResponseBody
  public boolean online() throws Exception {
    return monitoringService.onlineStatus(clientUrl);
  }
}
