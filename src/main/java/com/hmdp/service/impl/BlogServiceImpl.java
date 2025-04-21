package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查看热点笔记
     * @param current  当前页数
     * @return         当前页数和当前页要展示多少数据
     */
    @Override
    public Result queryHotBlog(Integer current) {
        //根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current,SystemConstants.MAX_PAGE_SIZE));

        //获取当前页数据
        List<Blog> records = page.getRecords();

        //查询用户
        records.forEach(blog -> {
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        });

        //查询是否被点赞
        records.forEach(this::isBlockLiked);

        return Result.ok(records);
    }


    /*
    查询博客是否被点赞
     */
    private void isBlockLiked(Blog blog) {
        //获取博客id,userId
        Long id = blog.getId();
        UserDTO userDTO = UserHolder.getUser();

        if(userDTO == null){
            return;
        }
        Long userId = UserHolder.getUser().getId();

        //判断当前用户是否点赞过
        Double isLiked = stringRedisTemplate.opsForZSet().score(RedisConstants.LIKE_BLOG_KEY + id, userId.toString());

        //点赞过则将属性设置为true
        if(isLiked != null){
            blog.setIsLike(true);
        }
    }

    /**
     * 查看笔记的详细信息
     * @param id  笔记的id
     * @return    笔记的详细信息+撰写人+商家信息
     */
    @Override
    public Result queryBlog(Long id) {
        //1.在blog中查找信息
        Blog blog = getById(id);

        if(blog==null){
            return Result.fail("笔记不存在");
        }

        //2.查询blog相关的用户信息
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        //3.查询是否被点赞
        isBlockLiked(blog);

        //返回数据
        return Result.ok(blog);
    }


    /*
    对博客进行点赞
     */
    @Override
    public Result likeBlog(Long id) {
        //1.查询当前博客
        Blog blog = getById(id);

        //2.判断当前用户是否点赞过
        Long userId = UserHolder.getUser().getId();
        Double isLiked = stringRedisTemplate.opsForZSet().score(RedisConstants.LIKE_BLOG_KEY + id, userId.toString());

        if(isLiked == null){
            //3.1没有点赞过,则可以进行点赞,更新数据库
            boolean isSuccess = update().setSql("liked = liked + 1 ").eq("id", id).update();

            if (isSuccess) {
                //3.2添加redis缓存,将当前用户id添加到set集合当中
                stringRedisTemplate.opsForZSet().add(RedisConstants.LIKE_BLOG_KEY + id,userId.toString(),System.currentTimeMillis());
            }

        }else {
            //4.如果已经点赞过了->将赞取消掉
            //4.1更新数据库
            boolean isSuccess = update().setSql("liked = liked - 1 ").eq("id", id).update();

            //4.2数据库信息更新成功,将缓存中的数据删除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(RedisConstants.LIKE_BLOG_KEY + id,userId.toString());
            }

        }

        return Result.ok(blog);
    }

    /**
     * 查询点赞排行
     * @param id  要查询的博客id
     * @return    点赞前5名
     */
    @Override
    public Result queryLikesById(Long id) {
        //1.查询该博客信息
        Blog blog = getById(id);

        //2.存在,去获取ZSet中的数据
        Set<String> range = stringRedisTemplate.opsForZSet().range(RedisConstants.LIKE_BLOG_KEY + id, 0, 4);

        //判断是否为空
        if(range == null || range.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }

        //3.解析出range中的id
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());

        //4.根据id去查找用户
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS );

    }
}
