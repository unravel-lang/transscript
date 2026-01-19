package com.jupiter.transcript.vo;

public record FileItem(String name, String path, boolean isDirectory, long size, String lastModified) {}