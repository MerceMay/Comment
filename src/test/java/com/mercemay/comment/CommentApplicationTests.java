package com.mercemay.comment;

import com.mercemay.comment.entity.Shop;
import com.mercemay.comment.service.IShopService;
import com.mercemay.comment.service.impl.ShopServiceImpl;
import com.mercemay.comment.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;

@SpringBootTest
class CommentApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

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
}