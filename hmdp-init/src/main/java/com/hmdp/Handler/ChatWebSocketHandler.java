package com.hmdp.Handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Map<Long, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private static final Map<String, String> userInfo = new ConcurrentHashMap<>();
    private static final Map<String, Long> userShopMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("🟢 连接建立: " + session.getId());

        // 解析查询参数
        Map<String, String> params = parseQueryParams(session);
        Long shopId = parseLong(params.get("shopId"), 1L);
        String userId = params.getOrDefault("userId", generateUserId());
        String nickname = params.getOrDefault("nickname", "用户" + new Random().nextInt(1000));

        System.out.printf("➡️ 用户加入: shopId=%s, userId=%s, nickname=%s%n", shopId, userId, nickname);

        // 存储用户信息
        userInfo.put(userId, nickname);
        userShopMap.put(session.getId(), shopId);

        // 加入房间
        Map<String, WebSocketSession> room = roomSessions.computeIfAbsent(shopId, k -> new ConcurrentHashMap<>());
        room.put(userId, session);

        // 发送欢迎消息
        sendWelcomeMessage(session, nickname);

        // 通知所有用户更新列表
        broadcastUserList(shopId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("📩 收到消息: " + message.getPayload());

        JSONObject json = JSONUtil.parseObj(message.getPayload());
        if ("message".equals(json.getStr("type"))) {
            Long shopId = userShopMap.get(session.getId());
            if (shopId != null) {
                String userId = getUserIdBySession(shopId, session);
                if (userId != null) {
                    String content = json.getStr("content");
                    System.out.println("📣 广播消息: " + content);
                    broadcastChatMessage(shopId, userId, content);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("🔴 连接关闭: " + session.getId());

        Long shopId = userShopMap.remove(session.getId());
        if (shopId != null) {
            Map<String, WebSocketSession> room = roomSessions.get(shopId);
            if (room != null) {
                String userId = getUserIdBySession(shopId, session);
                if (userId != null) {
                    String nickname = userInfo.remove(userId);

                    // 移除用户
                    room.remove(userId);

                    // 通知用户离开
                    System.out.printf("⬅️ 用户离开: shopId=%s, userId=%s%n", shopId, userId);
                    broadcastSystemMessage(shopId, nickname + " 离开了聊天室");

                    // 更新用户列表
                    broadcastUserList(shopId);
                }
            }
        }
    }

    // ========== 辅助方法 ==========
    private Map<String, String> parseQueryParams(WebSocketSession session) throws IOException {
        URI uri = session.getUri();
        String query = uri != null ? uri.getQuery() : null;
        if (query == null || query.isEmpty()) return Collections.emptyMap();

        Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 0) {
                String key = pair[0];
                String value = pair.length > 1 ? pair[1] : "";
                params.put(key, value);
            }
        }
        return params;
    }

    private Long parseLong(String value, Long defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String generateUserId() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String getUserIdBySession(Long shopId, WebSocketSession session) {
        Map<String, WebSocketSession> room = roomSessions.get(shopId);
        if (room != null) {
            for (Map.Entry<String, WebSocketSession> entry : room.entrySet()) {
                if (entry.getValue().equals(session)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    // ========== 消息发送方法 ==========
    private void sendWelcomeMessage(WebSocketSession session, String nickname) throws IOException {
        JSONObject message = new JSONObject();
        message.put("type", "system");
        message.put("content", "欢迎加入聊天室，" + nickname + "!");
        session.sendMessage(new TextMessage(message.toString()));
    }

    private void broadcastUserList(Long shopId) throws IOException {
        Map<String, WebSocketSession> room = roomSessions.get(shopId);
        if (room == null) return;

        JSONObject msg = new JSONObject();
        msg.put("type", "userList");

        JSONArray users = new JSONArray();
        for (Map.Entry<String, WebSocketSession> entry : room.entrySet()) {
            String userId = entry.getKey();
            JSONObject user = new JSONObject();
            user.put("id", userId);
            user.put("name", userInfo.get(userId));
            users.add(user);
        }

        msg.put("users", users);
        msg.put("count", users.size());

        broadcast(shopId, msg.toString());
    }

    private void broadcastChatMessage(Long shopId, String userId, String content) throws IOException {
        JSONObject msg = new JSONObject();
        msg.put("type", "message");
        msg.put("user", userInfo.get(userId));
        msg.put("content", content);
        msg.put("timestamp", System.currentTimeMillis());

        broadcast(shopId, msg.toString());
    }

    private void broadcastSystemMessage(Long shopId, String content) throws IOException {
        JSONObject msg = new JSONObject();
        msg.put("type", "system");
        msg.put("content", content);
        msg.put("timestamp", System.currentTimeMillis());

        broadcast(shopId, msg.toString());
    }

    private void broadcast(Long shopId, String message) throws IOException {
        Map<String, WebSocketSession> room = roomSessions.get(shopId);
        if (room == null) return;

        for (WebSocketSession session : room.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (Exception e) {
                    System.err.println("发送消息失败: " + e.getMessage());
                }
            }
        }
    }
}