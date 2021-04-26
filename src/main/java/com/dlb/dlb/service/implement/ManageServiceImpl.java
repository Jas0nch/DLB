package com.dlb.dlb.service.implement;

import com.dlb.dlb.service.ManageService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DockerClientBuilder;
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

  @Override
  public boolean startNode(String ip) {
    DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://" + host + ":2375").build();
    Info info = dockerClient.infoCmd().exec();
    System.out.println(info);
    return false;
  }
}
