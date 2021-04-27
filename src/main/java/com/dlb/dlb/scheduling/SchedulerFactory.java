package com.dlb.dlb.scheduling;


public class SchedulerFactory {
    private static final String WRR = "wrr";
    private static final String IP_HASH = "ip_hash";

    public static Scheduler createScheduler(String policy) {
        policy = policy.toLowerCase();

        if (policy.equals(WRR)) {
            return new WeightedRoundRobinScheduler();
        }

        else if (policy.equals(IP_HASH)) {
            return new IPHashScheduler();
        }

        else {
            return new RoundRobinScheduler();
        }
    }
}
