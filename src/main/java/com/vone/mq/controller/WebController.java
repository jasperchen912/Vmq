package com.vone.mq.controller;

import com.google.gson.Gson;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.CreateOrderRes;
import com.vone.mq.service.WebService;
import com.vone.mq.utils.ResUtil;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@RestController
public class WebController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebController.class);

    private final WebService webService;

    public WebController(WebService webService) {
        this.webService = webService;
    }

    @RequestMapping(value = "/enQrcode", produces = "image/png")
    public void enQrcode(HttpServletResponse resp, String url) throws IOException {
        if (url != null && !"".equals(url)) {
            resp.setContentType("image/png");
            try (ServletOutputStream stream = resp.getOutputStream()) {
                int width = 200;//图片的宽度
                int height = 200;//高度
                QRCodeWriter writer = new QRCodeWriter();
                BitMatrix m = writer.encode(url, BarcodeFormat.QR_CODE, height, width);
                MatrixToImageWriter.writeToStream(m, "png", stream);
            } catch (Exception e) {
                LOGGER.warn("Failed to generate QR code ({})",
                        e.getClass().getSimpleName());
                if (!resp.isCommitted()) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                }
            }
        }
    }

    @RequestMapping("/deQrcode")
    public CommonRes deQrcode(String base64) {
        if (base64 != null && !"".equals(base64)) {
            try {
                MultiFormatReader multiFormatReader = new MultiFormatReader();
                byte[] bytes1 = Base64.getDecoder().decode(base64);
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes1);
                BufferedImage image = ImageIO.read(bais);
                //定义二维码参数
                Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
                hints.put(DecodeHintType.CHARACTER_SET, "utf-8");
                //获取读取二维码结果
                BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
                Result result = multiFormatReader.decode(binaryBitmap, hints);
                //stream.print(result.getText());
                return ResUtil.success(result.getText());
            } catch (Exception e) {
                LOGGER.debug("Failed to decode base64 QR code ({})",
                        e.getClass().getSimpleName());
            }
        }
        return ResUtil.error();
    }

    @RequestMapping("/deQrcode2")
    public CommonRes deQrcode2(@RequestParam("file") MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            try {
                MultiFormatReader multiFormatReader = new MultiFormatReader();
                byte[] bytes1 = file.getBytes();
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes1);
                BufferedImage image = ImageIO.read(bais);
                //定义二维码参数
                Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
                hints.put(DecodeHintType.CHARACTER_SET, "utf-8");
                //获取读取二维码结果
                BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
                Result result = multiFormatReader.decode(binaryBitmap, hints);

                //stream.print(result.getText());
                return ResUtil.success(result.getText());
            } catch (Exception e) {
                LOGGER.debug("Failed to decode uploaded QR code ({})",
                        e.getClass().getSimpleName());
            }
        }
        return ResUtil.error();
    }

    /**
     * 创建订单
     *
     * @param payId     商户订单号
     * @param param     订单保存的信息
     * @param type      支付方式 1|微信 2|支付宝
     * @param price     订单价格
     * @param notifyUrl 异步通知地址，如果为空则使用系统后台设置的地址
     * @param returnUrl 支付完成后同步跳转地址，将会携带参数跳转
     * @param sign      签名认证 签名方式为 md5(payId + param + type + price + 通讯密钥)
     * @param isHtml    0返回json数据 1跳转到支付页面
     * @return
     */
    @RequestMapping("/createOrder")
    public String createOrder(String payId, String param, Integer type, String price, String notifyUrl, String returnUrl, String sign, Integer isHtml) {
        if (payId == null || payId.equals("")) {
            return new Gson().toJson(ResUtil.error("请传入商户订单号"));
        }
        if (type == null) {
            return new Gson().toJson(ResUtil.error("请传入支付方式=>1|微信 2|支付宝"));
        }
        if (type != 1 && type != 2) {
            return new Gson().toJson(ResUtil.error("支付方式错误=>1|微信 2|支付宝"));
        }


        Double priceD;
        try {
            priceD = Double.valueOf(price);
        } catch (Exception e){
            return new Gson().toJson(ResUtil.error("请传入订单金额"));
        }

        if (priceD == null) {
            return new Gson().toJson(ResUtil.error("请传入订单金额"));
        }
        if (priceD < 0) {
            return new Gson().toJson(ResUtil.error("订单金额必须大于0"));
        }


        if (sign == null || sign.equals("")) {
            return new Gson().toJson(ResUtil.error("请传入签名"));
        }
        if (param == null) {
            param = "";
        }
        if (isHtml == null) {
            isHtml = 0;
        }
        CommonRes commonRes = webService.createOrder(payId, param, type, price, notifyUrl, returnUrl, sign);
        if (isHtml == 0) {
            String res = new Gson().toJson(commonRes);
            return res;
        } else {
            CreateOrderRes c = (CreateOrderRes) commonRes.getData();
            if (c == null) {
                return commonRes.getMsg();
            } else {
                return "<script>window.location.href = '/payPage/pay.html?orderId=" + c.getOrderId() + "'</script>";
            }
        }
    }

    @RequestMapping("/closeOrder")
    public CommonRes closeOrder(String orderId, String sign) {
        if (orderId == null) {
            return ResUtil.error("请传入云端订单号");
        }
        if (sign == null) {
            return ResUtil.error("请传入签名");
        }
        return webService.closeOrder(orderId, sign);
    }

    @RequestMapping("/appHeart")
    public CommonRes appHeart(String t, String sign) {
        return webService.appHeart(t, sign);
    }

    @RequestMapping("/appPush")
    public CommonRes appPush(Integer type, String price, String t, String sign) {
        return webService.appPush(type, price, t, sign);
    }

    @RequestMapping("/getOrder")
    public CommonRes getOrder(String orderId) {
        if (orderId == null) {
            return ResUtil.error("请传入订单编号");
        }
        return webService.getOrder(orderId);
    }

    /**
     * 按商户订单号查询订单。
     *
     * 用于调用方在创建订单响应丢失时，使用幂等的商户订单号恢复云端订单。
     */
    @RequestMapping("/getOrderByPayId")
    public CommonRes getOrderByPayId(String payId, String sign) {
        if (payId == null || payId.equals("")) {
            return ResUtil.error("请传入商户订单号");
        }
        if (sign == null || sign.equals("")) {
            return ResUtil.error("请传入签名");
        }
        return webService.getOrderByPayId(payId, sign);
    }

    @RequestMapping("/checkOrder")
    public CommonRes checkOrder(String orderId) {
        if (orderId == null) {
            return ResUtil.error("请传入订单编号");
        }
        return webService.checkOrder(orderId);

    }

    @RequestMapping("/getState")
    public CommonRes getState(String t, String sign) {
        if (t == null) {
            return ResUtil.error("请传入t");
        }
        if (sign == null) {
            return ResUtil.error("请传入sign");
        }
        return webService.getState(t, sign);
    }
}
