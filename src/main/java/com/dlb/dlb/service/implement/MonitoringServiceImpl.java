package com.dlb.dlb.service.implement;

import com.dlb.dlb.configration.DLBConfiguration.UpstreamServerGroup;
import com.dlb.dlb.configration.DLBConfiguration.UpstreamServerGroups;
import com.dlb.dlb.service.ManageService;
import com.dlb.dlb.service.MonitoringService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MonitoringServiceImpl implements MonitoringService {

  static ConcurrentHashMap<String, LinkedList<String>> cpuData = new ConcurrentHashMap<>();
  static ConcurrentHashMap<String, LinkedList<String>> memData = new ConcurrentHashMap<>();
  static String live = "hello";
  static String heartbeatSuffix = "/hello";
  static String statusSuffix = "/cpu";
  int capacity = 10;
  ConcurrentHashMap<String, Boolean> status;
  ConcurrentHashMap<String, HashSet<String>> urls; // urls need to be monitoring
  ConcurrentHashMap<String, HashSet<String>> heartbeating; // urls already monitoring

  double scaleThreshold = 0.75;
  double descaleThreshold = 0.5;

  @Autowired
  ManageService manageService;

  @Autowired
  UpstreamServerGroups upstreamServerGroups;

  public MonitoringServiceImpl() throws ExecutionException, InterruptedException {
    status = new ConcurrentHashMap<>();
    Map<String, UpstreamServerGroup> map = upstreamServerGroups.getMap();
    urls = new ConcurrentHashMap<>();
    heartbeating = new ConcurrentHashMap<>();
  }

  public void addUrl(String groupName, String url) throws ExecutionException, InterruptedException {
    if (!urls.containsKey(groupName)) urls.put(groupName, new HashSet<>());

    urls.get(groupName).add(url);

    cpuData.put(url, new LinkedList<>());
    memData.put(url, new LinkedList<>());

    heartbeat(groupName);
  }

  public boolean onlineStatus(String clientUrl) {
    return status.getOrDefault(clientUrl, false);
  }

  synchronized void manageServer(String groupName) throws Exception {
    double cpu = 0;
    double mem = 0;
    int size = heartbeating.get(groupName).size();
    for (String ip:heartbeating.get(groupName)){
      String[] responseArr = sendRequest(ip + statusSuffix).split(" ");
      if (responseArr.length != 3) {
        throw new Exception("info data incorrect format");
      }
      cpu += Double.valueOf(responseArr[1]);
      mem += Double.valueOf(responseArr[2]);
    }

    if (cpu / size > scaleThreshold || mem / size > scaleThreshold){
      manageService.scale(groupName);
    }
    else if (cpu / size < descaleThreshold || mem / size < descaleThreshold){
      manageService.descale(groupName);
    }
  }

  public void heartbeat(String groupName) throws ExecutionException, InterruptedException {
    for (String url : urls.get(groupName)) {
      if (heartbeating.get(groupName).contains(url)) {
        continue;
      }

      if (!heartbeating.containsKey(groupName)) {
        heartbeating.put(groupName, new HashSet<>());
        // create a task manager whole group

        new Timer("timer - " + url)
            .schedule(
                new TimerTask() {
                  @SneakyThrows
                  @Override
                  public void run() {
                    manageServer(groupName);
                  }
                },
                1000,
                1000);

      }

      heartbeating.get(groupName).add(url);

      new Timer("timer - " + url)
          .schedule(
              new TimerTask() {
                @SneakyThrows
                @Override
                public void run() {
                  String ret = sendRequest(url + heartbeatSuffix);
                  if (ret.trim().equals(live)) {
                    status.put(url, true);
                    System.out.println(url + " live");
                  } else {
                    System.out.println(url + " dead");
                    status.put(url, false);
                  }

                  manageServer(groupName);
                }
              },
              1000,
              1000);
    }
  }

  String sendRequest(String clientUrl) throws ExecutionException, InterruptedException {
    CompletableFuture<String> completableFuture =
        CompletableFuture.supplyAsync(
            () -> {
              URL url = null;
              String res = "";
              try {
                url = new URL(clientUrl);
              } catch (MalformedURLException e) {
                e.printStackTrace();
              }

              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
                for (String line; (line = reader.readLine()) != null; ) {
                  //                  System.out.println(line);
                  res += line + " ";
                }
              } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
              } catch (IOException e) {
                e.printStackTrace();
              }

              System.out.println(
                  LocalDateTime.now().toString() + " query " + clientUrl + " return " + res);
              return res;
            });

    while (!completableFuture.isDone()) {
      //      System.out.println("CompletableFuture is not finished yet...");
    }

    return completableFuture.get();
  }

  @Override
  public String[] getCpuData(String clientUrl) throws Exception {
    String[] responseArr = sendRequest(clientUrl + statusSuffix).split(" ");
    if (responseArr.length != 3) {
      throw new Exception("info data incorrect format");
    }

    String cpu = responseArr[1];

    if (cpuData.get(clientUrl).size() >= capacity) {
      cpuData.get(clientUrl).removeFirst();
    }
    cpuData.get(clientUrl).addLast(cpu);

    return parseListToStringArray(cpuData.get(clientUrl));
  }

  @Override
  public String[] getMemData(String clientUrl) throws Exception {
    String[] responseArr = sendRequest(clientUrl + statusSuffix).split(" ");
    if (responseArr.length != 3) {
      throw new Exception("info data incorrect format");
    }

    String memory = responseArr[2];

    if (memData.size() >= capacity) {
      memData.get(clientUrl).removeFirst();
    }
    memData.get(clientUrl).addLast(memory);

    return parseListToStringArray(memData.get(clientUrl));
  }

  @Override
  public String[][] getInfoData(String clientUrl) throws Exception{
    String[] responseArr = sendRequest(clientUrl + statusSuffix).split(" ");
    if (responseArr.length != 3) {
      throw new Exception("info data incorrect format");
    }

    String cpu = responseArr[1];

    if (cpuData.size() >= capacity) {
      cpuData.get(clientUrl).removeFirst();
    }
    cpuData.get(clientUrl).addLast(cpu);

    String memory = responseArr[2];

    if (memData.size() >= capacity) {
      memData.get(clientUrl).removeFirst();
    }
    memData.get(clientUrl).addLast(memory);

    String[][] ret = new String[2][capacity];

    ret[0] = parseListToStringArray(cpuData.get(clientUrl));
    ret[1] = parseListToStringArray(memData.get(clientUrl));

    return ret;
  }

  String[] parseListToStringArray(List<String> data) {
    String[] res = new String[data.size()];
    int idx = 0;
    for (String s : data) {
      res[idx++] = s;
    }

    return res;
  }
}
