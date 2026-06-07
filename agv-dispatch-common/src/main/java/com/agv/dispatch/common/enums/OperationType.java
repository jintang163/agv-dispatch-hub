package com.agv.dispatch.common.enums;

import lombok.Getter;

@Getter
public enum OperationType {

    TASK_CREATE(1, "任务创建", "创建新任务"),
    TASK_DISPATCH(2, "任务下发", "下发任务给AGV执行"),
    TASK_PRIORITY_UPDATE(3, "任务插队", "调整任务优先级"),
    TASK_CANCEL(4, "任务取消", "取消已存在的任务"),
    TASK_PAUSE(5, "任务暂停", "暂停执行中的任务"),
    TASK_RESUME(6, "任务恢复", "恢复已暂停的任务"),
    TASK_REASSIGN(7, "任务重分配", "重新分配任务执行AGV"),
    AGV_PAUSE(8, "AGV暂停", "暂停AGV运行"),
    AGV_RESUME(9, "AGV恢复", "恢复AGV运行"),
    AGV_STOP(10, "AGV急停", "紧急停止AGV"),
    AGV_CHARGE(11, "呼叫充电", "呼叫AGV前往充电站"),
    AGV_REMOTE_CONTROL(12, "远程控制", "远程控制AGV移动"),
    CONFLICT_RESOLVE(13, "冲突解决", "手动解决路径冲突"),
    DEADLOCK_RESOLVE(14, "死锁解决", "手动解决死锁"),
    PATH_REPLAN(15, "路径重规划", "重新规划任务路径"),
    ALARM_HANDLE(16, "告警处理", "处理系统告警"),
    PATH_BLOCK_MARK(17, "标记阻塞", "标记路径节点为阻塞"),
    PATH_BLOCK_CLEAR(18, "清除阻塞", "清除路径节点阻塞标记"),
    USER_LOGIN(19, "用户登录", "用户登录系统"),
    USER_LOGOUT(20, "用户登出", "用户登出系统"),
    USER_CREATE(21, "创建用户", "创建新系统用户"),
    USER_UPDATE(22, "更新用户", "更新用户信息"),
    USER_DELETE(23, "删除用户", "删除系统用户"),
    USER_ROLE_CHANGE(24, "角色变更", "变更用户角色"),
    TASK_COMPLETE(25, "任务完成", "任务执行完成");

    private final int code;
    private final String desc;
    private final String remark;

    OperationType(int code, String desc, String remark) {
        this.code = code;
        this.desc = desc;
        this.remark = remark;
    }

    public static OperationType of(int code) {
        for (OperationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return TASK_CREATE;
    }
}
