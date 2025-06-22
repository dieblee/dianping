package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result show() {
        String key = "shop:type:";
        String shoptype= stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(shoptype)){
            List<ShopType> shopTypeList = JSONUtil.toList(shoptype, ShopType.class);
            return Result.ok(shopTypeList);
        }
        List<ShopType> ShopTypes = query().orderByAsc("sort").list();
        if(ShopTypes.isEmpty()){
            return Result.fail("未查找到商铺信息");
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(ShopTypes));
        return Result.ok(ShopTypes);
    }
}
