package com.jupiter.transcript;


import com.jupiter.transcript.utils.MyHints;
import com.jupiter.transcript.utils.SrtGenerator;
import com.jupiter.transcript.utils.SrtTranslator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@ImportRuntimeHints(MyHints.class)
@Slf4j
@EnableScheduling
@SpringBootApplication
public class TranscriptApplication {

    @Value("${transcript.enable:false}")
    private static boolean enable;

    static void main(String[] args) throws Exception {
        System.setProperty("TENCENTCLOUD_SECRET_ID", "AKIDaWN3WpxzqVyWzGZovqaDi5XuQuIWZWKv");
        System.setProperty("TENCENTCLOUD_SECRET_KEY", "TnwscDpIbjf1Tt9C41rWyFKEwXvUqBQi");
        SpringApplication.run(TranscriptApplication.class, args);
        String filePath = System.getProperty("VIDEO_FILE_PATH");
        String srcLang = System.getProperty("SRC_LANG");
        if (enable) {
            videoConvert(filePath == null ? "Z:\\vodei" : filePath, srcLang == null ? "ja" : srcLang);
            log.info("转录翻译完成");
        }
    }

//    @Scheduled(cron = "0 * * * * *")
    public void runSchedule() throws Exception {
        String filePath = System.getProperty("VIDEO_FILE_PATH");
        String srcLang = System.getProperty("SRC_LANG");
        videoConvert(filePath == null ? "Z:\\vodei" : filePath, srcLang == null ? "ja" : srcLang);
    }

    private static void videoConvert(String filePath,String srclang) throws Exception {
        File vFile = new File(filePath);
        File[] files = vFile.listFiles();
        assert files != null;
        for (File file : files) {
            if (file.isFile()) {
                // 获取文件后缀名
                String suffix = file.getName().substring(file.getName().lastIndexOf(".") + 1);
                String fileNameNoFix = file.getName().substring(0, file.getName().lastIndexOf("."));
                Path path = Paths.get(file.getPath());
                Path parent = path.getParent();
                if (StringUtils.isBlank(suffix)) {
                    continue;
                }
                if (!"mp4".equalsIgnoreCase(suffix)) {
                    continue;
                }
                if (fileNameNoFix.endsWith("-C")) {
                    log.info("视频可能有中文字幕，不转录：{}", file.getName());
                    continue;
                }
                log.info("开始处理文件：{}", file.getName());
                File strFileDir = new File(parent.toString() + "\\" + fileNameNoFix + "_srt");
                if (!strFileDir.exists()) {
                    strFileDir.mkdirs();
                }
                String MP3File = strFileDir.getAbsolutePath() + "\\" + fileNameNoFix + ".mp3";
                // 生成MP3文件
                makingMp3(file, MP3File);
                // 生成字幕文件
                String srtFileStr = strFileDir.getAbsolutePath() + "\\" + fileNameNoFix + ".srt";
                File srtFile = new File(srtFileStr);
                if (!srtFile.exists()) {
                    new SrtGenerator()
                            .generateSrt(MP3File,srclang, srtFileStr);
                }
                // 翻译字幕文件
                String srtFullName = srtFile.getName().substring(0, file.getName().lastIndexOf("."));
                String outputFile = srtFile.getParent() + "\\" + srtFullName + "_zh.srt";
                new SrtTranslator()
                        .translateSrt(srtFileStr, outputFile,srclang, "zh");
                log.info("处理完成文件：{}", file.getName());
            }
        }

    }

    private static void makingMp3(File file, String MP3File) throws IOException, InterruptedException {

        if (new File(MP3File).exists()) {
            log.info("视频{}的MP3音频已存在，跳过生成", file.getName());
            return;
        }
        List<String> commands = mp3Commands(file, MP3File);
        System.out.println(StringUtils.join(commands, " "));
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (
                BufferedReader reader = new BufferedReader(new
                        InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            process.waitFor();

        }
    }





    @NonNull
    private static List<String> mp3Commands(File file, String MP3File) {
        List<String> commands = new ArrayList<>();
        commands.add("cmd.exe");
        commands.add("/c");
        commands.add("ffmpeg");
        commands.add("-i");
        commands.add(file.getAbsolutePath());
        commands.add("-q:a");
        commands.add("0");
        commands.add("-map");
        commands.add("a");
        commands.add(MP3File);
        return commands;
    }
}
