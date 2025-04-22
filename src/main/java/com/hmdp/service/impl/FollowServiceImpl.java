package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result whetherFollow(Long id) {
        Long currentUserId = UserHolder.getUser().getId();

        //1.根据id查找数据库信息
        List<Follow> followList = query().eq("id", id)
                                        .eq("follow_user_id", currentUserId)
                                        .list();

        //followList为空,直接返回错误
        if(followList.isEmpty()){
            return Result.ok(false);
        }


        //2.遍历集合,判断followUserId是否和当前用户的id相同
        for (Follow follow : followList) {
            if(follow.getFollowUserId().equals(currentUserId)){
                return Result.ok(true);
            }
        }

        //3.到这说明list中关注的人没有当前用户,返回false
        return Result.ok(false);
    }

//    @Override
//    public Result whetherFollow(Long id) {
//        Long userId = UserHolder.getUser().getId();
//        //1.根据id查找数据库信息
//        Integer count = query().eq("user_id", id).eq("follow_user_id", userId).count();
//
//        return Result.ok(count > 0);
//
//
//    }


    /*
    关注和取关
     */
    @Override
    public Result follow(Long id, Boolean status) {
        Long userId = UserHolder.getUser().getId();

        //如果已经关注过了,执行取关操作
        if(!status){
            //将数据库中的信息删除掉
            QueryWrapper<Follow> followQuery = new QueryWrapper<>();
            followQuery.eq("follow_user_id", userId);
            followQuery.eq("user_id",id);

            remove(followQuery);

            return Result.ok("取关成功!");
        }

        //没有关注,执行关注操作
        Follow follow = Follow.builder()
                .followUserId(userId)
                .userId(id)
                .createTime(LocalDateTime.now()).build();

        //向数据库插入信息
        save(follow);

        return Result.ok("关注成功!");
    }
}
