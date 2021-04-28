package com.dlb.dlb.scheduling;


public class SchedulerFactory {
    private static final String WRR = "wrr";
    private static final String SOURCE_IP_HASH = "source_ip_hash";
    private static final String RR = "rr";

    public static Scheduler createScheduler(String policy) {
        policy = policy.toLowerCase();

        if (policy.equals(WRR)) {
            return new WeightedRoundRobinScheduler();
        }

        else if (policy.equals(SOURCE_IP_HASH)) {
            return new IPHashScheduler();
        }

        else if (policy.equals(RR)) {
            return new RoundRobinScheduler();
        }

        else {
            return new RoundRobinScheduler();
        }
    }
}
