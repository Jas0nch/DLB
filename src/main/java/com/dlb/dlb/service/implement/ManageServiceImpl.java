package com.dlb.dlb.service.implement;

import com.dlb.dlb.configration.DLBConfiguration;
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

/** Manager docker container in testing server */
@Service
public class ManageServiceImpl implements ManageService {

  /** Image name for test server, loaded from config file */
  static String imageName = DLBConfiguration.imageName;
  /** Customized container name based on image */
  static String containerName = "client";
  /**
   * Use this to control the output log in Console test = 1 means monitoring feature, 2 means scale
   * feature
   */
  int test = DLBConfiguration.flag;
  /** Testing Server Configuration */
  UpstreamServerGroups upstreamServerGroups;

  /** Remote Docker daemon port */
  String dockerDaemonPort = DLBConfiguration.dockerDaemonPort;

  /** Use Docker daemon or K8s SDK */
  boolean useDocker = true;

  /** Thread safe hash map to awake dead server, key is ip, value is timer. */
  ConcurrentHashMap<String, Timer> deadTimer = new ConcurrentHashMap<>();

  /** Bean of Monitoring Service */
  MonitoringService monitoringService;

  //
  /** Wheel Timer to create delayed task */
  HashedWheelTimer timer = new HashedWheelTimer(1, TimeUnit.SECONDS, 100);

  /** buffer to store server which is in pending(descaling) state */
  ConcurrentHashMap<String, Timeout> descaleBuffer = new ConcurrentHashMap<>();

  /** buffer to store server which is in pending(scaling) state */
  ConcurrentHashMap<String, String> scaleBuffer = new ConcurrentHashMap<>();

  public ManageServiceImpl(
      UpstreamServerGroups upstreamServerGroups, MonitoringService monitoringService) {
    this.upstreamServerGroups = upstreamServerGroups;
    this.monitoringService = monitoringService;
    // start one testing server when launch DLB
    for (String groupName : upstreamServerGroups.getMap().keySet()) {
      scale(groupName);
    }
  }

  // region scale part

  /**
   * Scale one server for specific service group
   *
   * @param groupName The group name defined in config file
   * @return True means success, False means failure
   */
  @Override
  public synchronized boolean scale(String groupName) {
    List<UpstreamServer> allServers = upstreamServerGroups.serverGroup(groupName).getServers();
    List<UpstreamServer> runningServers =
        upstreamServerGroups.serverGroup(groupName).getRunningServers();

    // no resources to scale
    if (allServers.size() == runningServers.size() + deadTimer.size() + scaleBuffer.size()) {
      System.out.println("alert: full workload");
      return false;
    }

    // find one available server
    boolean res = false;
    for (UpstreamServer upstreamServer : allServers) {
      if (deadTimer.containsKey(upstreamServer.getHost())
          || scaleBuffer.containsKey(upstreamServer.getHost())) {
        continue;
      }

      boolean find = false;
      for (UpstreamServer run : runningServers) {
        if (upstreamServer.getHost().equals(run.getHost())) {
          find = true;
          break;
        }
      }

      if (!find) {
        // if this server is in pending(descale) state, revert it to active state
        if (descaleBuffer.containsKey(upstreamServer.getHost())) {
          descaleBuffer.get(upstreamServer.getHost()).cancel();
          descaleBuffer.remove(upstreamServer.getHost());
          res = true;
        } else {
          res = startNode(upstreamServer.getHost());
        }

        if (res) {
          if (test >= 2) {
            System.out.println(
                LocalDateTime.now() + " " + "start " + upstreamServer.getHost() + " successfully");
          }

          onStartSuccess(upstreamServer.getHost(), groupName);
        }
        break;
      }
    }

    return res;
  }

  /**
   * start a testing server
   *
   * @param ip ip address of that testing server
   * @return success or failure
   */
  @Override
  public boolean startNode(String ip) {
    if (useDocker) {
      return startNodeUsingDocker(ip);
    } else {
      return startNodeUsingK8s(ip);
    }
  }

