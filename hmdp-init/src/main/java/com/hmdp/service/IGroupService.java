package com.hmdp.service;


import com.hmdp.dto.Result;

import java.util.List;

public interface IGroupService {



    // 加入群聊
    Result addMember(Long shopId, Long userId);


    // 获取群成员ID集合
    List<String> getMemberIds(Long shopId) ;


    // 检查用户是否在群聊中
    boolean isMember(Long shopId, Long userId) ;


}