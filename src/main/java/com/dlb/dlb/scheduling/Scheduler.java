package com.dlb.dlb.scheduling;


import com.dlb.dlb.configration.DLBConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.ArrayList;
import java.util.List;

@Data
public abstract class Scheduler {
    protected List<DLBConfiguration.UpstreamServer> servers = new ArrayList<>();
    protected int index = 0;
    private String name;

    public Scheduler() {}

    public Scheduler(String name) {
        this.name = name;
    }

    public abstract String schedule(ServerHttpRequest request);
}
