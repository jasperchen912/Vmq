package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.CreateOrderRes;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebServicePayIdQueryTest {

    private static final String KEY =
            "0123456789abcdef0123456789abcdef";

    private final SettingDao settingDao = mock(SettingDao.class);
    private final PayOrderDao payOrderDao = mock(PayOrderDao.class);
    private final WebService service = new WebService();

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(service, "settingDao", settingDao);
        ReflectionTestUtils.setField(service, "payOrderDao", payOrderDao);
        when(settingDao.findById("key")).thenReturn(
                Optional.of(setting("key", KEY)));
        when(settingDao.findById("close")).thenReturn(
                Optional.of(setting("close", "5")));
    }

    @Test
    public void signedMerchantOrderQueryReturnsCurrentOrder() {
        PayOrder order = order();
        when(payOrderDao.findByPayId("PAY-ABC")).thenReturn(order);
        String sign = DigestUtils.md5DigestAsHex(
                ("PAY-ABC" + KEY).getBytes(StandardCharsets.UTF_8));

        CommonRes response = service.getOrderByPayId("PAY-ABC", sign);

        assertEquals(1, response.getCode());
        assertTrue(response.getData() instanceof CreateOrderRes);
        CreateOrderRes result = (CreateOrderRes) response.getData();
        assertEquals("PAY-ABC", result.getPayId());
        assertEquals("VMQ-ORDER-1", result.getOrderId());
        assertEquals(20.01D, result.getReallyPrice(), 0.0001D);
    }

    @Test
    public void invalidSignatureDoesNotRevealWhetherOrderExists() {
        CommonRes response = service.getOrderByPayId(
                "PAY-ABC", "00000000000000000000000000000000");

        assertEquals(-1, response.getCode());
        assertEquals("签名校验不通过", response.getMsg());
        assertNull(response.getData());
        verify(payOrderDao, never()).findByPayId("PAY-ABC");
    }

    private Setting setting(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value);
        return setting;
    }

    private PayOrder order() {
        PayOrder order = new PayOrder();
        order.setPayId("PAY-ABC");
        order.setOrderId("VMQ-ORDER-1");
        order.setType(2);
        order.setPrice(20D);
        order.setReallyPrice(20.01D);
        order.setPayUrl("https://qr.alipay.example/abc");
        order.setIsAuto(1);
        order.setState(1);
        order.setCreateDate(1_700_000_000_000L);
        return order;
    }
}
