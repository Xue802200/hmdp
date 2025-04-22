package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*
    判断是否关注了id的用户
     */
    @Override
    public Result whetherFollow(Long id) {
        Long currentUserId = UserHolder.getUser().getId();

        //缓存中查找
        Set<String> ids = stringRedisTemplate.opsForSet().members("follow:user:" + currentUserId);

        if(ids == null || ids.isEmpty()) {
            return Result.ok(false);
        }

        //判断id是否在ids之内
        List<Long> idsList = ids.stream().map(Long::valueOf).collect(Collectors.toList());

        if(idsList.contains(id)) {
            //包含了说明关注了,返回true
            return Result.ok(true);
        }

        //不包含,返回false
        return Result.ok(false);
    }


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

            boolean remove = remove(followQuery);

            if(remove){
                //follow:user:userId  保存关注人的id
                //将缓存中的数据删除
                stringRedisTemplate.opsForSet().remove("follow:user:" + userId, id.toString());

            }

            return Result.ok("取关成功!");
        }

        //没有关注,执行关注操作
        Follow follow = Follow.builder()
                .followUserId(userId)
                .userId(id)
                .createTime(LocalDateTime.now()).build();

        //向数据库插入信息
        boolean save = save(follow);

        if(save){
            //向缓存中添加数据
            stringRedisTemplate.opsForSet().add("follow:user:" + userId , id.toString());

        }

        return Result.ok("关注成功!");
    }

    /*
    查找共同关注的人
     */
    @Override
    public Result mutualFollow(Long id) {
        //当前用户id
        Long userId = UserHolder.getUser().getId();

        //获取共同关注人的id
        String key1= "follow:user:" + userId;
        String key2= "follow:user:" + id;
        Set<String> shareId = stringRedisTemplate.opsForSet().intersect(key1, key2);

        //判断是否存在
        if(shareId == null || shareId.isEmpty()){
            //不存在返回空就好
            return null;
        }

        //存在,将id提取出来
        List<Long> ids = shareId.stream().map(Long::valueOf).collect(Collectors.toList());

        //根据id获取用户信息
        List<User> users = userService.listByIds(ids);

        //转为DTO
        List<UserDTO> userDTOS = new ArrayList<>();
        for(User user : users){
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            userDTOS.add(userDTO);
        }

        //返回
        return Result.ok(userDTOS);
    }


//    @Override
//    public Result mutualFollow(Long id) {
//        Long currentUserId = UserHolder.getUser().getId();
//        //1.查询该用户是否关注过其他人
//        List<Follow> followList1 = query().eq("follow_user_id", id).list();
//
//        //要查询用户的所有关注人id
//        List<Long> followUserIds = followList1.stream().map(Follow::getFollowUserId).collect(Collectors.toList());
//
//        //2.判断
//        if(followList1.isEmpty()){
//            return Result.ok("该用户未关注其他人");
//        }
//
//        //3.存在的话,根据当前用户id查找是否关注过用户
//        List<Follow> followList = query().eq("follow_user_id", currentUserId).list();
//
//        //当前用户所有的关注人id
//        List<Long> currentUserFollows = followList.stream().map(Follow::getFollowUserId).collect(Collectors.toList());
//
//        if(followList.isEmpty()){
//            return Result.ok("您未关注过用户!");
//        }
//
//        //4.查找是否有共同关注的人
//        List<Long> commons = currentUserFollows.stream()
//                .filter(followUserIds::contains)
//                .collect(Collectors.toList());
//
//        //5.根据id去查找对应的用户信息
//        List<User> users = userService.listByIds(commons);
//
//        //转化为DTO
//        List<UserDTO> userDTOS = new ArrayList<>();
//        for (User user : users) {
//            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//            userDTOS.add(userDTO);
//        }
//
//        //返回
//        return Result.ok(userDTOS);
//    }
}
