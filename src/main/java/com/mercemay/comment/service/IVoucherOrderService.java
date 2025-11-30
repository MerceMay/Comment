package com.mercemay.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
