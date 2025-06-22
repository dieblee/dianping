package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
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
@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserServiceImpl userService;
    //关注
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else {
                boolean isSuccess = remove(new QueryWrapper<Follow>()
                        .eq("user_id", userId).eq("follow_user_id", followUserId));
                if(isSuccess){
                    stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
                }
            }
            return Result.ok();


    }

    //查看是否关注
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        //1.查询是否关注select* from tb_follow where user_id=？ and follow_id=?
        Integer count = Math.toIntExact(query().eq("user_id", userId).eq("follow_user_id", followUserId).count());
        return Result.ok(count>0);
    }
    //查看是否互相关注
    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        String key2 = "follows:" + id;
        //intersect取交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key,key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);


    }

    @Override
    public Result selectFollow() {
        Long userId = UserHolder.getUser().getId();
        if (userId == null || userId <= 0) {
            return Result.fail("用户ID不合法");
        }
        try {
            String Key = "follows:" + userId;
            Set<String> followIds = stringRedisTemplate.opsForSet().members(Key);;

            // 5. 如果没有关注任何人，返回空列表
            if (followIds == null || followIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }

            // 6. 将String类型的ID转换为Long
            List<Long> ids = followIds.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            // 7. 批量查询用户信息 (避免循环查询数据库)
            List<User> users = userService.listByIds(ids);

            // 8. 转换DTO对象 (避免暴露敏感信息)
            List<UserDTO> userDTOs = users.stream()
                    .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                    .collect(Collectors.toList());
            // 9. 返回结果
            return Result.ok(userDTOs);
        } catch (Exception e) {
            log.error("查询用户{}关注列表失败: {}", userId, e.getMessage(), e);
            return Result.fail("查询关注列表失败");
        }

    }
}



