package com.mercemay.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.entity.BlogComments;
import com.mercemay.comment.mapper.BlogCommentsMapper;
import com.mercemay.comment.service.IBlogCommentsService;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {
}
