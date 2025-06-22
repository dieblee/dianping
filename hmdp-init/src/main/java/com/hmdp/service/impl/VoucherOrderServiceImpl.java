package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    String queueName = "stream.orders";
//线程池建立
//    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
        private class VoucherOrderHandler implements Runnable{


        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

    }
            private void handlePendingList(){
                while (true){
                    try {
                        List<MapRecord<String,Object,Object>> list = stringRedisTemplate.opsForStream().read(
                                Consumer.from("g1","c1"),
                                StreamReadOptions.empty().count(1),
                                StreamOffset.create(queueName, ReadOffset.from("0"))
                        );
                        if(list == null || list.isEmpty()){
                            break;
                        }
                        MapRecord<String,Object,Object> record = list.get(0);
                        Map<Object,Object> values = record.getValue();
                        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values,new VoucherOrder(),true);
                        handleVoucherOrder(voucherOrder);
                        stringRedisTemplate.opsForStream().acknowledge(queueName,"g1", record.getId());
                    } catch (Exception e) {
                        log.error("处理pendinglist订单异常",e);
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }

                    }

                }
            }
//    private class VoucherOrderHandler implements Runnable{
//
//        @Override
//        public void run(){
//            while (true){
//                try {
//                    VoucherOrder voucherOrder =  orderTasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常",e);
//                }
//            }
//        }
//    }


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private  IVoucherOrderService proxy;

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败，用户：{}", userId);
            return;
        }
        try {
            log.info("开始处理订单，用户：{}", userId);
            proxy.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("创建订单失败，用户：{}", userId, e);
            throw new RuntimeException("创建订单失败", e);
        } finally {
            lock.unlock();
            log.info("解锁完成，用户：{}", userId);
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        log.info("开始执行秒杀，voucherId={}, userId={}", voucherId, userId);

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId));

        if (result == null) {
            log.error("Redis 脚本执行失败, voucherId={}, userId={}", voucherId, userId);
            return Result.fail("系统异常，请稍后再试");
        }

        int r = result.intValue();
        log.info("Redis 脚本返回结果: {}", r);

        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//
//        log.info("开始执行秒杀，voucherId={}, userId={}", voucherId, userId);
//
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString());
//
//        if (result == null) {
//            log.error("Redis 脚本执行失败, voucherId={}, userId={}", voucherId, userId);
//            return Result.fail("系统异常，请稍后再试");
//        }
//
//        int r = result.intValue();
//        log.info("Redis 脚本返回结果: {}", r);
//
//        if (r != 0) {
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//
//        // 继续执行订单处理
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        orderTasks.add(voucherOrder);
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        return Result.ok(orderId);
//    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀尚未开始");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已经结束");
//        }
//        if(voucher.getStock()<1){
//            return Result.fail("库存不足");
//        }
//
//
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()) {
//        //SimpleRedisLock lock =new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("order:" + userId);
//        //boolean isLock = lock.tryLock(1200);
//        boolean isLock = lock.tryLock();
//        if(!isLock){
//            return Result.fail("不允许重复下单");
//        }
//        try {
//
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();
//        }
////        }
//
//    }


    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        int count = Math.toIntExact(query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count());
        if (count > 0) {
            log.error("用户已经下单，userId={} voucherId={}", userId, voucherOrder.getVoucherId());
            return;
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1 ")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
        if (!success) {
            return ;
        }
        save(voucherOrder);
    }
}
