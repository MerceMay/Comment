package com.mercemay.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.entity.Follow;
import com.mercemay.comment.mapper.FollowMapper;
import com.mercemay.comment.service.IFollowService;
import org.springframework.stereotype.Service;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

}
