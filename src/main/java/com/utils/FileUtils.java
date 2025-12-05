package com.detextion.utils;

import java.io.File;

public class FileUtils {
    public static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndex = name.lastIndexOf('.');
        if (lastIndex > 0 && lastIndex < name.length() - 1) {
            return name.substring(lastIndex + 1).toLowerCase();
        }
        return "";
    }
}

