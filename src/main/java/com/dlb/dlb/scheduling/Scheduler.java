package com.dlb.dlb.scheduling;


import com.dlb.dlb.configration.DLBConfiguration;
import lombok.Data;

import java.util.List;

@Data
public abstract class Scheduler {
    protected List<DLBConfiguration.UpstreamServer> servers;
    protected int index = 0;

    public Scheduler() {}

    public abstract String schedule();
}
