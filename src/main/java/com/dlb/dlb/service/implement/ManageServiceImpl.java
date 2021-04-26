package com.dlb.dlb.service.implement;

import com.dlb.dlb.service.ManageService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.CreateContainerCmdImpl;
import org.springframework.stereotype.Service;

@Service
public class ManageServiceImpl implements ManageService {

  String host = "3.230.127.51";
  @Override
  public boolean addNode() {
    return false;
  }

  @Override
  public boolean deleteNode() {
    return false;
  }

  @Override
  public boolean stopNode(String ip) {
    return false;
  }

  static String imageName = "stangithubdocker/dlb-agent";

  @Override
  public boolean startNode(String ip) {
    DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://" + host + ":2375").build();
    Info info = dockerClient.infoCmd().exec();

    dockerClient.pullImageCmd(imageName);

    ExposedPort tcp8081 = ExposedPort.tcp(8081);

    Ports portBindings = new Ports();
    portBindings.bind(tcp8081, Binding.bindPort(8080));

    CreateContainerResponse container = dockerClient.createContainerCmd(imageName).withCmd("-d")
        .withExposedPorts(tcp8081)
        .withHostConfig(new HostConfig()
            .withPortBindings(portBindings)).exec();

    dockerClient.startContainerCmd(container.getId()).exec();

//    System.out.println(info);
    return true;
  }
}
