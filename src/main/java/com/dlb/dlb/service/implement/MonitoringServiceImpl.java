package com.dlb.dlb.service.implement;

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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
public class MonitoringServiceImpl implements MonitoringService {

  static LinkedList<String> cpuData = new LinkedList<>();
  static LinkedList<String> memData = new LinkedList<>();
  static String live = "hello";
  static String heartbeatSuffix = "/hello";
  static String statusSuffix = "/cpu";
  int capacity = 10;
  HashMap<String, Boolean> status;
  HashSet<String> urls;
  HashSet<String> heartbeating;

  public MonitoringServiceImpl() throws ExecutionException, InterruptedException {
    status = new HashMap<>();
    urls = new HashSet<>();
    heartbeating = new HashSet<>();

    heartbeat();
  }

  public void addUrl(String url) throws ExecutionException, InterruptedException {
    urls.add(url);
    heartbeat();
  }

  public boolean onlineStatus(String clientUrl) {
    return status.getOrDefault(clientUrl, false);
  }

  public void heartbeat() throws ExecutionException, InterruptedException {
    for (String url : urls) {
      if (heartbeating.contains(url)) {
        continue;
      }

      heartbeating.add(url);
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

    if (cpuData.size() >= capacity) {
      cpuData.removeFirst();
    }
    cpuData.addLast(cpu);

    return parseListToStringArray(cpuData);
  }

  @Override
  public String[] getMemData(String clientUrl) throws Exception {
    String[] responseArr = sendRequest(clientUrl + statusSuffix).split(" ");
    if (responseArr.length != 3) {
      throw new Exception("info data incorrect format");
    }

    String memory = responseArr[2];

    if (memData.size() >= capacity) {
      memData.removeFirst();
    }
    memData.addLast(memory);

    return parseListToStringArray(memData);
  }

  @Override
  public String[][] getInfoData(String clientUrl) throws Exception{
    String[] responseArr = sendRequest(clientUrl + statusSuffix).split(" ");
    if (responseArr.length != 3) {
      throw new Exception("info data incorrect format");
    }

    String cpu = responseArr[1];

    if (cpuData.size() >= capacity) {
      cpuData.removeFirst();
    }
    cpuData.addLast(cpu);

    String memory = responseArr[2];

    if (memData.size() >= capacity) {
      memData.removeFirst();
    }
    memData.addLast(memory);

    String[][] ret = new String[2][capacity];

    ret[0] = parseListToStringArray(cpuData);
    ret[1] = parseListToStringArray(memData);

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
