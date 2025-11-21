package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;


/**
 * LoginInterceptor
 *
 * @Description
 * @Author hanchenyang
 * @Date 2025/11/21 15:02
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (UserHolder.getUser() == null) {
            // 4.不存在，拦截，返回401
            response.setStatus(401);
            return false;
        }

        return true;
    }

}
