package com.hmdp.ws;

import com.alibaba.fastjson2.JSON;
import com.hmdp.entity.Message;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

public class MessageDecoder implements Decoder.Text<Message> {

    @Override
    public Message decode(String messageStr) throws DecodeException {
        // 使用 FastJSON 解析 JSON 字符串到 Message 对象
        return JSON.parseObject(messageStr, Message.class);
    }

    @Override
    public boolean willDecode(String messageStr) {
        // 验证是否是有效的 JSON 格式（可选但推荐）
        return messageStr != null && !messageStr.isEmpty();
    }

    @Override
    public void init(EndpointConfig config) {
        // 初始化方法（可选）
    }

    @Override
    public void destroy() {
        // 销毁方法（可选）
    }
}
