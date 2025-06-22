package com.hmdp.utils;

import com.alibaba.fastjson2.JSON;
import com.hmdp.entity.ResultMessage;

public class MessageUtils {
    public static String getMessage(boolean isSystemMessage,String fromName,Object message){
        ResultMessage result = new ResultMessage();
        result.setSystem(isSystemMessage);
        result.setMessage((String) message);
        if(fromName != null){
            result.setFormName(fromName);
        }
        return JSON.toJSONString(result);
    }
}
