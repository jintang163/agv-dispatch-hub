package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.SysUser;
import com.agv.dispatch.common.enums.RoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统用户数据访问接口
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Repository
public interface UserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<SysUser> findByRole(RoleEnum role);

    List<SysUser> findByStatus(Integer status);

    List<SysUser> findByRoleAndStatus(RoleEnum role, Integer status);
}
