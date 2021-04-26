package com.dlb.dlb.service.implement;

import com.dlb.dlb.service.ManageService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.stereotype.Service;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

@Service
public class ManageServiceImpl implements ManageService {

  static String imageName = "stangithubdocker/dlb-agent";

  static String containerName = "agent";

  String dockerDaemonPort = "2375";

  boolean useDocker = true;

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
    if (useDocker){
      return stopNodeUsingDocker(ip);
    }
    else {
      return stopNodeUsingK8s(ip);
    }
  }

  boolean stopNodeUsingDocker(String ip) throws Exception {
    try{
      DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://" + ip + ":2375").build();

      InspectContainerResponse inspectContainerResponse =
          dockerClient.inspectContainerCmd(containerName).exec();

      dockerClient.stopContainerCmd(inspectContainerResponse.getId()).withTimeout(2).exec();

      inspectContainerResponse =
          dockerClient.inspectContainerCmd(containerName).exec();

      if (inspectContainerResponse.getState().getRunning()) {
        return false;
      }
      return true;
    }
    catch (Exception e){
      System.out.println(e.getMessage());
      return false;
    }
  }

  boolean stopNodeUsingK8s(String ip) throws Exception{
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

  boolean startNodeUsingK8s(String ip) throws Exception {
    throw new NotImplementedException();
  }

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
