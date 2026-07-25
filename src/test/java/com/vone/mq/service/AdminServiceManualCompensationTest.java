package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.PayQrcodeDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceManualCompensationTest {

    @Test
    void marksWaitingOrderCallbackPendingBeforeSendingCallback() {
        Fixture fixture = fixture(0);
        AtomicBoolean prepared = new AtomicBoolean();
        when(fixture.payOrderDao.markManualCallbackPending(
                eq(fixture.order.getId()), anyLong()))
                .thenAnswer(invocation -> {
                    prepared.set(true);
                    return 1;
                });
        AdminService service = fixture.service(url -> {
            assertTrue(prepared.get(),
                    "provider state must be callback-pending before callback");
            return "success";
        });

        CommonRes<?> response = service.setBd(
                Math.toIntExact(fixture.order.getId()));

        assertEquals(1, response.getCode());
        verify(fixture.tmpPriceDao).delprice("2-109.8");
        verify(fixture.payOrderDao).setState(1, fixture.order.getId());
    }

    @Test
    void leavesExpiredOrderCallbackPendingWhenCallbackFails() {
        Fixture fixture = fixture(-1);
        when(fixture.payOrderDao.markManualCallbackPending(
                eq(fixture.order.getId()), anyLong())).thenReturn(1);
        AdminService service = fixture.service(url -> "provider rejected");

        CommonRes<?> response = service.setBd(
                Math.toIntExact(fixture.order.getId()));

        assertEquals(-2, response.getCode());
        assertEquals("provider rejected", response.getData());
        verify(fixture.tmpPriceDao, never()).delprice(
                "2-" + fixture.order.getReallyPrice());
        verify(fixture.payOrderDao, never()).setState(
                1, fixture.order.getId());
    }

    @Test
    void doesNotSendCallbackWhenPreparationLosesStateRace() {
        Fixture fixture = fixture(0);
        when(fixture.payOrderDao.markManualCallbackPending(
                eq(fixture.order.getId()), anyLong())).thenReturn(0);
        AtomicBoolean callbackSent = new AtomicBoolean();
        AdminService service = fixture.service(url -> {
            callbackSent.set(true);
            return "success";
        });

        CommonRes<?> response = service.setBd(
                Math.toIntExact(fixture.order.getId()));

        assertEquals(-1, response.getCode());
        assertEquals("订单状态已变化，请刷新后重试", response.getMsg());
        assertEquals(false, callbackSent.get());
        verify(fixture.tmpPriceDao, never()).delprice(
                "2-" + fixture.order.getReallyPrice());
        verify(fixture.payOrderDao, never()).setState(
                1, fixture.order.getId());
    }

    @Test
    void retriesExistingCallbackPendingOrderWithoutPreparingAgain() {
        Fixture fixture = fixture(2);
        AdminService service = fixture.service(url -> "success");

        CommonRes<?> response = service.setBd(
                Math.toIntExact(fixture.order.getId()));

        assertEquals(1, response.getCode());
        verify(fixture.payOrderDao, never()).markManualCallbackPending(
                eq(fixture.order.getId()), anyLong());
        verify(fixture.payOrderDao).setState(1, fixture.order.getId());
    }

    private Fixture fixture(int state) {
        SettingDao settingDao = mock(SettingDao.class);
        PayOrderDao payOrderDao = mock(PayOrderDao.class);
        TmpPriceDao tmpPriceDao = mock(TmpPriceDao.class);
        PayQrcodeDao payQrcodeDao = mock(PayQrcodeDao.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        PayOrder order = new PayOrder();
        order.setId(42L);
        order.setPayId("PAY-TEST");
        order.setOrderId("VMQ-TEST");
        order.setParam("ORDER_PAYMENT");
        order.setType(2);
        order.setPrice(109.8);
        order.setReallyPrice(109.8);
        order.setNotifyUrl("http://callback.test/notice");
        order.setState(state);

        Setting key = new Setting();
        key.setVkey("key");
        key.setVvalue("test-key");
        when(settingDao.findById("key")).thenReturn(Optional.of(key));
        when(payOrderDao.findById(order.getId()))
                .thenReturn(Optional.of(order));
        when(payOrderDao.setState(1, order.getId())).thenReturn(1);

        return new Fixture(
                settingDao, payOrderDao, tmpPriceDao, payQrcodeDao,
                jdbcTemplate, order);
    }

    @FunctionalInterface
    private interface Callback {
        String send(String url);
    }

    private record Fixture(
            SettingDao settingDao,
            PayOrderDao payOrderDao,
            TmpPriceDao tmpPriceDao,
            PayQrcodeDao payQrcodeDao,
            JdbcTemplate jdbcTemplate,
            PayOrder order) {

        private AdminService service(Callback callback) {
            return new AdminService(
                    settingDao, payOrderDao, tmpPriceDao, payQrcodeDao,
                    jdbcTemplate) {
                @Override
                protected String sendCallback(
                        String url, String parameters) {
                    return callback.send(url);
                }
            };
        }
    }
}
