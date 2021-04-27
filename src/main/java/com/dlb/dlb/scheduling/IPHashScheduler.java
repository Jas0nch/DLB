package com.dlb.dlb.scheduling;


import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Data
public class IPHashScheduler extends Scheduler {
    public IPHashScheduler() {
        super();
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
