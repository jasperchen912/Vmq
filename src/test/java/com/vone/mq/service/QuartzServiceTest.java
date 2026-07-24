package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuartzServiceTest {

    private final SettingDao settingDao = mock(SettingDao.class);
    private final PayOrderDao payOrderDao = mock(PayOrderDao.class);
    private final TmpPriceDao tmpPriceDao = mock(TmpPriceDao.class);
    private final QuartzService service =
            new QuartzService(settingDao, payOrderDao, tmpPriceDao);

    @Test
    void expiresOpenOrdersAndReleasesReservedPricesInOneRun() {
        when(settingDao.findAllById(any())).thenReturn(List.of(
                setting("close", "5"),
                setting("lastheart", String.valueOf(System.currentTimeMillis())),
                setting("jkstate", "1")));
        PayOrder order = new PayOrder();
        order.setType(2);
        order.setReallyPrice(10.01D);
        when(payOrderDao.findAllByStateAndCreateDateLessThan(
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of(order));

        service.cleanExpiredOrdersAndHeartbeat();

        assertEquals(-1, order.getState());
        assertTrue(order.getCloseDate() > 0);
        verify(payOrderDao).saveAll(List.of(order));
        verify(tmpPriceDao).deleteAllByIdInBatch(List.of("2-10.01"));
    }

    @Test
    void missingSettingsAreSafeDuringStartup() {
        when(settingDao.findAllById(any())).thenReturn(List.of());

        service.cleanExpiredOrdersAndHeartbeat();

        verify(payOrderDao, never())
                .findAllByStateAndCreateDateLessThan(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyLong());
        verify(settingDao, never()).save(any(Setting.class));
    }

    private Setting setting(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value);
        return setting;
    }
}
