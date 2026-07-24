package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(
        name = "vmq.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class QuartzService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzService.class);
    private static final List<String> SETTING_KEYS =
            List.of("close", "lastheart", "jkstate");

    private final SettingDao settingDao;
    private final PayOrderDao payOrderDao;
    private final TmpPriceDao tmpPriceDao;

    public QuartzService(
            SettingDao settingDao,
            PayOrderDao payOrderDao,
            TmpPriceDao tmpPriceDao) {
        this.settingDao = settingDao;
        this.payOrderDao = payOrderDao;
        this.tmpPriceDao = tmpPriceDao;
    }

    @Scheduled(
            fixedDelayString = "${vmq.orders.cleanup-delay:30s}",
            initialDelayString = "${vmq.orders.cleanup-initial-delay:30s}")
    @Transactional
    public void cleanExpiredOrdersAndHeartbeat() {
        Map<String, String> settings = loadSettings();
        expireOrders(settings.get("close"));
        markMonitorOffline(settings.get("jkstate"), settings.get("lastheart"));
    }

    private Map<String, String> loadSettings() {
        Map<String, String> values = new HashMap<>();
        for (Setting setting : settingDao.findAllById(SETTING_KEYS)) {
            values.put(setting.getVkey(), setting.getVvalue());
        }
        return values;
    }

    private void expireOrders(String closeMinutesValue) {
        Integer closeMinutes = parsePositiveInteger(closeMinutesValue);
        if (closeMinutes == null) {
            LOGGER.debug("Skipping expired-order cleanup because close setting is unavailable");
            return;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - closeMinutes * 60_000L;
        List<PayOrder> expired =
                payOrderDao.findAllByStateAndCreateDateLessThan(0, cutoff);
        if (expired.isEmpty()) {
            return;
        }

        for (PayOrder order : expired) {
            order.setState(-1);
            order.setCloseDate(now);
        }
        payOrderDao.saveAll(expired);
        tmpPriceDao.deleteAllByIdInBatch(expired.stream()
                .map(order -> order.getType() + "-" + order.getReallyPrice())
                .distinct()
                .toList());
        LOGGER.info("Expired {} unpaid order(s)", expired.size());
    }

    private void markMonitorOffline(String state, String lastHeartbeatValue) {
        if (!"1".equals(state)) {
            return;
        }
        Long lastHeartbeat = parseLong(lastHeartbeatValue);
        if (lastHeartbeat != null
                && System.currentTimeMillis() - lastHeartbeat > 60_000L) {
            Setting setting = new Setting();
            setting.setVkey("jkstate");
            setting.setVvalue("0");
            settingDao.save(setting);
            LOGGER.info("Payment monitor marked offline after heartbeat timeout");
        }
    }

    private Integer parsePositiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
