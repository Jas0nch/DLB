package com.dlb.dlb.service;

import java.util.concurrent.ExecutionException;

public interface MonitoringService {
  String[] getCpuData(String clientUrl) throws Exception;
  String[] getMemData(String clientUrl) throws Exception;
  String[][] getInfoData(String clientUrl) throws Exception;
  boolean onlineStatus(String clientUrl);
  void addUrl(String groupName, String url) throws ExecutionException, InterruptedException;
}