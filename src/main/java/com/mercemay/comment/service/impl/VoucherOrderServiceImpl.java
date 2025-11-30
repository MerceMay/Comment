package com.mercemay.comment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.SeckillVoucher;
import com.mercemay.comment.entity.VoucherOrder;
import com.mercemay.comment.mapper.VoucherOrderMapper;
import com.mercemay.comment.service.ISeckillVoucherService;
import com.mercemay.comment.service.IVoucherOrderService;
import com.mercemay.comment.service.IVoucherService;
import com.mercemay.comment.utils.RedisIdWorker;
import com.mercemay.comment.utils.SimpleRedisLock;
import com.mercemay.comment.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;


    // 扣减库存方案一，问题：下面方法会出现超卖问题
    // boolean success = seckillVoucherService.update()
    //         .setSql("stock = stock - 1") // mybatis-plus中的setSql方法可以直接编写SQL语句
    //         .eq("voucher_id", voucherId).update(); // where voucher_id = voucherId

    // 扣减库存方案二：乐观锁解决超卖问题，库存充足但并发量大时，只有一个请求能成功
    // boolean success = seckillVoucherService.update()
    //         .setSql("stock = stock - 1")
    //         .eq("voucher_id", voucherId) // where voucher_id = voucherId
    //         .eq("stock", seckillVoucher.getStock()).update(); // where stock = seckillVoucher.getStock()
    // 扣减库存方案三：使用条件更新解决并发问题
    // boolean success = seckillVoucherService.update()
    //         .setSql("stock = stock - 1")
    //         .eq("voucher_id", voucherId)
    //         .gt("stock", 0) // where stock > 0
    //         .update();

    // 单体秒杀
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
    //     if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
    //         return Result.fail("秒杀还未开始，请耐心等待");
    //     }
    //     if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
    //         return Result.fail("秒杀已经结束，敬请期待下次活动");
    //     }
    //     if (seckillVoucher.getStock() < 1) {
    //         return Result.fail("优惠券已经被抢光了，下次早点来哦");
    //     }
    //
    //
    //     Long userId = UserHolder.getUser().getId();
    //     synchronized (userId.toString().intern()) {
    //         // 获取代理对象，以保证事务生效
    //         IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //         return proxy.createVoucherOrder(voucherId);
    //     }
    //
    // }

    // 一人一单方案一：直接在VoucherOrder表中查询是否存在该用户的订单
    // 问题：存在并发问题
    // Long userId = UserHolder.getUser().getId();
    // int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    // if (count > 0) {
    //     return Result.fail("每人限购一张");
    // }

    // 一人一单方案二：使用synchronized关键字解决并发问题
    // 问题：锁的粒度过大，性能低下
    // @Override
    // public synchronized Result createVoucherOrder(Long voucherId) {
    //     Long userId = UserHolder.getUser().getId();
    //     int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    //     if (count > 0) {
    //         return Result.fail("每人限购一张");
    //     }
    //
    //     boolean success = seckillVoucherService.update()
    //             .setSql("stock = stock - 1")
    //             .eq("voucher_id", voucherId)
    //             .gt("stock", 0)
    //             .update();
    //     if (!success) {
    //         return Result.fail("优惠券已经被抢光了，下次早点来哦");
    //     }
    //     VoucherOrder voucherOrder = new VoucherOrder();
    //     long orderId = redisIdWorker.nextId("order");
    //     voucherOrder.setId(orderId);
    //     voucherOrder.setUserId(userId);
    //     voucherOrder.setVoucherId(voucherId);
    //     save(voucherOrder);
    //     return Result.ok(orderId);
    // }

    // 一人一单方案三：通过intern()方法来缩小锁的粒度，提升性能
    // 问题：spring的事务失败时，锁不会释放，导致死锁
    // @Transactional
    // @Override
    // public Result createVoucherOrder(Long voucherId) {
    //     Long userId = UserHolder.getUser().getId();
    //     synchronized (userId.toString().intern()) { // intern()方法确保字符串常量池中只有一份userId的实例
    //         int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    //         if (count > 0) {
    //             return Result.fail("每人限购一张");
    //         }
    //         boolean success = seckillVoucherService.update()
    //                 .setSql("stock = stock - 1")
    //                 .eq("voucher_id", voucherId)
    //                 .gt("stock", 0)
    //                 .update();
    //         if (!success) {
    //             return Result.fail("优惠券已经被抢光了，下次早点来哦");
    //         }
    //         VoucherOrder voucherOrder = new VoucherOrder();
    //         long orderId = redisIdWorker.nextId("order");
    //         voucherOrder.setId(orderId);
    //         voucherOrder.setUserId(userId);
    //         voucherOrder.setVoucherId(voucherId);
    //         save(voucherOrder);
    //         return Result.ok(orderId);
    //     }
    // }

    // 一人一单方案四：将synchronized和@Transactional分开，解决死锁问题
    // 问题：分布式的情况下，只能解决单机的并发问题，无法解决分布式不同节点的并发问题
    // @Transactional
    // @Override
    // public Result createVoucherOrder(Long voucherId) {
    //     Long userId = UserHolder.getUser().getId();
    //     // intern()方法确保字符串常量池中只有一份userId的实例
    //     int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    //     if (count > 0) {
    //         return Result.fail("每人限购一张");
    //     }
    //     boolean success = seckillVoucherService.update()
    //             .setSql("stock = stock - 1")
    //             .eq("voucher_id", voucherId)
    //             .gt("stock", 0)
    //             .update();
    //     if (!success) {
    //         return Result.fail("优惠券已经被抢光了，下次早点来哦");
    //     }
    //     VoucherOrder voucherOrder = new VoucherOrder();
    //     long orderId = redisIdWorker.nextId("order");
    //     voucherOrder.setId(orderId);
    //     voucherOrder.setUserId(userId);
    //     voucherOrder.setVoucherId(voucherId);
    //     save(voucherOrder);
    //     return Result.ok(orderId);
    // }

    // 分布式方案
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
    //     if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //         return Result.fail("秒杀还未开始，请耐心等待");
    //     }
    //     if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
    //         return Result.fail("秒杀已经结束，敬请期待下次活动");
    //     }
    //     if (seckillVoucher.getStock() < 1) {
    //         return Result.fail("优惠券已经被抢光了，下次早点来哦");
    //     }
    //
    //     Long userId = UserHolder.getUser().getId();
    //     // 方案一：使用简单的Redis分布式锁，即SetNx命令实现分布式锁
    //     // SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
    //     // boolean isLock = simpleRedisLock.tryLock(1200);
    //
    //     // 方案二：使用Redisson实现分布式锁
    //     RLock rLock = redissonClient.getLock("lock:distributed:order:" + userId);
    //     boolean isLock = rLock.tryLock();
    //     if (!isLock) { // 说明其他人持有锁
    //         return Result.fail("不允许重复下单");
    //     }
    //     try {
    //         // 获取代理对象，以保证事务生效
    //         IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //         return proxy.createVoucherOrder(voucherId);
    //     } finally {
    //         rLock.unlock();
    //     }
    // }

    // /**
    //  * 阻塞队列实现异步下单
    //  *
    //  */
    // // 秒杀+一人一单，使用Lua脚本实现原子操作，然后把下单请求放入阻塞队列中，异步处理
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     Long userId = UserHolder.getUser().getId();
    //     Long result = stringRedisTemplate.execute(
    //             SECKILL_SCRIPT,
    //             Collections.emptyList(),
    //             voucherId.toString(), userId.toString());
    //     int r = result.intValue();
    //     if (r != 0) {
    //         return Result.fail(r == 1 ? "优惠券已经被抢光了，下次早点来哦" : "不允许重复下单");
    //     }
    //     // 创建订单对象
    //     VoucherOrder voucherOrder = new VoucherOrder();
    //     long orderID = redisIdWorker.nextId("order");
    //     voucherOrder.setId(orderID);
    //     voucherOrder.setUserId(userId);
    //     voucherOrder.setVoucherId(voucherId);
    //     // 放入阻塞队列
    //     orderTasks.add(voucherOrder);
    //     // 获取代理对象
    //     this.proxy = (IVoucherOrderService) AopContext.currentProxy();
    //     // 返回订单ID
    //     return Result.ok(orderID);
    // }
    //
    //
    // // 下面的内容是异步处理下单请求的代码
    // private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    // static {
    //     SECKILL_SCRIPT = new DefaultRedisScript<>();
    //     SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    //     SECKILL_SCRIPT.setResultType(Long.class);
    // }
    //
    // // 阻塞队列
    // private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // // 创建线程池
    // private static final ExecutorService SECKILL_ORDER_EXECUTOR =
    //         Executors.newSingleThreadExecutor();
    //
    // // 在类初始化后执行
    // @PostConstruct
    // private void init() {
    //     SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    // }
    //
    //
    // // 内部类：处理异步下单请求，把下单请求从队列中取出并写入数据库
    // private class VoucherOrderHandler implements Runnable {
    //     @Override
    //     public void run() {
    //         while (true) {
    //             try {
    //                 VoucherOrder voucherOrder = orderTasks.take();
    //                 handleVoucherOrder(voucherOrder);
    //             } catch (Exception e) {
    //                 log.error("处理订单异常", e);
    //             }
    //         }
    //     }
    // }
    //
    // private void handleVoucherOrder(VoucherOrder voucherOrder) {
    //     // 获取用户ID
    //     Long userId = voucherOrder.getId();
    //     // 创建锁对象
    //     RLock rLock = redissonClient.getLock("order_lock:" + userId);
    //     boolean isLock = rLock.tryLock();
    //     if (!isLock) {
    //         log.error("不允许重复下单");
    //         return;
    //     }
    //     try {
    //         this.proxy.createVoucherOrder(voucherOrder);
    //     } finally {
    //         rLock.unlock();
    //     }
    // }
    //
    // private IVoucherOrderService proxy;
    //
    //
    // @Override
    // public void createVoucherOrder(VoucherOrder voucherOrder) {
    //     Long userId = voucherOrder.getUserId();
    //     int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
    //     if (count > 0) {
    //         log.error("不允许重复下单"); // 其实这里不会走到，因为Lua脚本已经判断过一人一单
    //         return;
    //     }
    //     boolean success = seckillVoucherService.update()
    //             .setSql("stock = stock - 1")
    //             .eq("voucher_id", voucherOrder.getVoucherId())
    //             .gt("stock", 0)
    //             .update();
    //     if (!success) {
    //         log.error("优惠券已经被抢光了，下次早点来哦"); // 其实这里不会走到，因为Lua脚本已经判断过库存
    //         return;
    //     }
    //     save(voucherOrder);
    // }


    /**
     * redis消息队列实现异步下单
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;

    // Lua脚本实现秒杀+一人一单+塞入消息队列
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderID = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderID));
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "优惠券已经被抢光了，下次早点来哦" : "不允许重复下单");
        }
        // 获取代理对象
        this.proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回订单ID
        return Result.ok(orderID);
    }

    // 创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR =
            Executors.newSingleThreadExecutor();

    // 在类初始化后执行
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 内部类：处理异步下单请求，把下单请求从队列中取出并写入数据库
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list =
                            stringRedisTemplate.opsForStream().read(
                                    // 读取消息队列中的订单信息
                                    Consumer.from("g1", "c1"), // 消费者组为g1，消费者为c1
                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), // 阻塞2秒
                                    StreamOffset.create("stream.orders", ReadOffset.lastConsumed()) // 从上次消费的位置开始读取
                            );
                    // 判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为空，说明没有消息，继续下一次循环
                        continue;
                    }
                    MapRecord<String, Object, Object> mapRecord = list.get(0); // 获取订单信息
                    Map<Object, Object> map = mapRecord.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true); // 将订单信息转换为VoucherOrder对象
                    // 创建订单
                    createVoucherOrder(voucherOrder);
                    // 确认消息已经被消费 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", mapRecord.getId()); // 消息队列为s1，消费者组为g1
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }
    }

    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {

    }

    private void handlePendingList() {
        while (true) {
            try {
                // 获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                List<MapRecord<String, Object, Object>> list =
                        stringRedisTemplate.opsForStream().read(
                                // 读取消息队列中的订单信息
                                Consumer.from("g1", "c1"), // 消费者组为g1，消费者为c1
                                StreamReadOptions.empty().count(1), // 不阻塞
                                StreamOffset.create("stream.orders", ReadOffset.from("0")) // 从pending-list中读取
                        );
                // 判断订单信息是否为空
                if (list == null || list.isEmpty()) {
                    // 如果为空，说明pending-list没有消息，结束循环
                    break;
                }
                MapRecord<String, Object, Object> mapRecord = list.get(0); // 获取订单信息
                Map<Object, Object> map = mapRecord.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true); // 将订单信息转换为VoucherOrder对象
                // 创建订单
                createVoucherOrder(voucherOrder);
                // 确认消息已经被消费 XACK
                stringRedisTemplate.opsForStream().acknowledge("s1", "g1", mapRecord.getId()); // 消息队列为s1，消费者组为g1
            } catch (Exception e) {
                log.error("处理pendig-list订单异常", e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
}




