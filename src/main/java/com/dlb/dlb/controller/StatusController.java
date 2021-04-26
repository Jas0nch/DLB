package com.dlb.dlb.controller;

import com.dlb.dlb.service.ManageService;
import com.dlb.dlb.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class StatusController {
  @Autowired MonitoringService monitoringService;
  @Autowired ManageService manageService;

  String clientUrl = "http://3.230.127.51:8080";

  @GetMapping("/hello")
  @ResponseBody
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
  public String[] refresh() throws Exception {
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
    boolean res = monitoringService.onlineStatus(clientUrl);
    return res;
  }

  @GetMapping("/test")
  @ResponseBody
  public void test() throws Exception {
    String host = "18.209.211.129";
    manageService.stopNode(host);
  }
}
