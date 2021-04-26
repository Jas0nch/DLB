package com.dlb.dlb.scheduling;


import com.dlb.dlb.configration.DLBConfiguration;

public class RoundRobinScheduler extends Scheduler {
    public RoundRobinScheduler() {
        super();
    }

    @Override
    public synchronized String schedule() {
        DLBConfiguration.UpstreamServer server = servers.get(index++);
        if (index == servers.size()) index = 0;

        return server.getServer();
    }
}
