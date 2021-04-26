package com.dlb.dlb.configration;

import com.dlb.dlb.scheduling.Scheduler;
import com.dlb.dlb.scheduling.SchedulerFactory;
import com.dlb.dlb.service.ManageService;
import lombok.Data;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.util.*;

@Configuration
public class DLBConfiguration {
    private static Map<String, Object> map;

    {
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();

        yaml.setResources(new FileSystemResource("../config/dlb.yml"));

        map = yaml.getObject();
    }

    @Bean
    @SuppressWarnings("all")
    public UpstreamServerGroups upstreamServerGroups(ManageService manageService) {
        String policy = (String) map.getOrDefault("policy", "rr");

        UpstreamServerGroups serverGroups = new UpstreamServerGroups();

        List upstream = (List) map.get("up-stream");

        for (Object o : upstream) {
            LinkedHashMap upstreamMap = (LinkedHashMap) o;
            String name = (String) upstreamMap.get("name");

            List<UpstreamServer> servers = new ArrayList<>();

            List serversList = (List) upstreamMap.get("servers");

            for (Object o1 : serversList) {
                LinkedHashMap serverMap = (LinkedHashMap) o1;
                String serverName = (String) serverMap.get("server");
                int serverWeight = (Integer) serverMap.getOrDefault("weight", 1);

                UpstreamServer server = new UpstreamServer(serverName, serverWeight);

                servers.add(server);
            }

            List<UpstreamServer> runningServers = new ArrayList<>();

            for (UpstreamServer server : servers) {
                if (manageService.startNode(server.getHost())) {
                    runningServers.add(server);
                    break;
                }
            }

            UpstreamServerGroup serverGroup = new UpstreamServerGroup(name, servers, runningServers, SchedulerFactory.createScheduler(policy));

            serverGroups.addGroup(serverGroup);
        }


        return serverGroups;
    }

    @Bean
    @SuppressWarnings("all")
    public URIMapping uriMapping() {
        URIMapping uriMapping = new URIMapping();

        List mappingList = (List) map.get("location");

        for (Object o : mappingList) {
            LinkedHashMap locationMap = (LinkedHashMap) o;
            String path = (String) locationMap.get("path");
            String proxy = (String) locationMap.get("proxy_pass");

            uriMapping.addEntry(path, proxy);

        }

        return uriMapping;
    }


    public static class UpstreamServerGroups {
        private Map<String, UpstreamServerGroup> map = new HashMap<>();

        public UpstreamServerGroups() {}

        public void addGroup(UpstreamServerGroup serverGroup) {
            map.put(serverGroup.getName(), serverGroup);
        }

        public UpstreamServerGroup serverGroup(String groupName) {
            return map.get(groupName);
        }
    }

    @Data
    public static class UpstreamServerGroup {
        private String name;
        private List<UpstreamServer> servers;
        private List<UpstreamServer> runningServers;
        private Scheduler scheduler;

        public String taskServer() {
            return scheduler.schedule();
        }

        public UpstreamServerGroup(String name, List<UpstreamServer> servers, List<UpstreamServer> runningServers, Scheduler scheduler) {
            this.name = name;
            scheduler.setServers(runningServers);
            this.servers = servers;
            this.scheduler = scheduler;
            this.runningServers = runningServers;
        }
    }

    @Data
    public static class UpstreamServer {
        private String server;
        private String host;
        private int port;
        private int weight;

        public UpstreamServer(String server, int weight) {
            this.server = server;
            this.weight = weight;
            String[] split = server.split(":");
            this.host = split[0];
            this.port = Integer.parseInt(split[1]);
        }
    }


    public static class URIMapping {
        private Map<String, String> map = new HashMap<>();

        public URIMapping() {}

        public Set<String> allUris() {
            return map.keySet();
        }

        public String groupName(String path) {
            return map.get(path);
        }

        public void addEntry(String path, String proxy) {
            map.put(path, proxy);
        }
    }
}