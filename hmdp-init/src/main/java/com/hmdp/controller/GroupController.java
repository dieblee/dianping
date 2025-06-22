package com.hmdp.controller;

import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.GroupServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/group")
public class GroupController {

    @Resource
    private GroupServiceImpl groupService;

    @Resource
    private IUserInfoService userInfoService;

    // 加入群聊
    @PostMapping("/join")
    public ResponseEntity<?> joinGroup(@RequestParam Long shopId, @RequestParam Long userId) {
        groupService.addMember(shopId, userId);
        return ResponseEntity.ok().build();
    }

    // 获取群成员
    @GetMapping("/members")
    public ResponseEntity<List<UserInfo>> getGroupMembers(@RequestParam Long shopId) {
        List<String> memberIds = groupService.getMemberIds(shopId);
        List<UserInfo> members = memberIds.stream()
                .map(userInfoService::getById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return ResponseEntity.ok(members);
    }
}