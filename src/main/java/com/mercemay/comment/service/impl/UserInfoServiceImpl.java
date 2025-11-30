package com.mercemay.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.entity.UserInfo;
import com.mercemay.comment.mapper.UserInfoMapper;
import com.mercemay.comment.service.IUserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
