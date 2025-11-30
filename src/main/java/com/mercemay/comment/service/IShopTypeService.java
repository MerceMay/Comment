package com.mercemay.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.ShopType;

public interface IShopTypeService extends IService<ShopType> {
    Result queryTypeList();
}
