package com.cecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class FolderNodeReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Read from a JSON string */
    public static FolderNode fromJson(String json) throws IOException {
        return MAPPER.readValue(json, FolderNode.class);
    }

    /** Read from a File */
    public static FolderNode fromFile(File file) throws IOException {
        return MAPPER.readValue(file, FolderNode.class);
    }

    /** Read from a classpath resource */
    public static FolderNode fromResource(String resourcePath) throws IOException {
        try (InputStream is = FolderNodeReader.class
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return MAPPER.readValue(is, FolderNode.class);
        }
    }

    // Quick test
    public static void main(String[] args) throws IOException {
        String json = """
                {
                    "nodeType": "folder",
                    "id": "folder55675",
                    "name": "dir2026-2/sub1/new4",
                    "parent": "folder52214",
                    "path": "HOME/dir2026-2/sub1/new4",
                    "folderType": "standard",
                    "folderId": 55675,
                    "numChilds": 0,
                    "numChildFolders": 0,
                    "childs": null
                }
                """;

        FolderNode node = fromJson(json);
        System.out.println(node);
        System.out.println("id      : " + node.id());
        System.out.println("name    : " + node.name());
        System.out.println("folderId: " + node.folderId());
    }
}