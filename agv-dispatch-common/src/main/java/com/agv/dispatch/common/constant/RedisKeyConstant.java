package com.agv.dispatch.common.constant;

public class RedisKeyConstant {

    public static final String TASK_QUEUE_KEY = "agv:task:queue";
    public static final String TASK_PREFIX = "agv:task:";
    public static final String AGV_STATUS_PREFIX = "agv:robot:status:";
    public static final String AGV_POSITION_PREFIX = "agv:robot:position:";
    public static final String PATH_OCCUPY_PREFIX = "agv:path:occupy:";
    public static final String CONFLICT_KEY = "agv:conflict:current";
    public static final String DISPATCH_LOCK_KEY = "agv:dispatch:lock";
    public static final String HEARTBEAT_PREFIX = "agv:heartbeat:";

    public static final String NODE_LOCK_PREFIX = "agv:node:lock:";
    public static final String INTERSECTION_LOCK_PREFIX = "agv:intersection:lock:";
    public static final String DEADLOCK_KEY = "agv:deadlock:current";
    public static final String DEADLOCK_DETECTION_LOCK = "agv:deadlock:lock";
    public static final String AGV_WAITING_FOR_PREFIX = "agv:waiting:for:";
    public static final String PATH_BLOCKED_PREFIX = "agv:path:blocked:";

    public static final long TASK_CACHE_SECONDS = 3600;
    public static final long AGV_STATUS_SECONDS = 300;
    public static final long PATH_OCCUPY_SECONDS = 60;
    public static final long DISPATCH_LOCK_SECONDS = 10;
    public static final long NODE_LOCK_SECONDS = 30;
    public static final long INTERSECTION_LOCK_SECONDS = 20;
    public static final long DEADLOCK_LOCK_SECONDS = 30;
    public static final long WAITING_STATUS_SECONDS = 120;
    public static final long PATH_BLOCKED_SECONDS = 180;
}
