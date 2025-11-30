package com.mercemay.comment.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.Shop;
import com.mercemay.comment.entity.ShopType;
import com.mercemay.comment.mapper.ShopTypeMapper;
import com.mercemay.comment.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mercemay.comment.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.mercemay.comment.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.从redis查询商铺类型缓存
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        // 2.判断是否存在
        if (shopTypeList != null && !shopTypeList.isEmpty()) {
            List<ShopType> shopTypes = shopTypeList.stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))
                    .toList();
            return Result.ok(shopTypes);
        }
        // 3.不存在，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if (shopTypes.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        // 4.存入redis
        for (ShopType shopType : shopTypes) {
            String json = JSONUtil.toJsonStr(shopType);
            if (shopTypeList != null) {
                shopTypeList.add(json);
            }
        }
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, shopTypeList);
        stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY, CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        // 5.返回
        return Result.ok(shopTypes);
    }
}
