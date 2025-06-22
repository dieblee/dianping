package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ChatMessage;

import java.util.List;

public interface IMessageService {

    Result sendMessage(Long toId, String content);

    Result getHistory(Long friendId, Long lastMessageId, int size);

    void saveGroupMessage(Long shopId, Long userId, String content);

    List<ChatMessage> getGroupMessages(Long shopId, int limit);


}
