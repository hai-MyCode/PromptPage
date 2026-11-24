package com.cong.congaicodemother.mapper;

import com.mybatisflex.core.BaseMapper;
import com.cong.congaicodemother.model.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 映射层。
 *
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 扣减一次生成额度（只有额度大于 0 才会扣减）
     *
     * @param userId 用户 id
     * @return 受影响行数，0 表示额度不足
     */
    @Update("update `user` set quota = quota - 1 where id = #{userId} and quota > 0")
    int deductQuota(@Param("userId") Long userId);

    /**
     * 退回一次生成额度（生成失败时补偿）
     *
     * @param userId 用户 id
     * @return 受影响行数
     */
    @Update("update `user` set quota = quota + 1 where id = #{userId}")
    int refundQuota(@Param("userId") Long userId);
}
