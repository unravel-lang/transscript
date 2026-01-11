package com.jupiter.transcript.utils;

import com.jupiter.transcript.apicall.AliyunTranslator;
import com.jupiter.transcript.apicall.ApiCall;
import com.jupiter.transcript.apicall.TencentYunTranslator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SrtGenerator {

    private ApiCall apiCall;

    public void generateSrt(String audioPath, String lang, String outputSrtPath) throws IOException, InterruptedException {
        if (new File(outputSrtPath).exists()) {
            log.info("字幕文件已存在，跳过生成: {}", outputSrtPath);
            return;
        }

        // 构建命令
        List<String> commands = buildRunCommand(audioPath, lang);
        System.out.println(StringUtils.join(commands, " "));
        ProcessBuilder pb = new ProcessBuilder(commands);
        // 设置你想要切换到的目标目录
        File workingDir = new File("D:\\conda\\whisper_env\\project");
        pb.directory(workingDir);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputSrtPath), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                // 排除掉 Python 打印的库加载日志或警告信息
                if (isSrtContent(line)) {
                    writer.println(line);
                }
            }
            process.waitFor();
            System.out.println("字幕生成完毕: " + outputSrtPath);

        }
    }

    private static @NonNull List<String> buildRunCommand(String audioPath,String lang) {
        List<String> commands = new ArrayList<>();
        commands.add("cmd.exe");
        commands.add("/c");
        commands.add("conda");
        commands.add("activate");
        commands.add("whisper_env");
        commands.add("&&");
        commands.add("python");
        // 强制python脚本无缓冲
        commands.add("-u");
        commands.add("transcript.py");
        commands.add("--file");
        commands.add(String.format("\"%s\"", audioPath));
        commands.add("--lang");
        commands.add(lang);
        return commands;
    }

    /**
     * 简单的逻辑过滤掉非 SRT 内容的日志
     * (比如有些库会打印 "Created GL context" 之类的日志)
     */
    private boolean isSrtContent(String line) {
        // SRT 典型的行：数字、时间轴或文本
        // 排除掉包含 "Error", "Import", "Info" 等关键词的行
        return !line.toLowerCase().contains("nvidia") && !line.toLowerCase().contains("cuda");
    }



//    public static void main(String[] args) throws IOException, InterruptedException {
//        new SrtGenerator().generateSrt("Z:\\vodei\\[88q.me]sdde-563.mp4", "ja", "C:\\Users\\huangjb\\Documents\\output.srt");
//    }
}