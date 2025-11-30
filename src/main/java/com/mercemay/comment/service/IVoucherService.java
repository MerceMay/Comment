package com.mercemay.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.Voucher;

public interface IVoucherService extends IService<Voucher> {
    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
