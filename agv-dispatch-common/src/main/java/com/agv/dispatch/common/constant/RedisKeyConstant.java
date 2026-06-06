package com.agv.dispatch.common.constant;

/**
 * Redis键常量类
 * 定义系统中所有Redis缓存的键名规范及过期时间
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
public class RedisKeyConstant {

    /**
     * 待分配任务队列键
     * 存储等待分配的任务列表
     */
    public static final String TASK_QUEUE_KEY = "agv:task:queue";

    /**
     * 任务信息缓存前缀
     * 存储任务的详细信息
     */
    public static final String TASK_PREFIX = "agv:task:";

    /**
     * AGV状态缓存前缀
     * 存储AGV的实时状态信息
     */
    public static final String AGV_STATUS_PREFIX = "agv:robot:status:";

    /**
     * AGV位置缓存前缀
     * 存储AGV的实时位置信息
     */
    public static final String AGV_POSITION_PREFIX = "agv:robot:position:";

    /**
     * 路径占用缓存前缀
     * 存储AGV当前占用的路径节点信息
     */
    public static final String PATH_OCCUPY_PREFIX = "agv:path:occupy:";

    /**
     * 当前冲突信息键
     * 存储当前检测到的冲突信息
     */
    public static final String CONFLICT_KEY = "agv:conflict:current";

    /**
     * 调度锁键
     * 用于防止调度逻辑并发执行
     */
    public static final String DISPATCH_LOCK_KEY = "agv:dispatch:lock";

    /**
     * 心跳缓存前缀
     * 存储AGV的心跳时间戳
     */
    public static final String HEARTBEAT_PREFIX = "agv:heartbeat:";

    /**
     * 节点锁前缀
     * 用于锁定地图节点，防止多台AGV同时占用同一节点
     */
    public static final String NODE_LOCK_PREFIX = "agv:node:lock:";

    /**
     * 路口锁前缀
     * 用于锁定路口节点，防止多台AGV同时进入同一路口
     */
    public static final String INTERSECTION_LOCK_PREFIX = "agv:intersection:lock:";

    /**
     * 当前死锁信息键
     * 存储当前检测到的死锁信息
     */
    public static final String DEADLOCK_KEY = "agv:deadlock:current";

    /**
     * 死锁检测锁键
     * 用于防止死锁检测逻辑并发执行
     */
    public static final String DEADLOCK_DETECTION_LOCK = "agv:deadlock:lock";

    /**
     * AGV等待对象前缀
     * 存储AGV正在等待的对象（节点、AGV等）
     */
    public static final String AGV_WAITING_FOR_PREFIX = "agv:waiting:for:";

    /**
     * 路径阻塞前缀
     * 存储被阻塞的路径信息
     */
    public static final String PATH_BLOCKED_PREFIX = "agv:path:blocked:";

    /**
     * 任务调度锁前缀
     * 用于防止同一任务被重复调度
     * 过期时间：60秒
     */
    public static final String TASK_DISPATCH_LOCK_PREFIX = "task:dispatch:lock:";

    /**
     * 任务执行信息前缀
     * 存储任务执行过程中的实时状态
     * 过期时间：3600秒
     */
    public static final String TASK_EXECUTION_PREFIX = "task:execution:";

    /**
     * AGV当前任务前缀
     * 存储AGV当前正在执行的任务ID
     * 过期时间：3600秒
     */
    public static final String AGV_CURRENT_TASK_PREFIX = "agv:current:task:";

    /**
     * 节点占用时间前缀
     * 存储节点被占用的时间戳，用于超时检测
     */
    public static final String NODE_OCCUPANCY_TIME_PREFIX = "node:occupancy:time:";

    /**
     * 告警信息前缀
     * 存储告警记录的缓存信息
     */
    public static final String ALARM_PREFIX = "alarm:";

    /**
     * 未处理告警键
     * 存储所有未处理的告警ID列表
     */
    public static final String ALARM_UNHANDLED_KEY = "alarm:unhandled";

    /**
     * 任务缓存过期时间（秒）
     */
    public static final long TASK_CACHE_SECONDS = 3600;

    /**
     * AGV状态缓存过期时间（秒）
     */
    public static final long AGV_STATUS_SECONDS = 300;

    /**
     * 路径占用缓存过期时间（秒）
     */
    public static final long PATH_OCCUPY_SECONDS = 60;

    /**
     * 调度锁过期时间（秒）
     */
    public static final long DISPATCH_LOCK_SECONDS = 10;

    /**
     * 节点锁过期时间（秒）
     */
    public static final long NODE_LOCK_SECONDS = 30;

    /**
     * 路口锁过期时间（秒）
     */
    public static final long INTERSECTION_LOCK_SECONDS = 20;

    /**
     * 死锁检测锁过期时间（秒）
     */
    public static final long DEADLOCK_LOCK_SECONDS = 30;

    /**
     * 等待状态缓存过期时间（秒）
     */
    public static final long WAITING_STATUS_SECONDS = 120;

    /**
     * 路径阻塞缓存过期时间（秒）
     */
    public static final long PATH_BLOCKED_SECONDS = 180;

    /**
     * 任务调度锁过期时间（秒）
     */
    public static final long TASK_DISPATCH_LOCK_SECONDS = 60;

    /**
     * 任务执行信息过期时间（秒）
     */
    public static final long TASK_EXECUTION_SECONDS = 3600;

    /**
     * AGV当前任务过期时间（秒）
     */
    public static final long AGV_CURRENT_TASK_SECONDS = 3600;
}
