package com.dlb.dlb.scheduling;


import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * The scheduler that uses the ip hashing load balancing algorithm.
 */
@Data
public class IPHashScheduler extends Scheduler {
    public IPHashScheduler() {
        super("Source IP Hashing");
    }

    @Override
    public String schedule(ServerHttpRequest request) {
        String clientIP = request.getRemoteAddress().getAddress().getHostAddress();

        int hashcode = clientIP.hashCode();

        synchronized (servers) {
            index = Math.abs(hashcode % servers.size());
            return servers.get(index).getServer();
        }
    }
}
