package com.jupiter.transcript.apicall;

import com.aliyun.alimt20181012.Client;
import com.aliyun.alimt20181012.models.TranslateGeneralRequest;
import com.aliyun.alimt20181012.models.TranslateGeneralResponse;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.stereotype.Component;

public class AliyunTranslator implements ApiCall{


    private static volatile Client client = null;

    private static Client buildClient(String accessKeyId, String accessKeySecret) throws Exception {
        if (client == null) {
            synchronized (AliyunTranslator.class) {
                if (client != null) {
                    return client;
                }
                Config config = new Config()
                        .setAccessKeyId (accessKeyId)
                        .setAccessKeySecret (accessKeySecret);
                // Endpoint 请参考 https://api.aliyun.com/product/alimt
                config.endpoint = "mt.cn-hangzhou.aliyuncs.com";
                return new Client(config);
            }
        }
        return client;
    }

    public String callAliApi(String text, String source, String target) throws Exception {
        client = buildClient(System.getenv("ALIYUN_SECRET_ID"),System.getenv("ALIYUN_SECRET_KEY"));
        TranslateGeneralRequest request = new TranslateGeneralRequest()
                .setFormatType("text")
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .setSourceText(text)
                .setScene("general");

        TranslateGeneralResponse response = client.translateGeneral(request);
        return response.getBody().getData().getTranslated();
    }
}
