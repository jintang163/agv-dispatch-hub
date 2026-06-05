package com.agv.dispatch.common.util;

import com.agv.dispatch.common.entity.Task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;

public class TaskQueueComparator implements Comparator<Task> {

    private static final double DEADLINE_WEIGHT = 0.6;
    private static final double PRIORITY_WEIGHT = 0.4;

    @Override
    public int compare(Task t1, Task t2) {
        double score1 = calculateScore(t1);
        double score2 = calculateScore(t2);
        return Double.compare(score2, score1);
    }

    private double calculateScore(Task task) {
        double priorityScore = task.getPriority().getCode() * PRIORITY_WEIGHT;
        double deadlineScore = 0;

        if (task.getDeadline() != null) {
            LocalDateTime now = LocalDateTime.now();
            long minutesUntilDeadline = Duration.between(now, task.getDeadline()).toMinutes();
            if (minutesUntilDeadline <= 0) {
                deadlineScore = 100;
            } else if (minutesUntilDeadline < 30) {
                deadlineScore = 100 - (minutesUntilDeadline * 2);
            } else if (minutesUntilDeadline < 60) {
                deadlineScore = 40 - ((minutesUntilDeadline - 30) * 0.5);
            } else {
                deadlineScore = 10;
            }
        }
        deadlineScore *= DEADLINE_WEIGHT;

        return priorityScore + deadlineScore;
    }
}
