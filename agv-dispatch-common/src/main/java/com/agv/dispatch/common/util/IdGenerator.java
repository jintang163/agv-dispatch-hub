package com.agv.dispatch.common.util;

import cn.hutool.core.util.IdUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class IdGenerator {

    private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static String generateId() {
        return IdUtil.fastSimpleUUID();
    }

    public static String generateTaskNo() {
        String timestamp = LocalDateTime.now().format(TASK_NO_FORMATTER);
        String random = IdUtil.randomUUID().substring(0, 6).toUpperCase();
        return "TK" + timestamp + random;
    }

    public static String generateAgvNo() {
        return "AGV" + System.currentTimeMillis() % 100000;
    }
}
