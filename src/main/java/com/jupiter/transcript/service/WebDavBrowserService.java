package com.jupiter.transcript.service;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineImpl;
import com.google.common.collect.Queues;
import com.jupiter.transcript.vo.FileItem;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class WebDavBrowserService {

    public static final Semaphore SEMAPHORE = new Semaphore(5);
    @Value("${webdav.url}")
    private String webdavUrl;

    @Value("${webdav.username}")
    private String username;

    @Value("${webdav.password}")
    private String password;
    @Value("${webdav.syncLocalPath:Z:\\vodei\\sync}")
    private String syncLocalPath;
    @Value("${webdav.syncRemotePath:/sync/test/}")
    private String syncRemotePath;

    private volatile Sardine sardine = null;
    private Map<DavResource, FileInfo> downloadingMap = new ConcurrentHashMap<>();

    public Sardine buildSardine() {
        if (sardine == null) {
            synchronized (this) {
                if (sardine != null) {
                    return sardine;
                }
                PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
                cm.setMaxTotal(100);
                cm.setDefaultMaxPerRoute(20);

                HttpClientBuilder httpClientBuilder = HttpClients.custom()
                        .setConnectionManager(cm);
                sardine = new SardineImpl(httpClientBuilder);
                sardine.setCredentials(username, password);
            }
        }
        return sardine;
    }

    // 存储上一次的文件快照 (文件名 -> 修改时间)
    private final Map<String, FileInfo> fileCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> currentTaskMap = new ConcurrentHashMap<>();

    public List<FileItem> browse(String path) throws IOException {
        // 构建完整 URL，确保处理好路径拼接
        String fullUrl = encodingPath(path);

        List<DavResource> resources = buildSardine().list(fullUrl);
        List<FileItem> items = new ArrayList<>();

        // 第一个资源通常是当前目录本身，需要跳过
        for (int i = 1; i < resources.size(); i++) {
            DavResource res = resources.get(i);
            items.add(new FileItem(
                res.getName(),
                path + (path.endsWith("/") ? "" : "/") + res.getName(),
                res.isDirectory(),
                res.getContentLength(),
                res.getModified().toString()
            ));
        }
        return items;
    }

    public void download(String path, String rangeHeader, HttpServletResponse response) throws IOException {
        // 构建完整 URL，确保处理好路径拼接
        String fullUrl = encodingPath(path);

        // 2. 获取文件基本信息（如总大小）
        List<DavResource> list = buildSardine().list(fullUrl);
        if (CollectionUtils.isEmpty(list)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return ;
        }
        DavResource resource = list.getFirst();
        long fileSize = resource.getContentLength();
        long contentLength = fileSize;
        String fileName = resource.getName();
        // 3. 准备 WebDAV 请求头
        Map<String, String> davHeaders = new HashMap<>();
        if (rangeHeader != null) {
            // 3. 解析 Range 范围
            long start = 0;
            long end = fileSize - 1;

            if (rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.substring(6).split("-");
                try {
                    if (ranges.length > 0 && !ranges[0].isEmpty()) {
                        start = Long.parseLong(ranges[0]);
                    }
                    if (ranges.length > 1 && !ranges[1].isEmpty()) {
                        end = Long.parseLong(ranges[1]);
                    }
                } catch (NumberFormatException e) {
                    // 解析失败则按全文下载处理
                }
            }

            // 验证范围有效性
            if (start > end || start >= fileSize) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return ;
            }

            // 4. 设置分片响应头
            contentLength = end - start + 1;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize));

            // 5. 调用 Sardine 获取特定范围的流并转发

            davHeaders.put("Range", "bytes=" + start + "-" + end);
        }



        response.setHeader("Content-Length", String.valueOf(contentLength));
        response.setContentType("application/octet-stream");

        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");


        try (InputStream is = sardine.get(fullUrl, davHeaders)) {
            // 4. 将 Sardine 的流拷贝到 Response 输出流
            StreamUtils.copy(is, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在或 WebDAV 连接失败");
        }

    }

    private @NonNull String encodingPath(String path) {
        UriComponents components = UriComponentsBuilder.fromUriString(webdavUrl)
                .pathSegment(Strings.CS.removeStart(path,"/")) // 自动编码，空格转为 %20
                .build();
        String fullUrl = components.toUri().toString();
        if (!fullUrl.endsWith("/")) {
            fullUrl = fullUrl + "/";
        }
        return fullUrl;
    }


    // 每 10 秒扫描一次
    @Scheduled(fixedDelay = 100_000, initialDelay = 1000)
    public void scanWebDav() throws IOException, InterruptedException {
        int availablePermits = SEMAPHORE.availablePermits();
        if (!downloadingMap.isEmpty()) {
            for (Map.Entry<DavResource, FileInfo> entry : downloadingMap.entrySet()) {

                FileInfo value = entry.getValue();
                if (value == null) {
                    continue;
                }
                Long lastModify = value.lastModify();
                value.setLastModify(System.currentTimeMillis());
                DavResource resource = entry.getKey();
                log.info("file {} process is {}% download avg speed is {}", resource.getPath(), (float) value.size / resource.getContentLength(), (float) value.size / (System.currentTimeMillis() - lastModify));
            }
        }
        if (availablePermits <= 0) {
            log.info("current running task num > 5,wait for next scan");
            return;
        }
        buildLocalFileCache();
        List<DavResource> downloadList = new ArrayList<>();
        ArrayDeque<String> arrayDeque = Queues.newArrayDeque();
        arrayDeque.add(syncRemotePath);
        while (!arrayDeque.isEmpty()) {
            String path = arrayDeque.poll();
            List<DavResource> list = buildSardine().list(encodingPath(path));
            for (DavResource res : list) {
                if (path.equals(res.getPath())) {
                    continue;
                }
                if (res.isDirectory()) {
                    arrayDeque.add(res.getPath().endsWith("/") ? res.getPath() : res.getPath() + "/");
                    continue;
                }
                String name = StringUtils.defaultString(res.getName());
                if (name.endsWith(".jpeg") || name.endsWith(".jpg") || name.endsWith(".apk")) {
                    continue;
                }
                if (!fileCache.containsKey(res.getPath())
//                        || !(fileCache.get(res.getPath()).size == res.getContentLength())
                ) {
                    downloadList.add(res);
                }
            }
        }

        long start = System.currentTimeMillis();
        if (!downloadList.isEmpty()) {
            Thread.startVirtualThread(() -> {
                Thread.currentThread().setName("vir-async-download-task");
                downloadFile(downloadList);
                log.info("虚拟线程内执行完毕");
            });
//            System.out.println( System.currentTimeMillis() - start +"本地定时任务完毕");
        }
        log.info("本地定时任务完毕");

    }

    private void downloadFile(List<DavResource> downloadList) {
        List<StructuredTaskScope.Subtask<Object>> subtasks = new ArrayList<>();
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {
            for (DavResource davResource : downloadList) {
                String davPath = davResource.getPath();
                if (currentTaskMap.containsKey(davPath)) {
                    continue;
                }
                String path = Strings.CS.removeEnd(Strings.CS.replace(
                        Strings.CS.removeStart(davPath, webdavUrl + syncRemotePath),
                        "/", "\\"), "/");
                Path target = Paths.get(syncLocalPath, path + ".tmp");

                StructuredTaskScope.Subtask<Object> subtask = scope.fork(() -> {
                    Thread.currentThread().setName("worker-" + davResource.getName());

                    try {
                        SEMAPHORE.acquire();
                        // 在子任务开始执行时重命名
                        // 3. 准备 WebDAV 请求头
                        Map<String, String> davHeaders = new HashMap<>();

                        RandomAccessFile file = null;
                        try {
                            if (Files.exists(target)) {
                                log.info("remote file {},has already a legacy tempFile:{}", davResource.getPath(), target);
                                file = new RandomAccessFile(target.toFile(), "rw");
                                long length = file.length();
                                if (length == davResource.getContentLength() || length > davResource.getContentLength()) {
                                    log.info("temp file size has the same as remote file {}", davResource.getPath());
                                    file.close();
                                    return;
                                }
                                // 5. 调用 Sardine 获取特定范围的流并转发
                                davHeaders.put("Range", "bytes=" + length + "-" + davResource.getContentLength());
                            } else {
                                if (!Files.exists(target.getParent())) {
                                    Files.createDirectories(target.getParent());
                                }
                                file = new RandomAccessFile(target.toFile(), "rw");
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        try (InputStream is = sardine.get(encodingPath(davPath), davHeaders)) {
                            currentTaskMap.put(davPath, true);

                            log.info("start downloading {},local path:{}", davResource.getPath(), target);
                            FileInfo fileInfo = downloadingMap.computeIfAbsent(davResource, (k) -> new FileInfo(0L, System.currentTimeMillis()));
                            Long downloadSize = fileInfo.size();
                            file.seek(file.length());
                            downloadSize += file.length();
                            byte[] buffer = new byte[8 * 1024];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                file.write(buffer, 0, len);
                                downloadSize += len;
                                fileInfo.setSize(downloadSize);
                            }
                            file.close();
                            Files.move(target, Paths.get(Strings.CS.removeEnd(target.toString(), ".tmp")), StandardCopyOption.REPLACE_EXISTING);
                            downloadingMap.remove(davResource);
                            log.info("download {} finish", davResource.getPath());
                        } catch (IOException e) {
                            log.error("remote file {} download failed error", davResource.getPath(), e);
                            throw new RuntimeException(e);
                        } finally {
                            currentTaskMap.remove(davPath);
                            if (file != null) {
                                try {
                                    file.close();
                                } catch (IOException e) {
                                    log.info("file try close failed");
                                }
                            }
                        }

                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        SEMAPHORE.release();
                    }

                    log.info("file {} download finish", davResource.getPath());
                });
                subtasks.add(subtask);
            }
            scope.join();
            for (StructuredTaskScope.Subtask<Object> subtask : subtasks) {
                if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
                    Throwable cause = subtask.exception();
                    log.error("子进程出现异常" + cause.getMessage());
                }
            }
            log.info("download {} files", downloadList.size());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void buildLocalFileCache() throws IOException {
        File file = new File(syncLocalPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        ArrayDeque<File> arrayDeque = Queues.newArrayDeque();
        arrayDeque.add(file);
        while (!arrayDeque.isEmpty()) {
            File curFile = arrayDeque.poll();
            if (curFile.isDirectory()) {
                File[] files = curFile.listFiles();
                if (files == null) {
                    continue;
                }
                for (File listFile : files) {
                    if (listFile.isDirectory()) {
                        arrayDeque.add(listFile);
                        continue;
                    }
                    putCache(listFile);
                }

                continue;
            }
            putCache(curFile);
        }

    }

    private void putCache(File curFile) {
        String path = curFile.getPath();
        path = Strings.CS.removeStart(path, syncLocalPath);
        path = path.replace("\\", "/");
        fileCache.put(path, new FileInfo(curFile.length(), curFile.lastModified()));
    }

    public List<String> getFileList() {
        return new ArrayList<>(fileCache.keySet());
    }

     static final class FileInfo {
        private long size;
        private Long lastModify;

        FileInfo(long size, Long lastModify) {
            this.size = size;
            this.lastModify = lastModify;
        }

        public long size() {
            return size;
        }

        public Long lastModify() {
            return lastModify;
        }

         public void setLastModify(Long lastModify) {
             this.lastModify = lastModify;
         }

         public void setSize(long size) {
             this.size = size;
         }

         @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (FileInfo) obj;
            return this.size == that.size &&
                    Objects.equals(this.lastModify, that.lastModify);
        }

        @Override
        public int hashCode() {
            return Objects.hash(size, lastModify);
        }

        @Override
        public String toString() {
            return "FileInfo[" +
                    "size=" + size + ", " +
                    "lastModify=" + lastModify + ']';
        }


    }

}