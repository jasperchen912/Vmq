package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.PayQrcodeDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.PageRes;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.PayQrcode;
import com.vone.mq.entity.Setting;
import com.vone.mq.utils.HttpRequest;
import com.vone.mq.utils.ResUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;


import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.DigestUtils;

import jakarta.persistence.criteria.*;
import java.nio.charset.StandardCharsets;

@Service
public class AdminService {

    private static final String DASHBOARD_QUERY = """
            SELECT
              COALESCE(SUM(CASE WHEN create_date >= ? AND create_date <= ? THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN create_date >= ? AND create_date <= ? AND state IN (1, 2) THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN create_date >= ? AND create_date <= ? AND state = -1 THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN create_date >= ? AND create_date <= ? AND state IN (1, 2) THEN price ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN state = 1 THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN state IN (1, 2) THEN price ELSE 0 END), 0)
            FROM pay_order
            """;

    private final SettingDao settingDao;
    private final PayOrderDao payOrderDao;
    private final TmpPriceDao tmpPriceDao;
    private final PayQrcodeDao payQrcodeDao;
    private final JdbcTemplate jdbcTemplate;

    public AdminService(
            SettingDao settingDao,
            PayOrderDao payOrderDao,
            TmpPriceDao tmpPriceDao,
            PayQrcodeDao payQrcodeDao,
            JdbcTemplate jdbcTemplate) {
        this.settingDao = settingDao;
        this.payOrderDao = payOrderDao;
        this.tmpPriceDao = tmpPriceDao;
        this.payQrcodeDao = payQrcodeDao;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommonRes login(String user,String pass){
        Optional<Setting> userSetting = settingDao.findById("user");
        Optional<Setting> passwordSetting = settingDao.findById("pass");
        if (userSetting.isEmpty() || passwordSetting.isEmpty()) {
            return ResUtil.error("账号或密码不正确");
        }
        String u = userSetting.get().getVvalue();
        if (!user.equals(u)){
            return ResUtil.error("账号或密码不正确");
        }
        String p = passwordSetting.get().getVvalue();
        if (!pass.equals(p)){
            return ResUtil.error("账号或密码不正确");
        }

        return ResUtil.success();
    }
    public CommonRes saveSetting(String user,String pass,String notifyUrl,String returnUrl,String key,String wxpay,String zfbpay,String close,String payQf){
        Setting s = new Setting();
        s.setVkey("user");
        s.setVvalue(user);
        settingDao.save(s);

        s.setVkey("pass");
        s.setVvalue(pass);
        settingDao.save(s);

        s.setVkey("notifyUrl");
        s.setVvalue(notifyUrl);
        settingDao.save(s);

        s.setVkey("returnUrl");
        s.setVvalue(returnUrl);
        settingDao.save(s);

        s.setVkey("key");
        s.setVvalue(key);
        settingDao.save(s);

        s.setVkey("wxpay");
        s.setVvalue(wxpay);
        settingDao.save(s);
        s.setVkey("zfbpay");
        s.setVvalue(zfbpay);
        settingDao.save(s);

        s.setVkey("payQf");
        s.setVvalue(payQf);
        settingDao.save(s);

        s.setVkey("close");
        s.setVvalue(close);
        settingDao.save(s);
        return ResUtil.success();
    }
    public CommonRes getSettings(){
        List<Setting> settings = settingDao.findAll();
        Map<String,String> map = new HashMap<>();
        for (Setting s:settings ) {
            map.put(s.getVkey(),s.getVvalue());
        }
        return ResUtil.success(map);
    }

    public PageRes getOrders(Integer page, Integer limit, Integer type, Integer state){

        Sort order = Sort.by(
                Sort.Order.desc("createDate"),
                Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page - 1, limit, order);

        Specification<PayOrder> specification = new Specification<PayOrder>() {
            @Override
            public Predicate toPredicate(Root<PayOrder> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder cb) {
                List<Predicate> list = new ArrayList<Predicate>();

                if (type!=null) {
                    list.add(cb.equal(root.get("type").as(int.class), type));
                }

                if (state!=null) {
                    list.add(cb.equal(root.get("state").as(int.class), state));
                }
                return cb.and(list.toArray(new Predicate[list.size()]));
            }
        };
        Page<PayOrder> payOrders = payOrderDao.findAll(specification, pageable);

        PageRes list = PageRes.success(payOrders.getTotalElements(),payOrders.getContent());
        return list;
    }

    public CommonRes setBd(Integer id){
        PayOrder payOrder = payOrderDao.findById(id.longValue()).orElse(null);
        if (payOrder==null){
            return ResUtil.error("订单不存在");
        }
        String key = settingDao.findById("key").get().getVvalue();
        String sign = payOrder.getPayId()+payOrder.getParam()+payOrder.getType()+payOrder.getPrice()+payOrder.getReallyPrice()+key;
        Map<String, Object> callbackParameters = new LinkedHashMap<>();
        callbackParameters.put("payId", payOrder.getPayId());
        callbackParameters.put("param", payOrder.getParam());
        callbackParameters.put("type", payOrder.getType());
        callbackParameters.put("price", payOrder.getPrice());
        callbackParameters.put("reallyPrice", payOrder.getReallyPrice());
        callbackParameters.put("sign", md5(sign));
        String p = HttpRequest.formEncode(callbackParameters);

        String url = payOrder.getNotifyUrl();
        if (url==null || url.equals("")){
            url = settingDao.findById("notifyUrl").get().getVvalue();
            if (url==null || url.equals("")){
                return ResUtil.error("您还未配置异步通知地址，请现在系统配置中配置");
            }
        }

        String res = HttpRequest.sendGet(url,p);

        if (res!=null && res.equals("success")){
            if (payOrder.getState()==0){
                tmpPriceDao.delprice(payOrder.getType()+"-"+payOrder.getReallyPrice());
            }
            payOrderDao.setState(1,payOrder.getId());
            return ResUtil.success();
        }else{
            return ResUtil.error(-2,res);
        }

    }

    public CommonRes addPayQrcode(PayQrcode payQrcode){
        if (payQrcode.getPayUrl()==null){
            return ResUtil.error();
        }
        if (payQrcode.getPrice()==0){
            return ResUtil.error();
        }
        if (payQrcode.getType()==0){
            return ResUtil.error();
        }
        payQrcodeDao.save(payQrcode);
        return ResUtil.success();
    }

    public CommonRes getMain(){
        ZoneId zoneId = ZoneId.systemDefault();
        long startDate = LocalDate.now(zoneId)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli();
        long endDate = LocalDate.now(zoneId)
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli() - 1;

        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(2);

        DashboardStats stats = jdbcTemplate.queryForObject(
                DASHBOARD_QUERY,
                (resultSet, rowNum) -> new DashboardStats(
                        resultSet.getLong(1),
                        resultSet.getLong(2),
                        resultSet.getLong(3),
                        resultSet.getDouble(4),
                        resultSet.getLong(5),
                        resultSet.getDouble(6)),
                startDate, endDate,
                startDate, endDate,
                startDate, endDate,
                startDate, endDate);

        Map<String,String> map = new HashMap<>();
        map.put("todayOrder", String.valueOf(stats.todayOrder()));
        map.put("todaySuccessOrder", String.valueOf(stats.todaySuccessOrder()));
        map.put("todayCloseOrder", String.valueOf(stats.todayCloseOrder()));
        map.put("todayMoney", nf.format(stats.todayMoney()));
        map.put("countOrder", String.valueOf(stats.countOrder()));
        map.put("countMoney",nf.format(stats.countMoney()));

        return ResUtil.success(map);
    }

    public PageRes getPayQrcodes(Integer page, Integer limit, Integer type){

        Pageable pageable = PageRequest.of(page-1, limit, Sort.Direction.DESC, "id");

        Specification<PayQrcode> specification = new Specification<PayQrcode>() {
            @Override
            public Predicate toPredicate(Root<PayQrcode> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder cb) {
                List<Predicate> list = new ArrayList<Predicate>();

                if (type!=null) {
                    list.add(cb.equal(root.get("type").as(int.class), type));
                }

                return cb.and(list.toArray(new Predicate[list.size()]));
            }
        };
        Page<PayQrcode> payQrcodes = payQrcodeDao.findAll(specification, pageable);

        PageRes list = PageRes.success(payQrcodes.getTotalElements(),payQrcodes.getContent());
        return list;
    }
    public CommonRes delPayQrcode(Long id){
        payQrcodeDao.deleteById(id);
        return ResUtil.success();
    }
    public CommonRes delOrder(Long id){
        PayOrder payOrder = payOrderDao.findById(id).orElse(null);
        if (payOrder == null) {
            return ResUtil.error("订单不存在");
        }
        if (payOrder.getState()==0){
            tmpPriceDao.delprice(payOrder.getType()+"-"+payOrder.getReallyPrice());
        }
        payOrderDao.deleteById(id);
        return ResUtil.success();
    }
    public CommonRes delGqOrder(){
        payOrderDao.deleteByState(-1);
        return ResUtil.success();
    }

    public CommonRes delLastOrder(){
        payOrderDao.deleteByAfterCreateDate(String.valueOf(new Date().getTime()-7*86400*1000));
        return ResUtil.success();
    }


    public static String md5(String text) {
        //加密后的字符串
        String encodeStr= DigestUtils.md5DigestAsHex(
                text.getBytes(StandardCharsets.UTF_8));
        return encodeStr;
    }

    private record DashboardStats(
            long todayOrder,
            long todaySuccessOrder,
            long todayCloseOrder,
            double todayMoney,
            long countOrder,
            double countMoney) {
    }
}
