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
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mercemay.comment.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class CommentApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    @Test
    void localShopData() {
        List<Shop> shops = shopService.list(); // 获取所有店铺数据
        Map<Long, List<Shop>> map = shops.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId)); // 按类型分组
        // 分批写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            List<Shop> shopList = entry.getValue(); // 获取同类型的店铺列表
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shopList.size());
            // 把店铺位置写入Redis
            for (Shop shop : shopList) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}