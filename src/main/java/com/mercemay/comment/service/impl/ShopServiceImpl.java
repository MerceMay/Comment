package com.mercemay.comment.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.Shop;
import com.mercemay.comment.mapper.ShopMapper;
import com.mercemay.comment.service.IShopService;
import com.mercemay.comment.utils.CacheClient;
import com.mercemay.comment.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mercemay.comment.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 互斥锁解决缓存击穿
        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 逻辑过期解决缓存击穿：注意，使用这种方法说明这是一个热点数据，需要提前把数据放入缓存，下面的saveHotKeyToRedisForTest方法可以用来做这个事
        // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }


    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空！");
        }
        // 1.更新数据库
        boolean success = updateById(shop);
        if (!success) {
            return Result.fail("店铺更新失败！");
        }
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();

    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        return null;
    }


    public void saveHotKeyToRedisForTest(Long expireSeconds) {
        List<Shop> shops = query().list();
        for (Shop shop : shops) {
            RedisData redisData = new RedisData();
            redisData.setData(shop);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + shop.getId(),
                    JSONUtil.toJsonStr(redisData));
        }
    }
}
