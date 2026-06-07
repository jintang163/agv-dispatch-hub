package com.agv.dispatch.core.service;

import com.agv.dispatch.common.dto.UserCreateDTO;
import com.agv.dispatch.common.dto.UserUpdateDTO;
import com.agv.dispatch.common.entity.OperationLog;
import com.agv.dispatch.common.entity.SysUser;
import com.agv.dispatch.common.enums.OperationType;
import com.agv.dispatch.common.enums.RoleEnum;
import com.agv.dispatch.common.util.JsonUtil;
import com.agv.dispatch.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理服务
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OperationLogService operationLogService;
    private final AuthService authService;

    public Page<SysUser> queryUsers(String keyword, RoleEnum role, Integer status, Pageable pageable) {
        Specification<SysUser> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (keyword != null && !keyword.isEmpty()) {
                predicates = cb.and(predicates, cb.or(
                        cb.like(root.get("username"), "%" + keyword + "%"),
                        cb.like(root.get("realName"), "%" + keyword + "%"),
                        cb.like(root.get("phone"), "%" + keyword + "%")
                ));
            }
            if (role != null) {
                predicates = cb.and(predicates, cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            return predicates;
        };
        return userRepository.findAll(spec, pageable);
    }

    public SysUser getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    public SysUser getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    public List<SysUser> getAllUsers() {
        return userRepository.findAll();
    }

    public List<SysUser> getUsersByRole(RoleEnum role) {
        return userRepository.findByRole(role);
    }

    @Transactional
    public SysUser createUser(UserCreateDTO dto) {
        long startTime = System.currentTimeMillis();
        SysUser currentUser = authService.getCurrentUser();
        String operator = currentUser != null ? currentUser.getUsername() : "system";

        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        user.setRemark(dto.getRemark());
        user.setCreateBy(operator);
        user.setUpdateBy(operator);

        user = userRepository.save(user);

        OperationLog log = OperationLog.create(OperationType.USER_CREATE, operator)
                .operatorName(currentUser != null ? currentUser.getRealName() : "系统")
                .detail("创建用户: " + user.getUsername())
                .afterData(JsonUtil.toJson(user))
                .executeTime(System.currentTimeMillis() - startTime);
        operationLogService.saveLog(log);

        return user;
    }

    @Transactional
    public SysUser updateUser(Long id, UserUpdateDTO dto) {
        long startTime = System.currentTimeMillis();
        SysUser currentUser = authService.getCurrentUser();
        String operator = currentUser != null ? currentUser.getUsername() : "system";

        SysUser user = getUserById(id);
        String beforeData = JsonUtil.toJson(user);

        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getRole() != null && dto.getRole() != user.getRole()) {
            user.setRole(dto.getRole());
            OperationLog roleLog = OperationLog.create(OperationType.USER_ROLE_CHANGE, operator)
                    .operatorName(currentUser != null ? currentUser.getRealName() : "系统")
                    .detail("变更用户角色: " + user.getUsername() + " -> " + dto.getRole().getDesc())
                    .beforeData(user.getRole().getDesc())
                    .afterData(dto.getRole().getDesc());
            operationLogService.saveLog(roleLog);
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            user.setRemark(dto.getRemark());
        }
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(dto.getPassword());
        }
        user.setUpdateBy(operator);

        user = userRepository.save(user);

        OperationLog log = OperationLog.create(OperationType.USER_UPDATE, operator)
                .operatorName(currentUser != null ? currentUser.getRealName() : "系统")
                .detail("更新用户信息: " + user.getUsername())
                .beforeData(beforeData)
                .afterData(JsonUtil.toJson(user))
                .executeTime(System.currentTimeMillis() - startTime);
        operationLogService.saveLog(log);

        return user;
    }

    @Transactional
    public void deleteUser(Long id) {
        long startTime = System.currentTimeMillis();
        SysUser currentUser = authService.getCurrentUser();
        String operator = currentUser != null ? currentUser.getUsername() : "system";

        SysUser user = getUserById(id);
        String beforeData = JsonUtil.toJson(user);

        userRepository.delete(user);

        OperationLog log = OperationLog.create(OperationType.USER_DELETE, operator)
                .operatorName(currentUser != null ? currentUser.getRealName() : "系统")
                .detail("删除用户: " + user.getUsername())
                .beforeData(beforeData)
                .executeTime(System.currentTimeMillis() - startTime);
        operationLogService.saveLog(log);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}
