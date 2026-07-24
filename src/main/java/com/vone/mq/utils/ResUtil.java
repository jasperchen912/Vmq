package com.vone.mq.utils;


import com.vone.mq.dto.CommonRes;

public class ResUtil {

    public static <T> CommonRes<T> success(T value){
        CommonRes<T> commonRes = new CommonRes<>();
        commonRes.setCode(1);
        commonRes.setMsg("成功");
        commonRes.setData(value);
        return commonRes;
    }

    public static CommonRes<Void> success(){
        CommonRes<Void> commonRes = new CommonRes<>();
        commonRes.setCode(1);
        commonRes.setMsg("成功");
        return commonRes;
    }

    public static CommonRes<Void> error(String msg){
        CommonRes<Void> commonRes = new CommonRes<>();
        commonRes.setCode(-1);
        commonRes.setMsg(msg);
        return commonRes;
    }

    public static <T> CommonRes<T> error(int code, T data){
        CommonRes<T> commonRes = new CommonRes<>();
        commonRes.setCode(code);
        commonRes.setMsg("失败");
        commonRes.setData(data);
        return commonRes;
    }

    public static CommonRes<Void> error(){
        CommonRes<Void> commonRes = new CommonRes<>();
        commonRes.setCode(-1);
        commonRes.setMsg("失败");
        return commonRes;
    }
}
