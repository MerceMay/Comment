package com.mercemay.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.Shop;

public interface IShopService extends IService<Shop> {
    Result queryById(Long id);

    Result updateShop(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
