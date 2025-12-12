package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注或取消关注
     * @param followUserId 关注用户id
     * @param isFollow 是否关注
     * @return {@link Result}
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询是否关注
     * @param followUserId 关注用户id
     * @return {@link Result}
     */
    Result isFollow(Long followUserId);

    /**
     * 查询共同关注
     * @param id 用户id
     * @return {@link Result}
     */
    Result followCommons(Long id);
}
