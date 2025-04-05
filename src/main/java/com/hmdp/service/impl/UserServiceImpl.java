package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.constant.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.constant.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不合法，返回错误信息
            return Result.fail("填写的手机号有误,请检查后重新输入");
        }

        //3.合法,生成一个短信验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存到session当中并设置两分钟的有效期
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码
        log.info("发送的验证码为:{}",code);

        //6.返回
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.判断手机号是否符合规范
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            //2.不符合规范抛出异常
            return Result.fail(SystemConstants.PHONE_ERROR);
        }

        //3.从redis中获取到验证码
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());  //验证码code
        String cacheCode = loginForm.getCode();                                                                     //用户输入的验证码code

        //4.验证码错误,抛出异常
        if(cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail(SystemConstants.CODE_ERROR);
        }

        //4.验证码正确,根据手机号去查询数据库当中是否有这个用户
        User user = query().eq("phone", loginForm.getPhone()).one();

        //5.不存在这个用户,则自动完成注册
        if(user == null) {
            user = saveUserWithPhone(loginForm.getPhone());
        }

        //6.存在这个用户,将这个user存储到redis中
        //6.1首先将user转化为userDTO,防止用户信息的泄露
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //6.2将这个dto转化为对应的map存储到redis中
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(user.getId()));
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon());
//
//        Map<String,Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
//                CopyOptions.create()
//                        .setIgnoreNullValue(true)
//                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));

        //6.3生成一个随机的token作为redis中的key,并设置有效期
        String token = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token,userMap);  //login:user:...
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //7.返回token给前端
        return Result.ok(token);
    }

    /*
    注册用户,将用户信息保存到数据库当中
     */
    private User saveUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(6));
        save(user);

        return user;
    }

}
