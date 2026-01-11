package com.jupiter.transcript.utils;

import com.aliyun.alimt20181012.Client;
import com.aliyun.alimt20181012.models.TranslateGeneralRequest;
import com.aliyun.alimt20181012.models.TranslateGeneralResponse;
import com.aliyun.teaopenapi.models.Config;
import com.jupiter.transcript.apicall.AliyunTranslator;
import com.jupiter.transcript.apicall.ApiCall;
import com.jupiter.transcript.apicall.TencentYunTranslator;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class SrtTranslator {


    private static volatile ApiCall apiCall;
    /**
     * 翻译 SRT 文件
     * @param inputPath 输入路径
     * @param outputPath 输出路径
     * @param sourceLang 源语言 (ja, zh, etc.)
     * @param targetLang 目标语言 (zh, en, etc.)
     */
    public void translateSrt(String inputPath, String outputPath, String sourceLang, String targetLang) throws Exception {
        if (new File(outputPath).exists()) {
            log.info("翻译文件:{}已存在，跳过生成", outputPath);
            return;
        }
        apiCall = buildApiCall();
        List<String> lines = new ArrayList<>();
        List<String> textLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (isTextLine(line)) {
                    textLines.add(line);
                }
            }
        }
        if (textLines.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        List<String> translatedLines = new ArrayList<>();
        for (String textLine : textLines) {
            sb.append(textLine).append("\n");
            if (sb.length() > 3000) {
                String translated = apiCall.callAliApi(sb.toString(), sourceLang, targetLang);
                translatedLines.addAll(List.of(translated.split("\n")));
                sb.setLength(0);
            }
        }
        if (!sb.isEmpty()) {
            String translated = apiCall.callAliApi(sb.toString(), sourceLang, targetLang);
            translatedLines.addAll(List.of(translated.split("\n")));
            sb.setLength(0);
        }
        if (translatedLines.size() != textLines.size()) {
            throw new RuntimeException("翻译错误，数量不一致");
        }
        Map<String,String> translateMap = new HashMap<>();
        for (int i = 0; i < textLines.size(); i++) {
            translateMap.put(textLines.get(i), translatedLines.get(i));
        }
        lines = lines.stream()
                .map(line -> {
                    if (isTextLine(line)) {
                        return translateMap.getOrDefault(line, line);
                    }
                    return line;
                })
                .toList();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private boolean isTextLine(String line) {
        if (line.trim().isEmpty()) return false;
        if (line.matches("\\d+")) return false; // 排除序号
        if (line.contains("-->")) return false; // 排除时间轴
        return true;
    }

    private ApiCall buildApiCall() {
        if (apiCall == null) {
            synchronized (this) {
                if (System.getProperties().getProperty("TENCENTCLOUD_SECRET_KEY") != null) {
                    return new TencentYunTranslator();
                }else {
                    return new AliyunTranslator();
                }
            }
        }
        return apiCall;
    }


}
