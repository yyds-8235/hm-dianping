package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;

    private final RedisIdWorker redisIdWorker;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 添加一个运行标志位
    private volatile boolean isRunning = true;

    // 建议配置化
    private final String consumerName = "c-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String GROUP_NAME = "g1";
    private static final String STREAM_NAME = "stream.orders";

    @Lazy
    @Resource
    private IVoucherOrderService thisVoucherOrderService;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @PreDestroy
    public void destroy() {
        this.isRunning = false;
        // 可以在这里关闭线程池
    }

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            while (isRunning) {
                try {
                    // 1. 获取消息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, consumerName),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_NAME, ReadOffset.lastConsumed())
                    );

                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    // 2. 处理消息
                    processRecord(list.get(0));

                } catch (Exception e) {
                    log.error("处理订单流异常", e);
                    handlePendingList();
                }
            }
        });
    }

    private void handlePendingList() {
        while (isRunning) {
            try {
                // 读取 PEL 中的消息 (ReadOffset.from("0"))
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP_NAME, consumerName),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM_NAME, ReadOffset.from("0"))
                );

                if (list == null || list.isEmpty()) {
                    break; // PEL 为空，退出循环
                }

                // 处理消息
                processRecord(list.get(0));

            } catch (Exception e) {
                log.error("处理Pending订单异常", e);
                try {
                    Thread.sleep(50); // 稍微休眠，避免 CPU 空转
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record) {
        int retryCount = 0;
        int maxRetries = 3;

        while (retryCount < maxRetries) {
            try {
                Map<Object, Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                handleVoucherOrder(voucherOrder);

                // 成功则 ACK
                stringRedisTemplate.opsForStream().acknowledge(STREAM_NAME, GROUP_NAME, record.getId());
                return; // 处理成功，退出
            } catch (Exception e) {
                retryCount++;
                log.warn("订单处理失败，当前重试次数: {}/{}，消息ID: {}", retryCount, maxRetries, record.getId());
                if (retryCount >= maxRetries) {
                    log.error("订单处理多次失败，已确认为异常消息，执行ACK并记录日志。MessageId: {}", record.getId(), e);
                    // 【重要】强制 ACK，将其移出 PEL，防止死循环
                    // TODO: 建议将该 voucherOrder 写入数据库的 "seckill_error_log" 表，方便人工排查
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_NAME, GROUP_NAME, record.getId());
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象（兜底）
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取失败,返回错误或者重试
            throw new RuntimeException("发送未知错误");
        }
        try {
            thisVoucherOrderService.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }

    }

    @Override
    public Result seckillVoucher(Long voucherId) {

        //获取用户
        UserDTO user = UserHolder.getUser();
        //获取订单id
        Long orderId = redisIdWorker.nextId("order");
        //执行lua脚本
        Long res = stringRedisTemplate.execute(
                SECKILL_SCRIPT
                , Collections.emptyList()
                , voucherId.toString()
                , user.getId().toString()
                , orderId.toString());
        //判断结果是否为0
        int r = res.intValue();
        if (r != 0) {
            //不为0 没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "禁止重复下单");
        }
        return Result.ok(orderId);

//异步        //获取用户
//        UserDTO user = UserHolder.getUser();
//        //执行lua脚本
//        Long res = stringRedisTemplate.execute(
//                SECKILL_SCRIPT
//                , Collections.emptyList()
//                , voucherId.toString()
//                , user.getId().toString());
//        //判断结果是否为0
//        int r = res.intValue();
//        if (r != 0) {
//            //不为0 没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "禁止重复下单");
//        }
//        //为0有购买资格
//        Long orderId = redisIdWorker.nextId("order");
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        voucherOrder.setId(orderId);
//        //存入阻塞队列
//        orderTasks.add(voucherOrder);
//        //返回订单id
//        return Result.ok(orderId);

//性能较低        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        RLock lock = redissonClient.getLock("order:" + userId);
//        boolean isLock = lock.tryLock();
//        if (!isLock){
//            return Result.fail("一人一单哦！");
//        }
//        try {
//            return thisVoucherOrderService.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }

//不可重入，无超时重试        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(1200L);
//        if (!isLock) {
//            //获取失败,返回错误或者重试
//            return Result.fail("一人一单哦！");
//        }
//        try {
//            return thisVoucherOrderService.createVoucherOrder(voucherId);
//        } finally {
//            lock.unLock();
//        }

//  单体      synchronized (userId.toString().intern()) {
//            return thisVoucherOrderService.createVoucherOrder(voucherId);
//        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(VoucherOrder voucher) {
        Long userId = voucher.getUserId();
        Long voucherId = voucher.getVoucherId();

        Long count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过");
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        save(voucherOrder);
        return Result.ok(orderId);
    }

}
