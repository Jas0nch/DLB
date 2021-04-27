package com.dlb.dlb.scheduling;


import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Data
public class RoundRobinScheduler extends Scheduler {
    public RoundRobinScheduler() {
        super();
    }

    @Override
    public String schedule(ServerHttpRequest request) {
        synchronized (servers) {
            index %= servers.size();

            return servers.get(index++).getServer();
        }
    }
}
