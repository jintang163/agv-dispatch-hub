package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.dto.Result;
import com.agv.dispatch.common.dto.UserCreateDTO;
import com.agv.dispatch.common.dto.UserUpdateDTO;
import com.agv.dispatch.common.entity.SysUser;
import com.agv.dispatch.common.enums.RoleEnum;
import com.agv.dispatch.core.service.AuthService;
import com.agv.dispatch.core.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户增删改查、角色分配等接口")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "分页查询用户列表")
    public Result<Page<SysUser>> getUsers(
            @Parameter(description = "搜索关键词（用户名/姓名/手机号）") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色过滤") @RequestParam(required = false) RoleEnum role,
            @Parameter(description = "状态过滤") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<SysUser> page = userService.queryUsers(keyword, role, status, pageable);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情")
    public Result<SysUser> getUserById(@Parameter(description = "用户ID") @PathVariable Long id) {
        SysUser user = userService.getUserById(id);
        return Result.success(user);
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "根据用户名获取用户")
    public Result<SysUser> getUserByUsername(@Parameter(description = "用户名") @PathVariable String username) {
        SysUser user = userService.getUserByUsername(username);
        return Result.success(user);
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有用户列表")
    public Result<List<SysUser>> getAllUsers() {
        List<SysUser> users = userService.getAllUsers();
        return Result.success(users);
    }

    @GetMapping("/role/{role}")
    @Operation(summary = "根据角色获取用户列表")
    public Result<List<SysUser>> getUsersByRole(@Parameter(description = "角色") @PathVariable RoleEnum role) {
        List<SysUser> users = userService.getUsersByRole(role);
        return Result.success(users);
    }

    @PostMapping
    @Operation(summary = "创建用户")
    public Result<SysUser> createUser(@Valid @RequestBody UserCreateDTO dto) {
        if (!authService.hasPermission("user:create")) {
            return Result.fail(403, "没有创建用户的权限");
        }
        SysUser user = userService.createUser(dto);
        return Result.success(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户")
    public Result<SysUser> updateUser(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Valid @RequestBody UserUpdateDTO dto) {
        if (!authService.hasPermission("user:update")) {
            return Result.fail(403, "没有更新用户的权限");
        }
        SysUser user = userService.updateUser(id, dto);
        return Result.success(user);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public Result<Void> deleteUser(@Parameter(description = "用户ID") @PathVariable Long id) {
        if (!authService.hasPermission("user:delete")) {
            return Result.fail(403, "没有删除用户的权限");
        }
        userService.deleteUser(id);
        return Result.success();
    }

    @GetMapping("/check-username")
    @Operation(summary = "检查用户名是否存在")
    public Result<Boolean> checkUsername(@Parameter(description = "用户名") @RequestParam String username) {
        boolean exists = userService.existsByUsername(username);
        return Result.success(exists);
    }

    @GetMapping("/roles")
    @Operation(summary = "获取所有角色列表")
    public Result<RoleEnum[]> getRoles() {
        return Result.success(RoleEnum.values());
    }
}
