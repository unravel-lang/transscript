package com.jupiter.transcript.apicall;

import com.google.common.util.concurrent.RateLimiter;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.tmt.v20180321.TmtClient;
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateRequest;
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public class TencentYunTranslator implements  ApiCall{

    private volatile Credential cred;
    private volatile TmtClient client;
    private final RateLimiter reqRateLimiter = RateLimiter.create(5); // 每秒最多5次


    private Credential buildCred() {
        if (this.cred == null) {
            synchronized (this) {
                if (this.cred != null) {
                    return cred;
                }
                HttpProfile httpProfile = new HttpProfile();
                httpProfile.setEndpoint("tmt.tencentcloudapi.com");
                // 实例化一个client选项，可选的，没有特殊需求可以跳过
                ClientProfile clientProfile = new ClientProfile();
                clientProfile.setHttpProfile(httpProfile);
                // 实例化要请求产品的client对象,clientProfile是可选的
                this.cred = new Credential(System.getProperty("TENCENTCLOUD_SECRET_ID"), System.getProperty("TENCENTCLOUD_SECRET_KEY"));
                this.client = new TmtClient(cred, "ap-guangzhou", clientProfile);
            }
        }
        return cred;
    }
    @Override
    public String callAliApi(String text, String source, String target) throws Exception {

        cred = buildCred();
        // 使用临时密钥示例
        // Credential cred = new Credential("SecretId", "SecretKey", "Token");
        // 实例化一个http选项，可选的，没有特殊需求可以跳过

        // 实例化一个请求对象,每个接口都会对应一个request对象
        TextTranslateRequest req = new TextTranslateRequest();
        req.setSourceText(text);
        req.setSource(source);
        req.setTarget(target);
        req.setProjectId(1352639L);
//        TimeUnit.MILLISECONDS.sleep(200);
        reqRateLimiter.acquire();
        // 返回的resp是一个TextTranslateResponse的实例，与请求对象对应
        TextTranslateResponse resp = client.TextTranslate(req);
        return resp.getTargetText();
    }

//    static void main() throws Exception {
//        System.setProperty("TENCENTCLOUD_SECRET_ID", "AKIDaWN3WpxzqVyWzGZovqaDi5XuQuIWZWKv");
//        System.setProperty("TENCENTCLOUD_SECRET_KEY", "TnwscDpIbjf1Tt9C41rWyFKEwXvUqBQi");
//        String text = new TencentYunTranslator()
//                .callAliApi("いっぱいセックスいて飽きちゃったんだ", "ja", "zh");
//        System.out.println(text);
//    }
}
