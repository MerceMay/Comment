package com.mercemay.comment.controller;

import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.ShopType;
import com.mercemay.comment.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();
//        return Result.ok(typeList);
        return typeService.queryTypeList();
    }
}
