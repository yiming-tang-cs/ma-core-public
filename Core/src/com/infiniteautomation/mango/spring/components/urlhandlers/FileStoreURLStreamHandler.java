/*
 * Copyright (C) 2020 Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.components.urlhandlers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.infiniteautomation.mango.spring.service.FileStoreService;

/**
 * @author Jared Wiltshire
 */
@Component
@SupportedProtocols({"filestore"})
public class FileStoreURLStreamHandler extends URLStreamHandler {

    private final FileStoreService fileStoreService;

    @Autowired
    public FileStoreURLStreamHandler(FileStoreService fileStoreService) {
        this.fileStoreService = fileStoreService;
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        try {
            Path path = fileStoreService.getPathForRead(u.toURI());
            return path.toUri().toURL().openConnection();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

}