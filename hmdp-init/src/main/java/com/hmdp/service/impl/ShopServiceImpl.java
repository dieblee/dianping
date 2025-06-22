package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //互斥锁缓存穿透
//        Shop shop = queryWithLogicalExpire(id);
//        queryWithMutex(id);
        //逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY +id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if(StrUtil.isBlank(shopJson)){
//            return null;
//        }
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if(expireTime.isAfter(LocalDateTime.now())) {
//            return shop;
//        }
//        String lockKey = LOCK_SHOP_KEY+id;
//        boolean isLock = tryLock(lockKey);
//        if(isLock){
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                //重建缓存
//                try {
//                    this.saveShop2Redis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    unLock(lockKey);
//                }
//            });
//        }
//        return shop;
//    }

//    public Shop queryWithMutex(Long id){
//        String key = CACHE_SHOP_KEY +id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if(StrUtil.isNotBlank(shopJson)){
//            //toBean,传输数据
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//
//        }
//        if(shopJson != null){
//            return null;
//        }
//        //实现缓存重建
//        Shop shop = null;
//        String lockKey = "lock:shop:" +id;
//        try {
//            boolean isLock = tryLock(lockKey);
//            if(!isLock){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            shop = getById(id);
//            Thread.sleep(200);
//            if(shop == null ) {
//                stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //toJsonStr数据转换，存入数据
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            unLock(lockKey);
//        }
//        return shop;
//    }


//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY +id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if(StrUtil.isNotBlank(shopJson)){
//            //toBean,传输数据
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if(shopJson != null){
//            return null;
//        }
//        Shop shop = getById(id);
//        if(shop == null ) {
//            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //toJsonStr数据转换，存入数据
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key){
//      stringRedisTemplate.delete(key);
//    }

//    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY +id,JSONUtil.toJsonStr(redisData));
//    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("店铺id不为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result collectShop(Long shopId) {

        UserDTO User = UserHolder.getUser();
        if (User == null) {
            return Result.fail("用户未登录");
        }
        Long userId = User.getId();

        String key = RedisConstants.SHOP_COLLECTION_KEY + shopId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if (Boolean.TRUE.equals(isMember)) {
            return Result.fail("您已经收藏过该店铺");
        }

        // 6. 添加用户到收藏集合
        stringRedisTemplate.opsForSet().add(key, userId.toString());
        return Result.ok("收藏成功");
    }


    @Override
    public boolean isShopCollectedByUser(Long shopId, Long userId) {
        String key = RedisConstants.SHOP_COLLECTION_KEY + shopId;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, userId.toString()));
    }


    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if( x == null || y ==null){
            Page<Shop> page = query()
                    .eq("type_id",typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        int from = (current - 1 ) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end  = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x,y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if(results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        List<Long>ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result ->{
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        String idStr = StrUtil.join(",",ids);
        List<Shop> shops =query().in("id",ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for(Shop shop:shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
