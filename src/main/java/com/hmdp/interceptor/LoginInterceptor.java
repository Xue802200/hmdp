package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/**
 * 实现用户登陆的拦截操作
 */
public class LoginInterceptor  implements HandlerInterceptor {
    /*
    进行用户拦截的具体操作
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.从ThreadLocal中获取当前用户的信息
        UserDTO userDTO = UserHolder.getUser();

        //2.如果userDTO为空,说明没有完成登录校验,进行拦截
        if(userDTO == null){
            //用户没有登录,向前端返回401状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        //3.用户不为空,直接放行即可
        return true;
    }
}
