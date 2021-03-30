package com.dlb.dlb.service;

import java.util.concurrent.ExecutionException;

public interface MonitoringService {
  String[] getCpuData(String clientUrl) throws ExecutionException, InterruptedException;
  boolean onlineStatus(String clientUrl);
  void addUrl(String url) throws ExecutionException, InterruptedException;
}