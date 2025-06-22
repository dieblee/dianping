package com.hmdp.ws;

import com.alibaba.fastjson2.JSON;
import com.hmdp.config.GetHttpSessionConfig;
import com.hmdp.entity.Message;
import com.hmdp.entity.User;
import com.hmdp.utils.MessageUtils;
import org.apache.juli.OneLineFormatter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ServerEndpoint(value = "/chat",configurator = GetHttpSessionConfig.class,decoders = MessageDecoder.class)
@Component
public class ChatEndpoint {

    private static final Map<String,Session> onlineUsers = new ConcurrentHashMap<>();

    private HttpSession httpSession;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private void broadcastAllUsers(String message) throws IOException {
        try{
        Set<Map.Entry<String,Session>> entries = onlineUsers.entrySet();
        for(Map.Entry<String,Session> entry:entries){
            Session  session = entry.getValue();
            session.getBasicRemote().sendText(message);
        }
        }catch (Exception e){

        }

    }

    public Set getFriends(){
        Set<String> set = onlineUsers.keySet();
        return set;
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) throws IOException {
        this.httpSession = (HttpSession) config.getUserProperties().get(HttpSession.class.getName());
        String token = (String) httpSession.getAttribute("loginToken");
        if (token == null) {
            return;
        }

        // 2. 构建 Redis 的 key
        String key = "login:token:" + token;

        // 3. 从 Redis 获取用户信息
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        onlineUsers.put((String) entries.get("nickName"),session);
        String message =  MessageUtils.getMessage(true,null,getFriends());
        broadcastAllUsers(message);
    }

    @OnMessage
    public void onMessage(Message msg) throws IOException {
//        Message msg = JSON.parseObject(String.valueOf(message),Message.class);
        String toName =  msg.getToName();
        String mess = msg.getMessage();
        Session session = onlineUsers.get(toName);
        String token = (String) this.httpSession.getAttribute("loginToken");
        String key = "login:token:" + token;

        // 3. 从 Redis 获取用户信息
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        String msg1 =MessageUtils.getMessage(false,(String) entries.get("nickName"),mess);
        session.getBasicRemote().sendText(msg1);

    }
    @OnClose
    public void onClose(Session session) throws IOException {
        String token = (String) httpSession.getAttribute("loginToken");
        if (token == null) {
            return;
        }

        // 2. 构建 Redis 的 key
        String key = "login:token:" + token;

        // 3. 从 Redis 获取用户信息
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        onlineUsers.remove((String) entries.get("nickName"));
        String message =  MessageUtils.getMessage(true,null,getFriends());
        broadcastAllUsers(message);
    }
}
