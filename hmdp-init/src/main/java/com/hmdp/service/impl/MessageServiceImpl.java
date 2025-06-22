package com.hmdp.service.impl;

import com.alibaba.fastjson2.JSON;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.ChatMessage;
import com.hmdp.mapper.MessageMapper;
import com.hmdp.service.IMessageService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class MessageServiceImpl implements IMessageService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private MessageMapper messageMapper;

    @Override
    public Result sendMessage(Long toId, String content) {
        try {
            UserDTO fromUser = UserHolder.getUser();
            // 获取当前用户信息
            if (fromUser == null) {
                return Result.fail("用户未登录");
            }

            // 构建消息对象
            ChatMessage message = new ChatMessage();
            message.setId(System.currentTimeMillis());
            message.setFromId(fromUser.getId());
            message.setFromName(fromUser.getNickName());
            message.setToId(toId);
            message.setToName(userService.getById(toId).getNickName());
            message.setContent(content);
            message.setCreateTime(LocalDateTime.now());

            // 序列化消息
            String messageJson = JSON.toJSONString(message);

            // 获取聊天通道
            String channel = buildChatChannel(fromUser.getId(), toId);

            // 1. 将消息存储到Redis ZSet中（持久化存储）
            stringRedisTemplate.opsForZSet().add(
                    channel,
                    messageJson,
                    message.getId()
            );

            // 2. 将消息添加到联系人列表（双方）
            String contactsKey1 = RedisConstants.MESSAGE_CONTACTS_KEY + fromUser.getId();
            stringRedisTemplate.opsForZSet().add(contactsKey1, toId.toString(), message.getId());

            String contactsKey2 = RedisConstants.MESSAGE_CONTACTS_KEY + toId;
            stringRedisTemplate.opsForZSet().add(contactsKey2, fromUser.getId().toString(), message.getId());

            // 3. 发布消息到Redis频道（实时通知）
            stringRedisTemplate.convertAndSend(channel, messageJson);

            return Result.ok(message);

        } catch (RedisSystemException e) {
            log.error("发送消息失败: {}", e.getMessage());
            return Result.fail("发送消息失败");
        } catch (Exception e) {
            log.error("系统错误: {}", e.getMessage());
            return Result.fail("系统错误");
        }
    }

    @Override
    public Result getHistory(Long friendId, Long lastMessageId, int size) {
        try {
            UserDTO currentUser = UserHolder.getUser();
            if (currentUser == null) {
                log.warn("用户未登录, 无法获取消息历史");
                return Result.fail("用户未登录");
            }

            // 构建channel名
            String channel = buildChatChannel(currentUser.getId(), friendId);

            // 查询范围设置
            double minScore = lastMessageId == 0 ? 0 : lastMessageId;
            double maxScore = Long.MAX_VALUE;

            // 执行查询并记录结果
            Set<ZSetOperations.TypedTuple<String>> messages = stringRedisTemplate.opsForZSet().rangeByScoreWithScores(channel, minScore, maxScore);
            //实现查询范围限制
//            Set<ZSetOperations.TypedTuple<String>> messages;
//            if (lastMessageId == 0) {
//                // 第一页查询最大的size条消息
//                messages = stringRedisTemplate.opsForZSet()
//                        .reverseRangeByScoreWithScores(
//                                channel,
//                                0, Long.MAX_VALUE,
//                                0, size
//                        );
//            } else {
//                // 后续分页：查询比lastMessageId小的size条消息
//                messages = stringRedisTemplate.opsForZSet()
//                        .reverseRangeByScoreWithScores(
//                                channel,
//                                0, lastMessageId - 1, // 只查询比上条消息更早的消息
//                                0, size
//                        );
//            }


            if (messages == null || messages.isEmpty()) {
                log.info("在通道 [{}] 中未找到消息", channel);
                return Result.ok(Collections.emptyList());
            }

            // 解析消息
            List<ChatMessage> chatMessages = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : messages) {
                try {
                    String json = tuple.getValue();
                    Double score = tuple.getScore();
                    // 调试输出 - 查看原始数据
                    log.debug("原始值: {}, score: {}", json, score);
                    // 直接解析整个JSON字符串
                    ChatMessage message = JSON.parseObject(json, ChatMessage.class);
                    // 确保ID正确设置 - 使用score值
                    message.setId(score.longValue());
                    // 添加调试信息
                    log.debug("解析后的消息: ID={}, From={}, To={}, Content={}",
                            message.getId(), message.getFromId(), message.getToId(), message.getContent());

                    chatMessages.add(message);
                } catch (Exception e) {
                    log.error("解析消息失败: {}", e.getMessage());
                }
            }

            // 按时间顺序排序（从旧到新）
            chatMessages.sort(Comparator.comparingLong(ChatMessage::getId));


            log.info("成功解析 {} 条消息", chatMessages.size());
            return Result.ok(chatMessages);
        } catch (RedisSystemException e) {
            log.error("Redis错误: {}", e.getMessage(), e);
            return Result.fail("Redis服务错误");
        } catch (Exception e) {
            log.error("系统错误: {}", e.getMessage(), e);
            return Result.fail("系统内部错误");
        }

    }

    @Override
    @Transactional
    public void saveGroupMessage(Long shopId, Long userId, String content) {
        try {
            log.info("尝试保存群聊消息: shopId={}, userId={}, content={}", shopId, userId, content);
            int rows = messageMapper.insertGroupMessage(shopId, userId, content);

            if (rows != 1) {
                log.error("保存群聊消息失败! 影响行数: {}", rows);
                throw new RuntimeException("保存群消息失败");
            }
            log.info("群聊消息保存成功");
        } catch (Exception e) {
            log.error("保存群聊消息时发生异常", e);
            throw new RuntimeException("保存群消息失败", e);
        }
    }

    @Override
    public List<ChatMessage> getGroupMessages(Long shopId, int limit) {
        List<ChatMessage> rows = messageMapper.getGroupMessages(shopId, (long) limit);
        if (rows == null) {
            throw new RuntimeException("获取群消息失败");
        }
        return rows;
    }


    private String buildChatChannel(Long userId1, Long userId2) {
        Long small = Math.min(userId1, userId2);
        Long large = Math.max(userId1, userId2);
        return RedisConstants.CHAT_CHANNEL_PREFIX + small + ":" + large;
    }
}
