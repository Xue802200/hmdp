package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 关注其他用户
     * @param id      关联用户的id
     * @param status  是否关注过该用户
     */
    @PutMapping("/{id}/{status}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("status") Boolean status) {
        return followService.follow(id,status);
    }

    /**
     * 查询该用户是否关注过
     * @param id       判断用户的id
     * @return         true或者false
     */
    @GetMapping("/or/not/{id}")
    public Result whetherFollow(@PathVariable Long id) {
        return followService.whetherFollow(id);
    }


    /**
     * 查询共同关注的用户
     * @param id    查询用户的id
     * @return      共同关注的人
     */
    @GetMapping("/common/{id}")
    public Result mutualFollow(@PathVariable("id") Long id) {
        return followService.mutualFollow(id);
    }
}
