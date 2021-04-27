package com.dlb.dlb.service;

public interface ManageService {
  boolean addNode();
  boolean deleteNode();
  boolean stopNode(String ip) throws Exception;
  boolean startNode(String ip) throws Exception;
  boolean scale(String groupName) throws Exception;
  boolean descale(String groupName) throws Exception;
}
