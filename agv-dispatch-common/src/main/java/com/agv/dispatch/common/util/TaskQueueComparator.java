package com.agv.dispatch.common.util;

import com.agv.dispatch.common.entity.Task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * 任务队列比较器，用于对待分配任务进行优先级排序
 * 采用加权评分算法：优先级权重40%，截止时间权重60%
 * 所有需要进行任务队列排序的地方都应使用此类，确保排序逻辑统一
 *
 * 评分公式：总分 = 优先级得分 * 0.4 + 截止时间得分 * 0.6
 * 得分越高，排序越靠前
 */
public class TaskQueueComparator implements Comparator<Task> {

    /** 截止时间权重，占比60% */
    private static final double DEADLINE_WEIGHT = 0.6;
    /** 优先级权重，占比40% */
    private static final double PRIORITY_WEIGHT = 0.4;

    /**
     * 比较两个任务的优先级
     * @param t1 任务1
     * @param t2 任务2
     * @return 正数表示t2优先级更高，负数表示t1优先级更高
     */
    @Override
    public int compare(Task t1, Task t2) {
        double score1 = calculateScore(t1);
        double score2 = calculateScore(t2);
        return Double.compare(score2, score1);
    }

    /**
     * 计算任务的队列优先级分数
     * 此方法为公共静态方法，供Redis入队、自动调度、页面展示等所有场景使用
     * 确保整个系统使用同一套打分逻辑，避免排序不一致
     *
     * @param task 任务实体
     * @return 优先级分数，分数越高优先级越高
     */
    public static double calculateQueueScore(Task task) {
        // 计算优先级得分：HIGH=3, MEDIUM=2, LOW=1，乘以权重40%
        double priorityScore = task.getPriority().getCode() * PRIORITY_WEIGHT * 100;

        // 计算截止时间得分，乘以权重60%
        double deadlineScore = calculateDeadlineScore(task) * DEADLINE_WEIGHT;

        return priorityScore + deadlineScore;
    }

    /**
     * 计算截止时间得分
     * 距离截止时间越近，得分越高；已逾期的任务得分最高
     *
     * @param task 任务实体
     * @return 截止时间得分，范围0-100
     */
    private static double calculateDeadlineScore(Task task) {
        if (task.getDeadline() == null) {
            return 10; // 无截止时间的任务基础分10
        }

        LocalDateTime now = LocalDateTime.now();
        long minutesUntilDeadline = Duration.between(now, task.getDeadline()).toMinutes();

        if (minutesUntilDeadline <= 0) {
            // 已逾期，最高分100
            return 100;
        } else if (minutesUntilDeadline < 30) {
            // 30分钟内，40-100分，每分钟递减2分
            return 100 - (minutesUntilDeadline * 2);
        } else if (minutesUntilDeadline < 60) {
            // 30-60分钟，25-40分，每分钟递减0.5分
            return 40 - ((minutesUntilDeadline - 30) * 0.5);
        } else {
            // 60分钟以上，基础分10
            return 10;
        }
    }

    /**
     * 实例方法的calculateScore，供Comparator内部使用
     * @param task 任务实体
     * @return 优先级分数
     */
    private double calculateScore(Task task) {
        return calculateQueueScore(task);
    }
}
