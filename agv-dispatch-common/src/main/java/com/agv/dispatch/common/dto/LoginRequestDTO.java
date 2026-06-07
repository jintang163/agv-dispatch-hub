package com.agv.dispatch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Data
@Schema(description = "登录请求")
public class LoginRequestDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "admin123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "验证码")
    private String captcha;

    @Schema(description = "记住我")
    private Boolean rememberMe;
}
