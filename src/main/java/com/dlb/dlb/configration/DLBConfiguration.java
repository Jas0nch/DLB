package com.dlb.dlb.configration;

import com.dlb.dlb.scheduling.Scheduler;
import com.dlb.dlb.scheduling.SchedulerFactory;
import lombok.Data;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.*;

/**
 * This class will read the YML configuration file in the relative directory "../config/dlb.yml" and create corresponding
 * Java beans for the use of other modules.
 */
@Configuration
public class DLBConfiguration {
    /**
     * This map contains all configuration options specified in the configuration file: "../config/dlb.yml".
     */
    public static Map<String, Object> map;

    /**
     * The test flag for developer to use.
     */
    public static int flag;

    /**
     * The value specified by the scale.image_name attribute in the configuration file "../config/dlb.yml".
     */
    public static String imageName;

    /**
     * The value specified by the scale.remote_docker_daemon_port attribute in the configuration file "../config/dlb.yml".
     */
    public static String dockerDaemonPort;

    /**
     * The value specified by the scale.cpu_metrics_threshold.up attribute in the configuration file "../config/dlb.yml".
     */
    public static double cpuScaleThreshold;

    /**
     * The value specified by the scale.cpu_metrics_threshold.down attribute in the configuration file "../config/dlb.yml".
     */
    public static double cpuDescaleThreshold;

    /**
     * The value specified by the scale.memory_metrics_threshold.up attribute in the configuration file "../config/dlb.yml".
     */
    public static double memScaleThreshold;

    /**
     * The value specified by the scale.memory_metrics_threshold.down attribute in the configuration file "../config/dlb.yml".
     */
    public static double memDescaleThreshold;

    {
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();

        yaml.setResources(new FileSystemResource("../config/dlb.yml"));

        map = yaml.getObject();

        flag = (Integer) map.getOrDefault("test", 0);

        LinkedHashMap scaleMap = (LinkedHashMap) map.get("scale");

        imageName = (String) scaleMap.get("image_name");
        dockerDaemonPort = String.valueOf(scaleMap.get("remote_docker_daemon_port"));

        LinkedHashMap cpuThresholdMap = (LinkedHashMap) scaleMap.get("cpu_metrics_threshold");
        cpuScaleThreshold = (double) cpuThresholdMap.get("up");
        cpuDescaleThreshold = (double) cpuThresholdMap.get("down");

        LinkedHashMap memoryThresholdMap = (LinkedHashMap) scaleMap.get("memory_metrics_threshold");
        memScaleThreshold = (double) memoryThresholdMap.get("up");
        memDescaleThreshold = (double) memoryThresholdMap.get("down");
    }

    /**
     * The method will use the values specified by the up-stream attribute to create corrensponding Java bean.
     * @return
     * @throws Exception
     */
    @Bean
    @SuppressWarnings("all")
    public UpstreamServerGroups upstreamServerGroups() throws Exception {
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


//            for (UpstreamServer server : servers) {
//                if (manageService.startNode(server.getHost())) {
//                    runningServers.add(server);
//                    break;
//                }
//            }

            UpstreamServerGroup serverGroup = new UpstreamServerGroup(name, servers, SchedulerFactory.createScheduler(policy));

            serverGroups.addGroup(serverGroup);
        }


        return serverGroups;
    }


    /**
     * This method will use the value specified by the location attribute to create the corresponding Java Bean.
     * @return
     */
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


    /**
     * This class corresponds to the up-stream attribute in the configuration file "../config/dlb.yml", indicating a list
     * of upstream service groups.
     */
    @Data
    public static class UpstreamServerGroups {
        private Map<String, UpstreamServerGroup> map = new HashMap<>();

        public UpstreamServerGroups() {}

        public void addGroup(UpstreamServerGroup serverGroup) {
            map.put(serverGroup.getName(), serverGroup);
        }

        public UpstreamServerGroup serverGroup(String groupName) {
            return map.get(groupName);
        }

        public Set<String> allGroupNames() {
            return map.keySet();
        }
    }

    /**
     * This class represents a certain upstream service group.
     */
    @Data
    public static class UpstreamServerGroup {
        /**
         * The name of this upstream service group.
         */
        private String name;

        /**
         * A list of all servers undertaking this kind of service.
         */
        private List<UpstreamServer> servers;

        /**
         * A list of running servers to which the DLB can forward the corresponding requests at present.
         */
        private List<UpstreamServer> runningServers = new ArrayList<>();

        /**
         * Represents the LB algorithm specified by the policy attribute in the configuration file "../config/yml".
         */
        private Scheduler scheduler;


        /**
         * This method will determine the upstream server that handles the incoming request indeed.
         * @param request
         * @return
         */
        public String taskServer(ServerHttpRequest request) {
            return scheduler.schedule(request);
        }

        public UpstreamServerGroup(String name, List<UpstreamServer> servers, Scheduler scheduler) {
            this.name = name;
            scheduler.setServers(runningServers);
            this.servers = servers;
            this.scheduler = scheduler;
        }

        /**
         * When DLB decides to start and add a new server into the running server list, the Manage module will call this
         * method.
         * @param upstreamServer
         */
        public void addRunningServer(UpstreamServer upstreamServer){
            synchronized (runningServers) {
                runningServers.add(upstreamServer);
            }
        }

        /**
         * When DLB decides to remove and stop an idle server, the Manage module will call this method.
         * @param index
         */
        public void deleteRunningServer(int index){
            runningServers.remove(index);
        }

        public void deleteRunningServerUsingIP(String ip){
            int idx = 0;
            for (UpstreamServer upstreamServer:runningServers){
                if (upstreamServer.getHost().equals(ip)){
                    break;
                }
                idx++;
            }

            if (idx != runningServers.size())
                deleteRunningServer(idx);
        }

        public void addRunningServerUsingIP(String ip){
            UpstreamServer chosen = null;
            for (UpstreamServer upstreamServer:servers){
                if (upstreamServer.getHost().equals(ip)){
                    chosen = upstreamServer;
                    break;
                }
            }

            if (chosen != null){
                addRunningServer(chosen);
            }
        }
    }


    /**
     * This class indicates a single upstream server, including its ip address, port number and scheduling weight.
     */
    @Data
    public static class UpstreamServer {
        /**
         * The full server name.
         */
        private String server;

        /**
         * The hostname or ip address of this server.
         */
        private String host;

        /**
         * The port number of this server.
         */
        private int port;

        /**
         * The weight that is specified in the configuration file "../config/dlb.yml".
         */
        private int weight;

        public UpstreamServer(String server, int weight) {
            this.server = server;
            this.weight = weight;
            String[] split = server.split(":");
            this.host = server.substring(0, server.length() - split[split.length - 1].length() - 1);
            this.port = Integer.parseInt(split[split.length - 1]);
        }
    }

    /**
     * This class represents the mappings from the URIs to the names of the upstream server groups.
     */
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
