package com.cong.congaicodemother.constant;

/**
 * 用户常量
 */
public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";
    
    // endregion

    // region 额度

    /**
     * 新用户注册时默认赠送的生成额度（次数）
     */
    Integer DEFAULT_QUOTA = 3;

    // endregion
}