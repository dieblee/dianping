package com.hmdp.entity;// ChatMessage.java
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private Long fromId;
    private String fromName;
    private Long toId;
    private String toName;
    private String content;
    private LocalDateTime createTime;
}