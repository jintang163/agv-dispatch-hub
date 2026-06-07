package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.dto.LoginRequestDTO;
import com.agv.dispatch.common.dto.LoginResponseDTO;
import com.agv.dispatch.common.dto.Result;
import com.agv.dispatch.common.entity.SysUser;
import com.agv.dispatch.core.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、登出、Token验证等接口")
public class AuthController {

    private final AuthService authService;
    private final HttpServletRequest request;

    @PostConstruct
    public void init() {
        authService.initDefaultUsers();
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录，返回Token")
    public Result<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        LoginResponseDTO response = authService.login(dto);
        return Result.success(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "销毁当前用户Token")
    public Result<Void> logout() {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        authService.logout(token);
        return Result.success();
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "根据Token获取当前登录用户信息")
    public Result<Map<String, Object>> getCurrentUser() {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        SysUser user = authService.validateToken(token);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("role", user.getRole());
        userInfo.put("roleDesc", user.getRole().getDesc());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("phone", user.getPhone());
        userInfo.put("email", user.getEmail());
        userInfo.put("permissions", getUserPermissions(user));
        return Result.success(userInfo);
    }

    @GetMapping("/validate")
    @Operation(summary = "验证Token", description = "验证Token是否有效")
    public Result<Boolean> validateToken() {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            authService.validateToken(token);
            return Result.success(true);
        } catch (Exception e) {
            return Result.success(false);
        }
    }

    private String[] getUserPermissions(SysUser user) {
        return switch (user.getRole()) {
            case ADMIN -> new String[]{
                    "user:view", "user:create", "user:update", "user:delete",
                    "task:view", "task:create", "task:update", "task:cancel", "task:dispatch",
                    "agv:view", "agv:control",
                    "log:view", "log:export"
            };
            case DISPATCHER -> new String[]{
                    "user:view",
                    "task:view", "task:create", "task:update", "task:cancel", "task:dispatch",
                    "agv:view", "agv:control",
                    "log:view"
            };
            case READ_ONLY -> new String[]{
                    "user:view",
                    "task:view",
                    "agv:view",
                    "log:view"
            };
        };
    }
}
