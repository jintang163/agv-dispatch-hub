package com.agv.dispatch.common.context;

import com.agv.dispatch.common.entity.SysUser;

/**
 * 用户上下文，存储当前登录用户信息
 * 使用ThreadLocal实现线程隔离
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
public class UserContext {

    private static final ThreadLocal<SysUser> USER_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> IP_HOLDER = new ThreadLocal<>();

    public static void setUser(SysUser user) {
        USER_HOLDER.set(user);
    }

    public static SysUser getUser() {
        return USER_HOLDER.get();
    }

    public static String getUsername() {
        SysUser user = USER_HOLDER.get();
        return user != null ? user.getUsername() : null;
    }

    public static String getRealName() {
        SysUser user = USER_HOLDER.get();
        return user != null ? user.getRealName() : null;
    }

    public static Long getUserId() {
        SysUser user = USER_HOLDER.get();
        return user != null ? user.getId() : null;
    }

    public static void setToken(String token) {
        TOKEN_HOLDER.set(token);
    }

    public static String getToken() {
        return TOKEN_HOLDER.get();
    }

    public static void setIp(String ip) {
        IP_HOLDER.set(ip);
    }

    public static String getIp() {
        return IP_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
        TOKEN_HOLDER.remove();
        IP_HOLDER.remove();
    }

    public static boolean isLoggedIn() {
        return USER_HOLDER.get() != null;
    }
}
