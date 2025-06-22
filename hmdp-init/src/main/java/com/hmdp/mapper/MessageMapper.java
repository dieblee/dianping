package com.hmdp.mapper;

import com.hmdp.entity.ChatMessage;
import io.lettuce.core.dynamic.annotation.Param;

import java.util.List;

public interface MessageMapper {
    int insertGroupMessage(@Param("shopId") Long shopId,
                           @Param("userId") Long userId,
                           @Param("content") String content);
    List<ChatMessage> getGroupMessages(@Param("shopId") Long shopId,
                                      @Param("limit")Long limit);
}
