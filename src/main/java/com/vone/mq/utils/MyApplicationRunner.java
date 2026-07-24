package com.vone.mq.utils;

import com.vone.mq.dao.SettingDao;
import com.vone.mq.entity.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class MyApplicationRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyApplicationRunner.class);

    private final SettingDao settingDao;

    public MyApplicationRunner(SettingDao settingDao) {
        this.settingDao = settingDao;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Map<String, Supplier<String>> defaults = new LinkedHashMap<>();
        defaults.put("user", () -> "admin");
        defaults.put("pass", () -> "admin");
        defaults.put("notifyUrl", () -> "");
        defaults.put("returnUrl", () -> "");
        defaults.put("key", () -> md5(String.valueOf(System.currentTimeMillis())));
        defaults.put("lastheart", () -> "0");
        defaults.put("lastpay", () -> "0");
        defaults.put("jkstate", () -> "-1");
        defaults.put("close", () -> "5");
        defaults.put("payQf", () -> "1");
        defaults.put("wxpay", () -> "");
        defaults.put("zfbpay", () -> "");

        int created = 0;
        for (Map.Entry<String, Supplier<String>> entry : defaults.entrySet()) {
            if (!settingDao.existsById(entry.getKey())) {
                Setting setting = new Setting();
                setting.setVkey(entry.getKey());
                setting.setVvalue(entry.getValue().get());
                settingDao.save(setting);
                created++;
            }
        }

        if (created > 0) {
            LOGGER.warn(
                    "Initialized {} missing setting(s); change the default admin password immediately",
                    created);
        }
        LOGGER.info("Vmq initialization complete");
    }

    public static String md5(String text) {
        return DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
    }
}
