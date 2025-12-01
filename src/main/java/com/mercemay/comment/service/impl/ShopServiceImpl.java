package com.mercemay.comment.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.Shop;
import com.mercemay.comment.mapper.ShopMapper;
import com.mercemay.comment.service.IShopService;
import com.mercemay.comment.utils.CacheClient;
import com.mercemay.comment.utils.RedisData;
import com.mercemay.comment.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
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
        if (x == null || y == null) {
            // 不需要根据坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 计算开始分页位置和结束位置
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 查询redis，按照距离排序，分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId; // 键
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                stringRedisTemplate.opsForGeo().search( // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                        key, // 键
                        GeoReference.fromCoordinate(x, y), // 坐标
                        new Distance(5000), // 半径5km
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end) // 包含距离，分页
                );
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent(); // 获取结果列表
        if (list.size() <= from) {
            // 没有下一页
            return Result.ok(Collections.emptyList());
        }
        // 获取从from到end的部分
        List<Long> ids = new ArrayList<>(list.size()); // 店铺id列表
        Map<String, Distance> distanceMap = new HashMap<>(list.size()); // 店铺id到距离的映射
        list.stream().skip(from).forEach(it -> { // 跳过前from个
            String shopIdStr = it.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = it.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 根据id查询店铺
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list(); // 保持顺序一致
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
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
