package com.dlb.dlb.service.implement;

import com.dlb.dlb.configration.DLBConfiguration.UpstreamServer;
import com.dlb.dlb.configration.DLBConfiguration.UpstreamServerGroups;
import com.dlb.dlb.service.ManageService;
import com.dlb.dlb.service.MonitoringService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.core.DockerClientBuilder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class ManageServiceImpl implements ManageService {

  static String imageName = "stangithubdocker/dlb-client";
  static String containerName = "client";

  UpstreamServerGroups upstreamServerGroups;
  String dockerDaemonPort = "2375";

  boolean useDocker = true;

  MonitoringService monitoringService;

  public ManageServiceImpl(UpstreamServerGroups upstreamServerGroups, MonitoringService monitoringService){
    this.upstreamServerGroups = upstreamServerGroups;
    this.monitoringService = monitoringService;
    for (String groupName:upstreamServerGroups.getMap().keySet()){
      scale(groupName);
    }
  }


  @Override
  public boolean addNode() {
    return false;
  }

  @Override
  public boolean deleteNode() {
    return false;
  }

  @Override
  public boolean stopNode(String ip) throws Exception {
    if (useDocker) {
      return stopNodeUsingDocker(ip);
    } else {
      return stopNodeUsingK8s(ip);
    }
  }

  boolean stopNodeUsingDocker(String ip){
    try {
      DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://" + ip + ":2375").build();

      InspectContainerResponse inspectContainerResponse =
          dockerClient.inspectContainerCmd(containerName).exec();

      dockerClient.stopContainerCmd(inspectContainerResponse.getId()).withTimeout(2).exec();

      inspectContainerResponse = dockerClient.inspectContainerCmd(containerName).exec();

      if (inspectContainerResponse.getState().getRunning()) {
        return false;
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  boolean stopNodeUsingK8s(String ip) throws Exception {
    return false;
  }

  @Override
  public boolean startNode(String ip){
    if (useDocker) {
      return startNodeUsingDocker(ip);
    } else {
      return startNodeUsingK8s(ip);
    }
  }

  @Override
  public synchronized boolean scale(String groupName){
    List<UpstreamServer> all = upstreamServerGroups.serverGroup(groupName).getServers();
    List<UpstreamServer> running = upstreamServerGroups.serverGroup(groupName).getRunningServers();
    boolean res = false;
    for (UpstreamServer upstreamServer : all) {
      if (deadTimer.containsKey(upstreamServer.getHost())){
        continue;
      }

      boolean find = false;
      for (UpstreamServer run : running) {
        if (upstreamServer.getHost().equals(run.getHost())) {
          find = true;
          break;
        }
      }

      if (!find) {
        if (buffer.containsKey(upstreamServer.getHost())){
          buffer.get(upstreamServer.getHost()).cancel();
          buffer.remove(upstreamServer.getHost());
          res = true;
        }
        else {
          res = startNode(upstreamServer.getHost());
        }

        if (res) {
          System.out.println("starting " + upstreamServer.getHost());

          TimerTask task =
              new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                  System.out.println(upstreamServer.getHost() + " added to running");
                  upstreamServerGroups.serverGroup(groupName).addRunningServer(upstreamServer);
                  monitoringService.addUrl(groupName, upstreamServer.getHost());
                }
              };

          timer.newTimeout(task, 10, TimeUnit.SECONDS);
        }
        break;
      }
    }

    return res;
  }

  // create delayed task
  HashedWheelTimer timer =
      new HashedWheelTimer(
          1,
          TimeUnit.SECONDS,
          100);

  ConcurrentHashMap<String, Timeout> buffer = new ConcurrentHashMap<>();

  // stop server after 10 seconds
  @Override
  public synchronized boolean descale(String groupName) throws Exception {
    List<UpstreamServer> running = upstreamServerGroups.serverGroup(groupName).getRunningServers();

    if (running.size() <= 1) {
      return false;
    }

    UpstreamServer chosen = running.get(0);

    System.out.println("stopping " + chosen.getHost());
    monitoringService.deleteUrl(groupName, chosen.getHost());
    upstreamServerGroups.serverGroup(groupName).deleteRunningServer(0);
    TimerTask task =
        new TimerTask() {
          @Override
          public void run(Timeout timeout) throws Exception {
            stopNode(chosen.getHost());
            System.out.println("run tasks" + " ，time：" + LocalDateTime.now());
          }
        };

    Timeout timeout = timer.newTimeout(task, 10, TimeUnit.SECONDS);
    buffer.put(chosen.getHost(), timeout);
    return false;
  }

  boolean startNodeUsingK8s(String ip){
    return false;
  }

  // TODO change this to configuration version
  boolean startNodeUsingDocker(String ip){
    try {
      System.out.println("Starting node in " + ip);
      DockerClient dockerClient =
          DockerClientBuilder.getInstance("tcp://" + ip + ":" + dockerDaemonPort).build();

      Info info = dockerClient.infoCmd().exec();

      dockerClient.pullImageCmd(imageName)
          .start()
          .awaitCompletion(30, TimeUnit.SECONDS);

      ExposedPort tcp8081 = ExposedPort.tcp(8081);
      ExposedPort tcp8000 = ExposedPort.tcp(8000);

      Ports portBindings = new Ports();
      portBindings.bind(tcp8081, Binding.bindPort(8080));
      portBindings.bind(tcp8000, Binding.bindPort(8000));

      try{
        dockerClient.removeContainerCmd(containerName).exec();
      }
      catch (Exception e){
        System.out.println("remove error");
      }

      CreateContainerResponse container =
          dockerClient
              .createContainerCmd(imageName)
              .withName(containerName)
              .withExposedPorts(tcp8081)
              .withHostConfig(new HostConfig().withPortBindings(portBindings))
              .withCmd("/bin/sh")
              .withStdinOpen(true)
              .withTty(true)
              .withLinks(new Link("es", "es"))
              .exec();

      dockerClient.startContainerCmd(container.getId()).exec();

      InspectContainerResponse inspectContainerResponse =
          dockerClient.inspectContainerCmd(containerName).exec();

      if (inspectContainerResponse.getState().getRunning()) {
        return true;
      }

      return false;
    } catch (Exception e) {
      System.out.println(e.getMessage());
      return false;
    }
  }

  ConcurrentHashMap<String, Timer> deadTimer = new ConcurrentHashMap<>();

  public void addToDead(String groupName, String ip, Timer t){
    deadTimer.put(ip, t);
  }

  public void deleteFromDead(String groupName, String ip){
    if (deadTimer.containsKey(ip)){
      deadTimer.get(ip).cancel();
      deadTimer.remove(ip);
    }
  }

}
