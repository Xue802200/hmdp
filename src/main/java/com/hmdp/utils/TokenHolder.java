package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public class TokenHolder {
    private static final ThreadLocal<String> tl = new ThreadLocal<>();

    public static void saveToken(String token){
        tl.set(token);
    }

    public static String getToken(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
