package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import io.netty.handler.codec.compression.Zstd;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

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

    @Resource
    private FollowServiceImpl followService;

    @Override
    public Result queryHotBlog(Integer current){
        Page<Blog> page =query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    };
    //获得博客内容
    public Result queryBlogById(Long id){
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }
    //查看点赞状态
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return;
        }
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked:" + blog.getId();
        //查看点赞状态
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        blog.setIsLike(score != null);
    }
    //实现点赞功能
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score == null) {
            //点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            //如果已经点过赞，则取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }
    //获取一个博客的喜欢人数前五个
    @Override
    public Result queryBlogByLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //只获取前五个
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key,0,4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //long::valueOf 实现类型转换
        //.collect(Collectors.toList() 把流（Stream）中的元素收集成一个 List 列表。
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",",ids);
        List<UserDTO> userDTOS = userService.query().in("id",ids).last("ORDER BY FIELD(id," + idStr +")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
    //发布博客的时候同时发布给所有关注的人
    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean isSuccess =  save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        List<Follow> follows = followService.query().eq("follow_user_id",user.getId()).list();
        for(Follow follow:follows){
            Long userId = follow.getUserId();
            String key = FEED_KEY+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        //关注的博主
        String key = FEED_KEY+userId;
        //从 Redis 的 ZSet 中查询 score ≤ max 的元素，按 score 降序排序（分页获取2条）
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key,0,max,offset,2);
        if( typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //保证更新博客时，界面刷新时可以刷新
        //初始化数组大小
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;// 偏移量，初始为1
        //填充数组
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            //获取zset中的score（时间戳）进行比对
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        String idStr = StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id",ids).last("ORDER BY FIELD(id," + idStr +")").list();
        for(Blog blog:blogs){
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    //博客详情
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    ;
}
