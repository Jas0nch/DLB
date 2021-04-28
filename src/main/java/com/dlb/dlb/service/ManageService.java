package com.dlb.dlb.service;

import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

public interface ManageService {
  boolean addNode();
  boolean deleteNode();
  boolean stopNode(String ip) throws Exception;
  boolean startNode(String ip) throws Exception;
  boolean scale(String groupName) throws Exception;
  boolean descale(String groupName) throws Exception;
  void addToDead(String groupName, String ip, Timer t);
  void deleteFromDead(String groupName, String ip);
}
