package com.dlb.dlb.service;

public interface ManageService {
  boolean addNode();
  boolean deleteNode();
  boolean stopNode(String ip);
  boolean startNode(String ip);
}
