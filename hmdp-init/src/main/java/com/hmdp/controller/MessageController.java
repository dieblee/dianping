package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ChatMessage;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/message")
public class MessageController {

    @Resource
    private IMessageService messageService;
    @PostMapping("/send")
    public Result sendMessage(@RequestParam Long toId,
                              @RequestParam String content) {
        Result message =  messageService.sendMessage(toId, content);
        return Result.ok(message);
    }

    // 获取历史消息
    @GetMapping("/history")
    public Result getHistory(@RequestParam Long friendId,
                             @RequestParam(defaultValue = "0") Long lastMessageId,
                             @RequestParam(defaultValue = "20") int size) {
        Result messages = messageService.getHistory(friendId, lastMessageId, size);
        return Result.ok(messages);
    }

    @GetMapping("/group/history")
    public Result getHistory(
            @RequestParam Long shopId,
            @RequestParam(defaultValue = "50") int limit) {

        List<ChatMessage> messages = messageService.getGroupMessages(shopId,limit);
        return Result.ok(messages);
    }
}
