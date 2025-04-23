package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /*
    查看页面上的热点博客
     */
    Result queryHotBlog(Integer current);

    /*
    查询单个博客的详细记录
     */
    Result queryBlog(Long id);

    /*
    修改点赞数量
     */
    Result likeBlog(Long id);

    /**
     * 查看点赞排行
     * @param id  要查询的博客id
     * @return    点赞排行
     */
    Result queryLikesById(Long id);

    Result pageQueryBlog(Long id, Integer current);

    /*
    保存博客
     */
    Result saveBlog(Blog blog);

    /**
     * 查询关注者的博客信息
     * @param timeStamp  当前时间戳
     * @param offset    偏移量
     * @return          offset和上一次查询的最大值stamp,
     */
    Result queryFollowBlog(Long timeStamp, Integer offset);
}