package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.RoleEnum;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Data
@Entity
@Table(name = "sys_user", indexes = {
        @Index(name = "idx_username", columnList = "username", unique = true),
        @Index(name = "idx_role", columnList = "role"),
        @Index(name = "idx_status", columnList = "status")
})
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String username;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(nullable = false, length = 64)
    private String realName;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RoleEnum role;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(length = 256)
    private String avatar;

    @Column(length = 512)
    private String remark;

    @Column(length = 64)
    private String lastLoginIp;

    private LocalDateTime lastLoginTime;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;

    @Column(length = 64)
    private String createBy;

    @Column(length = 64)
    private String updateBy;

    public boolean isEnabled() {
        return status == 1;
    }

    public boolean isAdmin() {
        return role == RoleEnum.ADMIN;
    }

    public boolean isDispatcher() {
        return role == RoleEnum.DISPATCHER;
    }

    public boolean isReadOnly() {
        return role == RoleEnum.READ_ONLY;
    }

    public boolean hasPermission(String permission) {
        return role.hasPermission(permission);
    }
}
