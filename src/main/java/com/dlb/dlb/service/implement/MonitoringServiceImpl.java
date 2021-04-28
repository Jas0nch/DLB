package com.dlb.dlb.service.implement;

import com.dlb.dlb.configration.DLBConfiguration.UpstreamServerGroups;
import com.dlb.dlb.service.ManageService;
import com.dlb.dlb.service.MonitoringService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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
  static String httpPrefix = "http://";
  static String heartbeatSuffix = ":8080/hello";
  static String statusSuffix = ":8080/cpu";
  int capacity = 10;
  ConcurrentHashMap<String, Boolean> status;
  ConcurrentHashMap<String, HashSet<String>> urls; // urls need to be monitoring
  ConcurrentHashMap<String, HashSet<String>> heartbeating; // urls already monitoring

  double cpuScaleThreshold = 0.2;
  double cpuDescaleThreshold = 0.5;

  double memScaleThreshold = 1.1;
  double memDescaleThreshold = 0.5;


  @Autowired ManageService manageService;

  @Autowired UpstreamServerGroups upstreamServerGroups;

  public MonitoringServiceImpl() throws ExecutionException, InterruptedException {
    status = new ConcurrentHashMap<>();
    urls = new ConcurrentHashMap<>();
    heartbeating = new ConcurrentHashMap<>();
  }

  public void addUrl(String groupName, String url) {
    if (!urls.containsKey(groupName)) urls.put(groupName, new HashSet<>());

    urls.get(groupName).add(url);

    cpuData.put(url, new LinkedList<>());
    memData.put(url, new LinkedList<>());

    heartbeat(groupName);
  }

  public void deleteUrl(String groupName, String url){
    urls.get(groupName).remove(url);

    if (urls.get(groupName).size() == 0){
      groupTimer.get(groupName).cancel();
      groupTimer.remove(groupName);
    }

    cpuData.remove(url);
    memData.remove(url);

    heartbeating.get(groupName).remove(url);
    stopHeartbeat(url);
  }

  ConcurrentHashMap<String, Timer> groupTimer = new ConcurrentHashMap<>();
  ConcurrentHashMap<String, Timer> urlTimer = new ConcurrentHashMap<>();

  public void stopHeartbeat(String url){
    if (urlTimer.containsKey(url)){
      urlTimer.get(url).cancel();
      urlTimer.remove(url);
    }
  }

  public boolean onlineStatus(String clientUrl) {
    return status.getOrDefault(clientUrl, false);
  }

  int hbCnt = 0;  // heartbeat cnt
  int dsCnt = 0;  // descale cnt

  synchronized void manageServer(String groupName) throws Exception {
    try{
      double cpu = 0;
      double mem = 0;
      int size = heartbeating.get(groupName).size();
      for (String ip : heartbeating.get(groupName)) {
        String[] responseArr = sendRequest(ip + statusSuffix).split(" ");
        if (responseArr.length != 3) {
          //          throw new Exception("info data incorrect format");
          System.out.println("info data incorrect format");
          break;
        }
        cpu += Double.valueOf(responseArr[1]);
        mem += Double.valueOf(responseArr[2]);
      }

      if (hbCnt > 15){
        if (cpu / size / 100 > cpuScaleThreshold || mem / size / 100 > memScaleThreshold) {
          manageService.scale(groupName);
          dsCnt = 0;
        } else if (cpu / size / 100 < cpuDescaleThreshold || mem / size / 100 < memDescaleThreshold) {
          dsCnt++;
          if (dsCnt > 120)
            manageService.descale(groupName);
        }
        else {
          dsCnt = 0;
        }
      }

      hbCnt++;
      hbCnt %= 100000;
    }
    catch (Exception e){
      e.printStackTrace();
    }
  }

  public void heartbeat(String groupName) {
    try {
      for (String url : urls.get(groupName)) {
        if (!heartbeating.containsKey(groupName)) {
          heartbeating.put(groupName, new HashSet<>());
          // create a task manager whole group

          Timer timer = new Timer("timer - " + url);
            timer.schedule(
                  new TimerTask() {
                    @SneakyThrows
                    @Override
                    public void run() {
                      manageServer(groupName);
                    }
                  },
                  10000,
                  1000);

            groupTimer.put(groupName, timer);
        }

        if (heartbeating.get(groupName).contains(url)) {
          continue;
        }

        heartbeating.get(groupName).add(url);

        // heartbeat timer
        Timer hbTimer = new Timer("timer - " + url);
        hbTimer.schedule(
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

                      frozen(groupName, url);
                    }
                  }
                },
                10000,
                1000);

          urlTimer.put(url, hbTimer);
      }

    } catch (Exception e) {
      System.out.println(e.getStackTrace());
    }
  }

  void frozen(String groupName, String ip){
    Timer tryTimer = new Timer("try to call frozen server " + ip);

    tryTimer.schedule(
        new java.util.TimerTask() {
          @SneakyThrows
          @Override
          public void run() {
            String ret = sendRequest(ip + heartbeatSuffix);
            if (ret.trim().equals(live)) {
              status.put(ip, true);
              manageService.deleteFromDead(groupName, ip);
            }
          }
        },
        10000,
        1000);

    upstreamServerGroups.serverGroup(groupName).deleteRunningServerUsingIP(ip);
    manageService.addToDead(groupName, ip, tryTimer);
    if (urlTimer.containsKey(ip)){
      urlTimer.get(ip).cancel();
    }
    urlTimer.remove(ip);

    if (heartbeating.containsKey(groupName) && heartbeating.get(groupName).contains(ip)){
      heartbeating.get(groupName).remove(ip);
    }
  }

  String sendRequest(String clientUrl) throws ExecutionException, InterruptedException {
    CompletableFuture<String> completableFuture =
        CompletableFuture.supplyAsync(
            () -> {
              URL url = null;
              String res = "";
              try {
                url = new URL(httpPrefix + clientUrl);
              } catch (MalformedURLException e) {
                e.printStackTrace();
              }

              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
                for (String line; (line = reader.readLine()) != null; ) {
                  //                  System.out.println(line);
                  res += line + " ";
                }
              }catch (Exception e) {
                System.out.println(e.getMessage());
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
  public String[][] getInfoData(String clientUrl) throws Exception {
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
