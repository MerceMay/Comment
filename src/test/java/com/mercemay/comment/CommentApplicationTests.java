package com.mercemay.comment;

import com.mercemay.comment.entity.Shop;
import com.mercemay.comment.service.IShopService;
import com.mercemay.comment.service.impl.ShopServiceImpl;
import com.mercemay.comment.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class CommentApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Test
    void saveHotKeyToRedis() {
        shopService.saveHotKeyToRedisForTest(10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("Generated ID: " + id);
            }
            countDownLatch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            new Thread(task).start();
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("Total time: " + (end - start) + " ms");
    }

    @Test
    void testRedisson() throws Exception {
        RLock lock = redissonClient.getLock("anyLock");
        boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (isLock) {
            try {
                Thread.sleep(5000);
                System.out.println("Lock acquired successfully");
            } finally {
                lock.unlock();
                System.out.println("Lock released");
            }
        }
    }
}