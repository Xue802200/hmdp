package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.constant.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 写博客
     * @param blog  博客的实体类
     * @return      String
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 进行博客点赞/取消点赞
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlog(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryLikesById(@PathVariable("id") Long id) {
        return blogService.queryLikesById(id);
    }

    /**
     * 分页查询用户的blog
     * @param id        要查询的用户的id
     * @param current   当前的页数
     * @return          list集合封装符合数据的blog
     */
    @GetMapping("/of/user")
    public Result pageQueryBlog(@RequestParam("id") Long id,@RequestParam(value = "current" , defaultValue = "1") Integer current) {
        return blogService.pageQueryBlog(id,current);
    }

    /**
     * 查询关注者的博客信息
     * @param timeStamp  时间戳
     * @param offset    偏移量
     * @return          offset和上一次查询的最大值stamp,
     */
    @GetMapping("/of/follow")
    public Result queryFollowBlog(@RequestParam("lastId") Long timeStamp,@RequestParam(value = "offset" , defaultValue = "0") Integer offset) {
        return blogService.queryFollowBlog(timeStamp , offset);
    }
}
