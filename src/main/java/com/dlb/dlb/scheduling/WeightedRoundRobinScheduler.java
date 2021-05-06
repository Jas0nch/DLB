package com.dlb.dlb.scheduling;

import com.dlb.dlb.configration.DLBConfiguration;
import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * The scheduler that uses the weighted Round-Robin load balancing algorithm.
 */
@Data
public class WeightedRoundRobinScheduler extends Scheduler {
    private int count;

    public WeightedRoundRobinScheduler() {
        super("Weighted Round Robin");
        count = 0;
    }

    @Override
    public String schedule(ServerHttpRequest request) {
        synchronized (servers) {
            int total = 0;

            for (DLBConfiguration.UpstreamServer server : servers) {
                total += server.getWeight();
            }

            count %= total;

            int temp = 0;

            index = 0;

            for (int i = 0; i < servers.size(); i++) {
                DLBConfiguration.UpstreamServer upstreamServer = servers.get(i);
                if (upstreamServer.getWeight() + temp > count) {
                    index = i;
                    break;
                } else {
                    temp += upstreamServer.getWeight();
                }
            }

            count++;

            return servers.get(index).getServer();
        }
    }
}
