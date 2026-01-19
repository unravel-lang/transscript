package com.jupiter.transcript.controller;

import com.jupiter.transcript.service.WebDavBrowserService;
import com.jupiter.transcript.vo.FileItem;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
public class WebDavController {

    @Autowired
    private WebDavBrowserService webDavBrowserService;

    @GetMapping("api/files")
    public ResponseEntity<List<FileItem>> browser(@RequestParam(required = false, defaultValue = "") String path) throws IOException {
        return ResponseEntity.ok(webDavBrowserService.browse(path));
    }

    @GetMapping("api/download")
    public void download(@RequestParam(required = false, defaultValue = "") String path,
                                                   @RequestHeader(value = "Range",required = false) String rangeHeader,
                                                   HttpServletResponse response
    ) throws IOException {
        webDavBrowserService.download(path, rangeHeader, response);
    }


}
