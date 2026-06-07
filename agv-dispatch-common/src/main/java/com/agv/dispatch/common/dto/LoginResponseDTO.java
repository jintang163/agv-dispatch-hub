package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.RoleEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponseDTO {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "角色")
    private RoleEnum role;

    @Schema(description = "角色描述")
    private String roleDesc;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "过期时间（秒）")
    private Long expiresIn;
}
