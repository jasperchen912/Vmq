package com.vone.mq.controller;

import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.PageRes;
import com.vone.mq.entity.PayQrcode;
import com.vone.mq.service.AdminService;
import com.vone.mq.utils.ResUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class AdminController {
    private static final String LOGIN_SESSION_KEY = "login";
    private static final String ASSET_VERSION = "20260724";

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/login")
    public CommonRes login(HttpServletRequest request, String user, String pass){
        if (user==null){
            return ResUtil.error("请输入账号");
        }
        if (pass==null){
            return ResUtil.error("请输入密码");
        }
        CommonRes r = adminService.login(user, pass);
        if (r.getCode()==1){
            request.getSession(true);
            request.changeSessionId();
            request.getSession(false).setAttribute(LOGIN_SESSION_KEY, "1");
        }
        return r;
    }

    @PostMapping("/logout")
    public CommonRes logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResUtil.success();
    }

    @RequestMapping("/admin/getMenu")
    public List<Map<String,Object>> getMenu(HttpSession session){
        if (!isLoggedIn(session)){
            return null;
        }
        List<Map<String,Object>> menu = new ArrayList<>();
        Map<String,Object> node = new HashMap<>();
        node.put("name","系统设置");
        node.put("type","url");
        node.put("url",versioned("admin/setting.html"));
        menu.add(node);

        node = new HashMap<>();
        node.put("name","监控端设置");
        node.put("type","url");
        node.put("url",versioned("admin/jk.html"));
        menu.add(node);


        List<Map<String,Object>> menu1 = new ArrayList<>();

        node = new HashMap<>();
        node.put("name","添加");
        node.put("type","url");
        node.put("url",versioned("admin/addwxqrcode.html"));
        menu1.add(node);

        node = new HashMap<>();
        node.put("name","管理");
        node.put("type","url");
        node.put("url",versioned("admin/wxqrcodelist.html"));
        menu1.add(node);

        node = new HashMap<>();
        node.put("name","微信二维码");
        node.put("type","menu");
        node.put("node",menu1);
        menu.add(node);


        List<Map<String,Object>> menu2 = new ArrayList<>();

        node = new HashMap<>();
        node.put("name","添加");
        node.put("type","url");
        node.put("url",versioned("admin/addzfbqrcode.html"));
        menu2.add(node);

        node = new HashMap<>();
        node.put("name","管理");
        node.put("type","url");
        node.put("url",versioned("admin/zfbqrcodelist.html"));
        menu2.add(node);

        node = new HashMap<>();
        node.put("name","支付宝二维码");
        node.put("type","menu");
        node.put("node",menu2);
        menu.add(node);

        node = new HashMap<>();
        node.put("name","订单列表");
        node.put("type","url");
        node.put("url",versioned("admin/orderlist.html"));
        menu.add(node);

        node = new HashMap<>();
        node.put("name","Api说明");
        node.put("type","url");
        node.put("url",versioned("../api.html"));
        menu.add(node);
        return menu;
    }
    @RequestMapping("/admin/saveSetting")
    public CommonRes saveSetting(HttpSession session,String user,String pass,String notifyUrl,String returnUrl,String key,String wxpay,String zfbpay,String close,String payQf){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }
        return adminService.saveSetting(user, pass, notifyUrl, returnUrl, key, wxpay, zfbpay, close, payQf);
    }
    @RequestMapping("/admin/getSettings")
    public CommonRes getSettings(HttpSession session){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }
        return adminService.getSettings();
    }
    @RequestMapping("/admin/getOrders")
    public PageRes getOrders(HttpSession session,Integer page, Integer limit, Integer type, Integer state){
        if (!isLoggedIn(session)){
            PageRes p = new PageRes();
            p.setCode(-1);
            p.setMsg("未登录");
            return p;
        }
        return adminService.getOrders(page, limit, type,state);
    }
    @RequestMapping("/admin/setBd")
    public CommonRes setBd(HttpSession session,Integer id){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }
        if (id==null){
            return ResUtil.error();
        }
        return adminService.setBd(id);
    }
    @RequestMapping("/admin/getPayQrcodes")
    public PageRes getPayQrcodes(HttpSession session,Integer page, Integer limit, Integer type){
        if (!isLoggedIn(session)){
            PageRes p = new PageRes();
            p.setCode(-1);
            p.setMsg("未登录");
            return p;
        }
        return adminService.getPayQrcodes(page, limit, type);
    }
    @RequestMapping("/admin/delPayQrcode")
    public CommonRes delPayQrcode(HttpSession session,Long id){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }

        return adminService.delPayQrcode(id);
    }
    @RequestMapping("/admin/addPayQrcode")
    public CommonRes addPayQrcode(HttpSession session,PayQrcode payQrcode){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }
        return adminService.addPayQrcode(payQrcode);
    }
    @RequestMapping("/admin/getMain")
    public CommonRes getMain(HttpSession session){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }
        return adminService.getMain();
    }

    @RequestMapping("/admin/delOrder")
    public CommonRes delOrder(HttpSession session,Long id){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }

        return adminService.delOrder(id);
    }

    @RequestMapping("/admin/delGqOrder")
    public CommonRes delGqOrder(HttpSession session){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }

        return adminService.delGqOrder();
    }
    @RequestMapping("/admin/delLastOrder")
    public CommonRes delLastOrder(HttpSession session){
        if (!isLoggedIn(session)){
            return ResUtil.error("未登录");
        }

        return adminService.delLastOrder();
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute(LOGIN_SESSION_KEY) != null;
    }

    private String versioned(String path) {
        return path + "?v=" + ASSET_VERSION;
    }
}
