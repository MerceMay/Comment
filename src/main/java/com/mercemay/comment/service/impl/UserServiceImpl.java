package com.mercemay.comment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mercemay.comment.dto.LoginFormDTO;
import com.mercemay.comment.dto.Result;
import com.mercemay.comment.dto.UserDTO;
import com.mercemay.comment.entity.User;
import com.mercemay.comment.mapper.UserMapper;
import com.mercemay.comment.service.IUserService;
import com.mercemay.comment.utils.RegexUtils;
import com.mercemay.comment.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mercemay.comment.utils.RedisConstants.*;
import static com.mercemay.comment.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // session.setAttribute("code", code);

        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);


        log.debug("发送短信验证码成功，验证码：{}", code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 校验验证码
        // Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }
        User user = query().eq("phone", phone).one(); // one()表示查询一个
        if (user == null) {
            // 用户不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        // session.setAttribute("user", user);
        // 生成token
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY + token;
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 存储用户信息到redis
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 1.获取当前的登录的用户
        Long userId = UserHolder.getUser().getId();
        // 2.截取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接 key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4. 获取今日日期
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入 Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前的登录的用户
        Long userId = UserHolder.getUser().getId();
        // 2.截取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接 key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4. 获取今日日期
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今日位置的所有签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        // 为0，说明未签到，结束
        // 不为0，说明已签到，计数器 +1
        while ((num & 1) != 0) {
            // 让数字与 1 作与运算，得到数字钟的最后一个 bit 位 // 判断 bit 位是否为0
            count++;
            // 数字右移一维，排期最后一个 bit 位，继续下一个 bit 位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user); // 保存用户到数据库
        return user;
    }
}
