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

    /*
    盘带判断是否关注这个用户
     */
    Result whetherFollow(Long id);

    /*
    关注和取关
     */
    Result follow(Long id, Boolean status);

    /*
    查询共同关注的用户
     */
    Result mutualFollow(Long id);
}
