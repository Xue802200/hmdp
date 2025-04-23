package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
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

        if(blog==null){
            return Result.fail("博客不存在!");
        }

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

    @Override
    public Result pageQueryBlog(Long id, Integer current) {
        //1.根据id去查找
        Page<Blog> blogs = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        //2.获取当前页数据
        List<Blog> records = blogs.getRecords();

        return Result.ok(records);
    }

    @Override
    public Result saveBlog(Blog blog) {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());

        //2.保存探店博文
        save(blog);

        //3.获取粉丝id
        List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();

        if(followList != null && !followList.isEmpty()){
            for(Follow follow : followList){
                //4.将博客信息发送到粉丝的收件箱中   feed:粉丝id
                String key = "feed:" + follow.getUserId();
                stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
            }

        }

        //5.返回id
        return Result.ok(blog.getId());
    }

    /**
     * 关注推流
     * @param timeStamp  当前时间戳
     * @param offset    偏移量
     * @return          offset最大score的数量   maxScore的最大值   List<Blog>
     */
    @Override
    public Result queryFollowBlog(Long timeStamp, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = "feed:" + userId;

        //1.判断缓存中是否存在数据
        Long size = stringRedisTemplate.opsForZSet().size(key);
        if(size == null || size == 0){
            //没有说明关注的人没有发过blog
            return null;
        }

        //2.在收件箱中拿取消息 ZREVRANGE timeStamp 0 limit offset 3    feed:currentID
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, timeStamp, offset, 3);

        //3.对其为空进行判断
        if(typedTuples == null || typedTuples.isEmpty()){
            ////没有说明关注的人没有发过blog
            return null;
        }

        //4.解析保存在缓存中的id和score
        List<Blog> blogs = new ArrayList<>();
        List<Double> scoreList = new ArrayList<>();
        for(ZSetOperations.TypedTuple<String> typedTuple : typedTuples){
            String blogId = typedTuple.getValue();
            Double score = typedTuple.getScore();
            Blog blog = getById(blogId);
            blogs.add(blog);
            scoreList.add(score);
        }

        //5.获取当前查询获取到的score最大值max 和最大值对应的数目
        List<Long> scoreListLong = scoreList.stream().map(Double::longValue).collect(Collectors.toList());   //将Double值转化为Long值
        scoreList.sort(Collections.reverseOrder());  //将集合进行降序排序

        //获取最大数的数量
        long max = scoreListLong.stream()
                .filter(score -> score.equals(Collections.max(scoreListLong)))
                .count();
        Integer count = (int) max;

        //5.封装返回对象
        ScrollDTO scrollDTO = ScrollDTO.builder()
                .List(blogs)
                .Offset(count)
                .MinTime(Collections.max(scoreListLong))
                .build();


        //6.返回给前端
        return Result.ok(scrollDTO);
    }
}
