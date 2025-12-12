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

    /**
     * 查询热门博客
     *
     * @param current 当前
     * @return {@link Result}
     */
    Result queryHotBlog(Integer current);

    /**
     * 通过id查询博客
     *
     * @param id id
     * @return {@link Result}
     */
    Result queryBlogById(Long id);

    /**
     * 点赞博客
     *
     * @param id id
     * @return {@link Result}
     */
    Result likeBlog(Long id);

    /**
     * 查询博客点赞
     *
     * @param id id
     * @return {@link Result}
     */
    Result queryBlogLikesById(Long id);

    /**
     * 查询博客关注
     *
     * @param max   最大
     * @param offset 偏移
     * @return {@link Result}
     */
    Result queryBlogOfFollow(Long max, Integer offset);

    /**
     * 保存博客
     *
     * @param blog 博客
     * @return {@link Result}
     */
    Result saveBlog(Blog blog);
}
