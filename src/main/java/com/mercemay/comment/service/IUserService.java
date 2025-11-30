package com.mercemay.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mercemay.comment.dto.LoginFormDTO;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
