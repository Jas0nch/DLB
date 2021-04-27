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
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.core.DockerClientBuilder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

@Service
public class ManageServiceImpl implements ManageService {

  static String imageName = "stangithubdocker/dlb-agent";
  static String containerName = "agent";
  @Autowired UpstreamServerGroups upstreamServerGroups;
  String dockerDaemonPort = "2375";

  boolean useDocker = true;

  @Autowired
  MonitoringService monitoringService;

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

  boolean stopNodeUsingDocker(String ip) throws Exception {
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
      System.out.println(e.getMessage());
      return false;
    }
  }

  boolean stopNodeUsingK8s(String ip) throws Exception {
    throw new NotImplementedException();
  }

  @Override
  public boolean startNode(String ip) throws Exception {
    if (useDocker) {
      return startNodeUsingDocker(ip);
    } else {
      return startNodeUsingK8s(ip);
    }
  }

  @Override
  public synchronized boolean scale(String groupName) throws Exception {
    List<UpstreamServer> all = upstreamServerGroups.serverGroup(groupName).getServers();
    List<UpstreamServer> running = upstreamServerGroups.serverGroup(groupName).getRunningServers();
    boolean res = false;
    for (UpstreamServer upstreamServer : all) {
      boolean find = false;
      for (UpstreamServer run : running) {
        if (upstreamServer.equals(run)) {
          find = true;
          break;
        }
      }

      if (!find) {
        res = startNode(upstreamServer.getHost());
        if (res) {
          upstreamServerGroups.serverGroup(groupName).addRunningServer(upstreamServer);
          monitoringService.addUrl(groupName, upstreamServer.getHost());
        }
        break;
      }
    }

    return res;
  }

  // stop server after 10 seconds
  @Override
  public synchronized boolean descale(String groupName) throws Exception {
    List<UpstreamServer> running = upstreamServerGroups.serverGroup(groupName).getRunningServers();

    if (running.size() == 0) {
      return false;
    }

    UpstreamServer chosen = running.get(0);
    upstreamServerGroups.serverGroup(groupName).deleteRunningServer(0);

    // create delayed task
    HashedWheelTimer timer =
        new HashedWheelTimer(
            10,
            TimeUnit.SECONDS,
            100);

    TimerTask task =
        new TimerTask() {
          @Override
          public void run(Timeout timeout) throws Exception {
            stopNode(chosen.getHost());
            System.out.println("run tasks" + " ，time：" + LocalDateTime.now());
          }
        };

    timer.newTimeout(task, 0, TimeUnit.SECONDS);

    return false;
  }

  boolean startNodeUsingK8s(String ip) throws Exception {
    throw new NotImplementedException();
  }

  // TODO change this to configuration version
  boolean startNodeUsingDocker(String ip) throws Exception {
    try {
      DockerClient dockerClient =
          DockerClientBuilder.getInstance("tcp://" + ip + ":" + dockerDaemonPort).build();

      Info info = dockerClient.infoCmd().exec();

      dockerClient.pullImageCmd(imageName);

      ExposedPort tcp8081 = ExposedPort.tcp(8081);

      Ports portBindings = new Ports();
      portBindings.bind(tcp8081, Binding.bindPort(8080));

      CreateContainerResponse container =
          dockerClient
              .createContainerCmd(imageName)
              .withCmd("-d")
              .withName(containerName)
              .withExposedPorts(tcp8081)
              .withHostConfig(new HostConfig().withPortBindings(portBindings))
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
}
