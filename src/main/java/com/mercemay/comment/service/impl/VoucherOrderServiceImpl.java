package com.mercemay.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.SeckillVoucher;
import com.mercemay.comment.entity.VoucherOrder;
import com.mercemay.comment.mapper.VoucherOrderMapper;
import com.mercemay.comment.service.ISeckillVoucherService;
import com.mercemay.comment.service.IVoucherOrderService;
import com.mercemay.comment.utils.RedisIdWorker;
import com.mercemay.comment.utils.SimpleRedisLock;
import com.mercemay.comment.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


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
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // intern()方法确保字符串常量池中只有一份userId的实例
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("每人限购一张");
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("优惠券已经被抢光了，下次早点来哦");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }

    // 分布式方案
    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还未开始，请耐心等待");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束，敬请期待下次活动");
        }
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("优惠券已经被抢光了，下次早点来哦");
        }

        Long userId = UserHolder.getUser().getId();
        // 分布式锁
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = simpleRedisLock.tryLock(1200);
        if (!isLock) { // 说明其他人持有锁
            return Result.fail("不允许重复下单");
        }
        try {
            // 获取代理对象，以保证事务生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            simpleRedisLock.unlock();
        }
    }
}




