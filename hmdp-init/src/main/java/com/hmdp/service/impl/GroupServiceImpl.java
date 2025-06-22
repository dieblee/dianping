package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.service.IGroupService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
@Service
public class GroupServiceImpl implements IGroupService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // Redis键格式：group:shop:{shopId}:members


    // 加入群聊
    public Result addMember(Long shopId, Long userId) {
        String key = "group:shop:" + shopId + ":members";
        stringRedisTemplate.opsForSet().add(key, userId.toString());

        // 设置过期时间（可选）
        stringRedisTemplate.expire(key, 30, TimeUnit.DAYS);
        return Result.ok();
    }

    // 获取群成员ID集合
    public List<String> getMemberIds(Long shopId) {
        String key = "group:shop:" + shopId + ":members";
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        return members != null ? new ArrayList<>(members) : Collections.emptyList();
    }

    // 检查用户是否在群聊中
    public boolean isMember(Long shopId, Long userId) {
        String key = "group:shop:" + shopId + ":members";
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, userId));
    }
}
