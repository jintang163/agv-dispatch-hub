package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.RoleEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户更新DTO
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Data
@Schema(description = "更新用户请求")
public class UserUpdateDTO {

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "角色")
    private RoleEnum role;

    @Schema(description = "状态 1-启用 0-禁用", example = "1")
    private Integer status;

    @Schema(description = "新密码")
    private String password;

    @Schema(description = "备注")
    private String remark;
}
