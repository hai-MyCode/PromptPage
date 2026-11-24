package com.cong.congaicodemother.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员调整用户额度请求
 */
@Data
public class UserQuotaUpdateRequest implements Serializable {

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 剩余生成额度（次数）
     */
    private Integer quota;

    private static final long serialVersionUID = 1L;
}
