package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.constant.RedisConstants;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.TokenHolder;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新token的有效期
 */

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 拦截所有路径,刷新token的有效期
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");

        //将token保存进线程当中
        TokenHolder.saveToken(token);

        //2.如果未携带token,直接放行即可
        if(StrUtil.isBlank(token)){
            return true;
        }

        //3.根据token去查找redis当中是否有用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);

        //4.如果redis中没有用户信息,则直接放行
        if(userMap.isEmpty()){
            return true;
        }

        //5.将map集合转化为对应的DTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //6.将用户保存到当前线程中,便于后面拦截器的判断操作
        UserHolder.saveUser(userDTO);

        //7.刷新token的有效期,延长30分钟
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token , RedisConstants.LOGIN_USER_TTL , TimeUnit.MINUTES);

        //8.统一放行处理
        return true;
    }
}
