package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.constant.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.constant.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * 实现用户登陆的拦截操作
 */
public class LoginInterceptor  implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");

        //2.如果token为空,说明用户未完成登录,进行拦截
        if(StrUtil.isBlank(token)){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        //3.根据token从redis中获取到用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);

        //4.将userMap转换为userDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //5.将userDTO放进ThreadLocal中
        UserHolder.saveUser(userDTO);

        //6.为redis中的用户延长半个小时的有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token ,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }
}
