package com.agv.dispatch.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * JSON工具类
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
public class JsonUtil {

    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.toJSONString(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, typeReference);
    }

    public static <T> List<T> fromJsonList(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSON.parseArray(json, clazz);
    }

    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.parseObject(toJson(obj), new TypeReference<Map<String, Object>>() {});
    }

    public static <T> T mapToObject(Map<String, Object> map, Class<T> clazz) {
        if (map == null) {
            return null;
        }
        return JSON.parseObject(toJson(map), clazz);
    }

    public static JSONObject toJsonObject(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.parseObject(toJson(obj));
    }

    public static boolean isJson(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            JSON.parse(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
