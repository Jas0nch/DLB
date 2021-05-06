package com.dlb.dlb.service.implement;

import com.dlb.dlb.configration.DLBConfiguration;
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

  /**
   * Thread safe hash map to store recent CPU data of servers, key is ip address, value is a linked
   * list of data, the newest data is added at last of linked list
   */
  static ConcurrentHashMap<String, LinkedList<String>> cpuData = new ConcurrentHashMap<>();
  /**
   * Thread safe hash map to store recent Memory data of servers, key is ip address, value is a
   * linked list of data, the newest data is added at last of linked list
   */
  static ConcurrentHashMap<String, LinkedList<String>> memData = new ConcurrentHashMap<>();
  /** Default string to check if one server is alive */
  static String alive = "hello";
  /** Prefix for http */
  static String httpPrefix = "http://";
  /** Suffix for heartbeat url */
  static String heartbeatSuffix = ":8080/hello";
  /** Suffix for url to get cpu and memory data */
  static String statusSuffix = ":8080/cpu";
  /**
   * Use this to control the output log in Console test = 1 means monitoring feature, 2 means scale
   * feature
   */
  int test = DLBConfiguration.flag;
  /** Maximum size for CPU and Memory Data */
  int capacity = 10;
  /** Status hash map, key is ip address, value is if the server is alive */
  ConcurrentHashMap<String, Boolean> status;
  /**
   * URLs need to be monitoring (in active state), key is service group name, value is ip address
   * hash set
   */
  ConcurrentHashMap<String, HashSet<String>> urls;
  /** URLs already monitoring, key is service group name, value is ip address hash set */
  ConcurrentHashMap<String, HashSet<String>> heartbeating;

  /** Threshold to scale based on CPU usage, bigger than this value, then scale */
  double cpuScaleThreshold = DLBConfiguration.cpuScaleThreshold;
  /** Threshold to descale based on CPU usage, small than this value, then descale */
  double cpuDescaleThreshold = DLBConfiguration.cpuDescaleThreshold;
  /** Threshold to scale based on Memory usage, bigger than this value, then scale */
  double memScaleThreshold = DLBConfiguration.memScaleThreshold;
  /** Threshold to descale based on Memory usage, small than this value, then descale */
  double memDescaleThreshold = DLBConfiguration.memDescaleThreshold;

  /** Bean for ManageService */
  @Autowired ManageService manageService;

  /** Testing Server Configuration */
  @Autowired UpstreamServerGroups upstreamServerGroups;
  /**
   * Thread safe hash map to monitoring in group level, key is service group name, value is Timer
   * monitoring all active servers in that group, get cpu and memory usage every second.
   */
  ConcurrentHashMap<String, Timer> groupTimer = new ConcurrentHashMap<>();
  /**
   * Thread safe hash map to monitoring all server, key is ip address, value is Timer monitoring
   * active server, check if that server is alive
   */
  ConcurrentHashMap<String, Timer> urlTimer = new ConcurrentHashMap<>();
  /** heartbeat count, wait for a period of time then start to check if we should scale or not */
  int hbCnt = 0;
  /**
   * descale count, wait for a period of time to let the whole system in a stable state, then
   * descale.
   */
  int dsCnt = 0;
  /**
   * Scale count, wait for a period of time to let the whole system in a stable state, then scale.
   */
  int scaleCnt = 0;

  public MonitoringServiceImpl() throws ExecutionException, InterruptedException {
    status = new ConcurrentHashMap<>();
    urls = new ConcurrentHashMap<>();
    heartbeating = new ConcurrentHashMap<>();
  }

  /**
   * Add this server to be monitoring
   *
   * @param groupName service group name for that ip
   * @param url ip address for that server
   */
  public void addUrl(String groupName, String url) {
    if (!urls.containsKey(groupName)) urls.put(groupName, new HashSet<>());

    urls.get(groupName).add(url);

    cpuData.put(url, new LinkedList<>());
    memData.put(url, new LinkedList<>());

    heartbeat(groupName);
  }

  /**
   * heartbeat all running server in that group
   *
   * @param groupName service group name
   */
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
                if (ret.trim().equals(alive)) {
                  status.put(url, true);
                  if (test >= 2) {
                    System.out.println(LocalDateTime.now() + " " + url + " live");
                  }
                } else {
                  if (test >= 2) {
                    System.out.println(LocalDateTime.now() + " " + url + " dead");
                  }
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

  /**
   * Delete this server from monitoring list *
   *
   * @param groupName service group name for that ip
   * @param url ip address for that server
   */
  public void deleteUrl(String groupName, String url) {
    urls.get(groupName).remove(url);

    if (urls.get(groupName).size() == 0) {
      groupTimer.get(groupName).cancel();
      groupTimer.remove(groupName);
    }

    cpuData.remove(url);
    memData.remove(url);

    heartbeating.get(groupName).remove(url);
    stopHeartbeat(url);
  }

  /**
   * Stop heartbeat for a server
   *
   * @param url ip address
   */
  public void stopHeartbeat(String url) {
    if (urlTimer.containsKey(url)) {
      urlTimer.get(url).cancel();
      urlTimer.remove(url);
    }
  }

  /**
   * Monitoring the whole group, decide to scale or descale
   *
   * @param groupName service group name
   * @throws Exception
   */
  synchronized void manageServer(String groupName) throws Exception {
    try {
      double cpu = 0;
      double mem = 0;
      int size = heartbeating.get(groupName).size();
      for (String ip : heartbeating.get(groupName)) {
        // get cpu and memory data
        String[] responseArr = sendRequest(ip + statusSuffix).split(" ");
        if (responseArr.length != 3) {
          //          throw new Exception("info data incorrect format");
          if (test == 1) {
            System.out.println("info data incorrect format");
          }
          break;
        }
        cpu += Double.valueOf(responseArr[1]);
        mem += Double.valueOf(responseArr[2]);
      }

      // the server is running for 15 seconds
      if (hbCnt > 15) {
        if (cpu / size / 100 > cpuScaleThreshold || mem / size / 100 > memScaleThreshold) {
          scaleCnt++;
          // the system is in a stable state and should be scale for 10 seconds
          if (scaleCnt > 10) {
            manageService.scale(groupName);
            dsCnt = 0;
            scaleCnt = -20;
          }

        } else if (cpu / size / 100 < cpuDescaleThreshold
            || mem / size / 100 < memDescaleThreshold) {
          dsCnt++;
          // the system is in a stable state and should be descale for 120 seconds
          if (dsCnt > 120) {
            manageService.descale(groupName);
            scaleCnt = 0;
            dsCnt = -20;
          }
        } else {
          dsCnt = 0;
          scaleCnt = 0;
        }
      }

      hbCnt++;
      hbCnt %= 100000;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * if a server is down, should stop the heart-beating and try to wake
   *
   * @param groupName service group name
   * @param ip ip address
   */
  void frozen(String groupName, String ip) {
    Timer tryTimer = new Timer("try to wake frozen server " + ip);

    tryTimer.schedule(
        new java.util.TimerTask() {
          @SneakyThrows
          @Override
          public void run() {
            manageService.startNode(ip);
            String ret = sendRequest(ip + heartbeatSuffix);
            if (ret.trim().equals(alive)) {
              status.put(ip, true);
              manageService.deleteFromDead(groupName, ip);
            }
          }
        },
        10000,
        1000);

    // remove it to prevent further requests
    upstreamServerGroups.serverGroup(groupName).deleteRunningServerUsingIP(ip);
    manageService.addToDead(groupName, ip, tryTimer);

    // remove its timer
    if (urlTimer.containsKey(ip)) {
      urlTimer.get(ip).cancel();
    }
    urlTimer.remove(ip);

    if (heartbeating.containsKey(groupName) && heartbeating.get(groupName).contains(ip)) {
      heartbeating.get(groupName).remove(ip);
    }
  }

  /**
   * get the status of that server
   *
   * @param clientUrl ip address
   * @return alive or dead
   */
  public boolean onlineStatus(String clientUrl) {
    return status.getOrDefault(clientUrl, false);
  }

  /**
   * Send a request to a url
   *
   * @param clientUrl url needs to be visit
   * @return response for the request
   * @throws ExecutionException
   * @throws InterruptedException
   */
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
              } catch (Exception e) {
                System.out.println(e.getMessage());
              }

              //              System.out.println(
              //                  LocalDateTime.now().toString() + " query " + clientUrl + " return
              // " + res);
              return res;
            });

    while (!completableFuture.isDone()) {
      //      System.out.println("CompletableFuture is not finished yet...");
    }

    return completableFuture.get();
  }

  /**
   * Get most recent CPU data of size {@link capacity} from a server
   * @param clientUrl ip address
   * @return
   * @throws Exception
   */
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

  /**
   * Get most recent Memory data of size {@link capacity} from a server
   * @param clientUrl ip address
   * @return
   * @throws Exception
   */
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

  /**
   * Get most recent CPU and Memory data of size {@link capacity} from a server
   * @param clientUrl ip address
   * @return
   * @throws Exception
   */
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

  /**
   * A helper function to parse a list of data to a String array
   * @param data
   * @return
   */
  String[] parseListToStringArray(List<String> data) {
    String[] res = new String[data.size()];
    int idx = 0;
    for (String s : data) {
      res[idx++] = s;
    }

    return res;
  }
}
