package com.dlb.dlb.scheduling;


public class SchedulerFactory {
    public static Scheduler createScheduler(String policy) {
        return new RoundRobinScheduler();
    }
}
