package com.dlb.dlb.scheduling;


import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * The scheduler that uses the Round-Robin load balancing algorithm.
 */
@Data
public class RoundRobinScheduler extends Scheduler {
    public RoundRobinScheduler() {
        super("Round Robin");
    }

    @Override
    public String schedule(ServerHttpRequest request) {
        synchronized (servers) {
            index %= servers.size();

            return servers.get(index++).getServer();
        }
    }
}
