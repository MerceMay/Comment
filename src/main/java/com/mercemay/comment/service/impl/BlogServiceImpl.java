package com.mercemay.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.entity.Blog;
import com.mercemay.comment.mapper.BlogMapper;
import com.mercemay.comment.service.IBlogService;
import org.springframework.stereotype.Service;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
