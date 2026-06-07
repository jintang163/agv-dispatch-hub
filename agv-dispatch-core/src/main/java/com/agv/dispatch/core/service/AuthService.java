package com.agv.dispatch.core.service;

import com.agv.dispatch.common.dto.LoginRequestDTO;
import com.agv.dispatch.common.dto.LoginResponseDTO;
import com.agv.dispatch.common.entity.OperationLog;
import com.agv.dispatch.common.entity.SysUser;
import com.agv.dispatch.common.enums.OperationType;
import com.agv.dispatch.common.enums.RoleEnum;
import com.agv.dispatch.common.util.JsonUtil;
import com.agv.dispatch.core.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务
 * 处理用户登录、登出、Token验证等功能
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OperationLogService operationLogService;
    private final HttpServletRequest request;

    private static final Map<String, SysUser> TOKEN_CACHE = new ConcurrentHashMap<>();
    private static final long TOKEN_EXPIRE_HOURS = 24;

    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        long startTime = System.currentTimeMillis();

        OperationLog operationLog = OperationLog.create(OperationType.USER_LOGIN, request.getUsername());

        try {
            SysUser user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

            if (!user.isEnabled()) {
                throw new IllegalArgumentException("用户已被禁用");
            }

            if (!verifyPassword(request.getPassword(), user.getPassword())) {
                operationLog.error("密码错误").executeTime(System.currentTimeMillis() - startTime);
                operationLogService.saveLog(operationLog);
                throw new IllegalArgumentException("用户名或密码错误");
            }

            String token = generateToken();
            TOKEN_CACHE.put(token, user);

            user.setLastLoginIp(getClientIp());
            user.setLastLoginTime(LocalDateTime.now());
            userRepository.save(user);

            operationLog
                    .operatorName(user.getRealName())
                    .operationIp(getClientIp())
                    .detail("用户登录成功")
                    .afterData(JsonUtil.toJson(user))
                    .executeTime(System.currentTimeMillis() - startTime);
            operationLogService.saveLog(operationLog);

            return LoginResponseDTO.builder()
                    .token(token)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .realName(user.getRealName())
                    .role(user.getRole())
                    .roleDesc(user.getRole().getDesc())
                    .avatar(user.getAvatar())
                    .expiresIn(TOKEN_EXPIRE_HOURS * 3600)
                    .build();

        } catch (Exception e) {
            operationLog
                    .error(e.getMessage())
                    .executeTime(System.currentTimeMillis() - startTime);
            operationLogService.saveLog(operationLog);
            throw e;
        }
    }

    @Transactional
    public void logout(String token) {
        SysUser user = TOKEN_CACHE.get(token);
        if (user != null) {
            OperationLog operationLog = OperationLog.create(OperationType.USER_LOGOUT, user.getUsername())
                    .operatorName(user.getRealName())
                    .operationIp(getClientIp())
                    .detail("用户登出成功");
            operationLogService.saveLog(operationLog);
            TOKEN_CACHE.remove(token);
        }
    }

    public SysUser validateToken(String token) {
        SysUser user = TOKEN_CACHE.get(token);
        if (user == null) {
            throw new IllegalArgumentException("无效的Token");
        }
        return user;
    }

    public SysUser getCurrentUser() {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null) {
            return null;
        }
        return TOKEN_CACHE.get(token);
    }

    public boolean hasPermission(String permission) {
        SysUser user = getCurrentUser();
        if (user == null) {
            return false;
        }
        return user.hasPermission(permission);
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean verifyPassword(String rawPassword, String encodedPassword) {
        return rawPassword.equals(encodedPassword);
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    public void initDefaultUsers() {
        if (userRepository.count() == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword("admin123");
            admin.setRealName("系统管理员");
            admin.setRole(RoleEnum.ADMIN);
            admin.setPhone("13800138000");
            admin.setEmail("admin@agv.com");
            admin.setCreateBy("system");
            userRepository.save(admin);
            log.info("创建默认管理员账号: admin/admin123");

            SysUser dispatcher = new SysUser();
            dispatcher.setUsername("dispatcher");
            dispatcher.setPassword("dispatcher123");
            dispatcher.setRealName("调度员");
            dispatcher.setRole(RoleEnum.DISPATCHER);
            dispatcher.setPhone("13800138001");
            dispatcher.setEmail("dispatcher@agv.com");
            dispatcher.setCreateBy("system");
            userRepository.save(dispatcher);
            log.info("创建默认调度员账号: dispatcher/dispatcher123");

            SysUser viewer = new SysUser();
            viewer.setUsername("viewer");
            viewer.setPassword("viewer123");
            viewer.setRealName("只读用户");
            viewer.setRole(RoleEnum.READ_ONLY);
            viewer.setPhone("13800138002");
            viewer.setEmail("viewer@agv.com");
            viewer.setCreateBy("system");
            userRepository.save(viewer);
            log.info("创建默认只读账号: viewer/viewer123");
        }
    }
}
