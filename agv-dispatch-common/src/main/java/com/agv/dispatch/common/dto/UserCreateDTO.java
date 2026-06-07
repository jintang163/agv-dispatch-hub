package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.RoleEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户创建DTO
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Data
@Schema(description = "创建用户请求")
public class UserCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "newuser", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "真实姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String realName;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @NotNull(message = "角色不能为空")
    @Schema(description = "角色", requiredMode = Schema.RequiredMode.REQUIRED)
    private RoleEnum role;

    @Schema(description = "状态 1-启用 0-禁用", example = "1")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