  /**
   * start a testing server based on docker
   *
   * @param ip ip address of that testing server
   * @return success or failure
   */
  // TODO change this to configuration version
  boolean startNodeUsingDocker(String ip) {
    try {
      if (test >= 2) {
        System.out.println(LocalDateTime.now() + " " + "prepare to start node " + ip);
      }

      if (scaleBuffer.containsKey(ip)) {
        return false;
      }

      scaleBuffer.put(ip, "");

      DockerClient dockerClient =
          DockerClientBuilder.getInstance("tcp://" + ip + ":" + dockerDaemonPort).build();

      Info info = dockerClient.infoCmd().exec();

      dockerClient.pullImageCmd(imageName).start().awaitCompletion(30, TimeUnit.SECONDS);

      ExposedPort tcp8081 = ExposedPort.tcp(8081);
      ExposedPort tcp8000 = ExposedPort.tcp(8000);

      Ports portBindings = new Ports();
      portBindings.bind(tcp8081, Binding.bindPort(8080));
      portBindings.bind(tcp8000, Binding.bindPort(8000));

      try {
        InspectContainerResponse inspectContainerResponse =
            dockerClient.inspectContainerCmd(containerName).exec();
        if (inspectContainerResponse.getState().getRunning()) {
          dockerClient.stopContainerCmd(containerName).withTimeout(2).exec();
        }

        dockerClient.removeContainerCmd(containerName).exec();
      } catch (Exception e) {
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

  /**
   * change testing server state, add it to monitoring after start one server successfully
   *
   * @param ip ip address of that server
   * @param groupName service group name of that server
   */
  void onStartSuccess(String ip, String groupName) {
    // delay if the container is running but program is still preparing
    TimerTask task =
        new TimerTask() {
          @Override
          public void run(Timeout timeout) throws Exception {
            if (test >= 2) {
              System.out.println(LocalDateTime.now() + " " + ip + " added to running");
            }
            scaleBuffer.remove(ip);
            upstreamServerGroups.serverGroup(groupName).addRunningServerUsingIP(ip);
            monitoringService.addUrl(groupName, ip);
          }
        };

    timer.newTimeout(task, 10, TimeUnit.SECONDS);
  }

  // endregion

  // region descale part

  /**
   * Descale a server within that service group after 10 seconds
   *
   * @param groupName service group name
   * @return Success or failure
   * @throws Exception
   */
  @Override
  public synchronized boolean descale(String groupName) throws Exception {
    List<UpstreamServer> running = upstreamServerGroups.serverGroup(groupName).getRunningServers();

    if (running.size() <= 1) {
      return false;
    }

    UpstreamServer chosen = running.get(0);

    System.out.println("stopping " + chosen.getHost());
    // change server state to pending(descaling)
    monitoringService.deleteUrl(groupName, chosen.getHost());
    upstreamServerGroups.serverGroup(groupName).deleteRunningServer(0);

    // stop it after 10s to finish existing requests in that server
    TimerTask task =
        new TimerTask() {
          @Override
          public void run(Timeout timeout) throws Exception {
            if (test >= 2) {
              System.out.println(
                  LocalDateTime.now() + " " + "stopping node in " + chosen.getHost());
            }
            stopNode(chosen.getHost());
          }
        };

    Timeout timeout = timer.newTimeout(task, 10, TimeUnit.SECONDS);
    descaleBuffer.put(chosen.getHost(), timeout);

    return true;
  }

  /**
   * Stop a running container on testing server
   *
   * @param ip ip address for that server
   * @return Success or failure
   * @throws Exception
   */
  @Override
  public boolean stopNode(String ip) throws Exception {
    if (useDocker) {
      return stopNodeUsingDocker(ip);
    } else {
      return stopNodeUsingK8s(ip);
    }
  }

  /**
   * Stop a running container on testing server using Docker
   *
   * @param ip ip address for that server
   * @return Success or failure
   */
  boolean stopNodeUsingDocker(String ip) {
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

  // endregion

  // region awake dead server

  /**
   * Add one server to dead list
   *
   * @param groupName service group name for that server
   * @param ip ip address for that server
   * @param t Timer to check if that server is alive
   */
  public void addToDead(String groupName, String ip, Timer t) {
    deadTimer.put(ip, t);
  }

  /**
   * Delete a server from dead list
   *
   * @param groupName service group name
   * @param ip ip address
   */
  public void deleteFromDead(String groupName, String ip) {
    if (deadTimer.containsKey(ip)) {
      deadTimer.get(ip).cancel();
      deadTimer.remove(ip);
      onStartSuccess(ip, groupName);
    }
  }

  // endregion

  // region useless

  @Override
  public boolean addNode() {
    return false;
  }

  @Override
  public boolean deleteNode() {
    return false;
  }

  boolean stopNodeUsingK8s(String ip) throws Exception {
    return false;
  }

  boolean startNodeUsingK8s(String ip) {
    return false;
  }

  //endregion
}
