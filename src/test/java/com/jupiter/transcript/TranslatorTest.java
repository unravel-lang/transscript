package com.jupiter.transcript;

import com.jupiter.transcript.apicall.TencentYunTranslator;
import com.jupiter.transcript.utils.SrtGenerator;
import com.jupiter.transcript.utils.SrtTranslator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TranslatorTest {

    @TempDir
    Path tempDir;

    @Test
    public void testTranslateWithValidInputs() throws Exception {

        tempDir = Paths.get("C:\\Users\\huangjb\\Documents");
        // 准备测试输入文件
        Path inputPath = tempDir.resolve("HSODA-085.srt");
        Path outputPath = tempDir.resolve("HSODA-085-CN.srt");

        // 执行翻译
        SrtTranslator translator = new SrtTranslator();
        translator.translateSrt(inputPath.toString(), outputPath.toString(), "ja", "zh");

        // 验证输出文件存在
        assertTrue(Files.exists(outputPath));
    }

    @Test
    public void testTranslateWithMutiInputs() throws Exception {
        SrtTranslator translator = new SrtTranslator();
        String str = "催眠術室のとこに\n帰してあげるからね\nな、達者でな";
//        String res = translator.callAliApi(str, "ja", "zh");
//        System.out.println(res);
    }

    @Test
    public void testTranslateVideo() throws Exception {
        new SrtGenerator()
                .generateSrt(
                        "Z:\\vodei\\(SOD)(3DSVR-0433)洗脳ドリルVR 強気な女弁護士を性処理便器であるということに悟り堕とし服従洗脳プレイ 古川いおり_1_srt\\(SOD)(3DSVR-0433)洗脳ドリルVR 強気な女弁護士を性処理便器であるということに悟り堕とし服従洗脳プレイ 古川いおり_1.mp3",
                        "ja",
                        "C:\\Users\\huangjb\\Documents\\output.srt");
    }

    @Test
    public void testTencentTranslator() throws Exception {
        System.setProperty("TENCENTCLOUD_SECRET_ID", "AKIDaWN3WpxzqVyWzGZovqaDi5XuQuIWZWKv");
        System.setProperty("TENCENTCLOUD_SECRET_KEY", "TnwscDpIbjf1Tt9C41rWyFKEwXvUqBQi");
        String translate = new TencentYunTranslator()
                .callAliApi("いっぱいセックスいて飽きちゃったんだ", "ja", "zh");
        System.out.println(translate);
    }
//
//    @Test
//    public void testTranslateWithNonexistentInputFile() {
//        Path inputPath = tempDir.resolve("nonexistent.srt");
//        Path outputPath = tempDir.resolve("output.srt");
//
//        Translator translator = new Translator();
//        assertThrows(Exception.class, () -> {
//            translator.translate(inputPath.toString(), outputPath.toString(), "en", "zh");
//        });
//    }
//
//    @Test
//    public void testTranslateWithInvalidLanguageCode() throws Exception {
//        Path inputPath = tempDir.resolve("input.srt");
//        Path outputPath = tempDir.resolve("output.srt");
//        Files.write(inputPath, "1\n00:00:01,000 --> 00:00:04,000\nHello\n".getBytes());
//
//        Translator translator = new Translator();
//        assertThrows(Exception.class, () -> {
//            translator.translate(inputPath.toString(), outputPath.toString(), "invalid", "zh");
//        });
//    }
//
//    @Test
//    public void testTranslateWithEmptyInputFile() throws Exception {
//        Path inputPath = tempDir.resolve("empty.srt");
//        Path outputPath = tempDir.resolve("output.srt");
//        Files.write(inputPath, "".getBytes());
//
//        Translator translator = new Translator();
//        translator.translate(inputPath.toString(), outputPath.toString(), "en", "zh");
//
//        // 验证输出文件存在且为空
//        assertTrue(Files.exists(outputPath));
//        assertEquals(0, Files.size(outputPath));
//    }
//
//    @Test
//    public void testTranslateWithSpecialCharacters() throws Exception {
//        Path inputPath = tempDir.resolve("special.srt");
//        Path outputPath = tempDir.resolve("output.srt");
//        Files.write(inputPath, "1\n00:00:01,000 --> 00:00:04,000\nHello @#$%^\n".getBytes());
//
//        Translator translator = new Translator();
//        translator.translate(inputPath.toString(), outputPath.toString(), "en", "zh");
//
//        // 验证输出文件存在
//        assertTrue(Files.exists(outputPath));
//    }
//
//    @Test
//    public void testTranslateWithMultipleLanguages() throws Exception {
//        Path inputPath = tempDir.resolve("multi.srt");
//        Path outputPath = tempDir.resolve("output.srt");
//        Files.write(inputPath, "1\n00:00:01,000 --> 00:00:04,000\nHello\n2\n00:00:05,000 --> 00:00:08,000\nBonjour\n".getBytes());
//
//        Translator translator = new Translator();
//        translator.translate(inputPath.toString(), outputPath.toString(), "en", "zh");
//
//        // 验证输出文件存在
//        assertTrue(Files.exists(outputPath));
//    }
}
