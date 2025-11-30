package com.mercemay.comment.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mercemay.comment.dto.UserDTO;
import com.mercemay.comment.entity.User;
import com.mercemay.comment.utils.RedisConstants;
import com.mercemay.comment.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object hander) throws Exception {
        // 判断是否有用户登录
        if (UserHolder.getUser() == null) { // 说明上一个拦截器没有存取用户信息，未登录
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
