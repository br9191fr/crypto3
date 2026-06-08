package com.cecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ArchiveNodeReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Read from a JSON string */
    public static ArchiveNode fromJson(String json) throws IOException {
        return MAPPER.readValue(json, ArchiveNode.class);
    }

    /** Read from a File */
    public static ArchiveNode fromFile(File file) throws IOException {
        return MAPPER.readValue(file, ArchiveNode.class);
    }

    /** Read from a classpath resource */
    public static ArchiveNode fromResource(String resourcePath) throws IOException {
        try (InputStream is = ArchiveNodeReader.class
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return MAPPER.readValue(is, ArchiveNode.class);
        }
    }

    // Quick test
    public static void main(String[] args) throws IOException {
        String json = """
                {
                    "nodeType": "archive",
                    "id": "archive548689",
                    "name": "image1",
                    "parent": "52218",
                    "path": null,
                    "archiveId": 548689,
                    "identifier": "image1",
                    "date": "1780935431007",
                    "dateISO8601": "2026-06-08T18:17:11.007+02:00",
                    "size": 54672,
                    "depositor": "bruno",
                    "firstRead": null,
                    "check": null,
                    "digest": {
                        "algo": "SHA256",
                        "value": "5c5837a6df0cdd62553a99364247fb0d4485f0e4c6fc47ad7bca57af918fa3c5"
                    },
                    "digestCrypt": {
                        "algo": "SHA256",
                        "value": "6617409e25d23c2541d39fe16091edfda2e5aae651b9b17f376ab008d01f5c55"
                    },
                    "isCrypted": true,
                    "metas": null,
                    "mimetype": null,
                    "version": 0,
                    "basket": null,
                    "alert": null,
                    "metacrypt": null
                }
                """;

        ArchiveNode node = fromJson(json);
        System.out.println(node);
        System.out.println("id         : " + node.id());
        System.out.println("name       : " + node.name());
        System.out.println("size       : " + node.size());
        System.out.println("isCrypted  : " + node.isCrypted());
        //System.out.println("digest algo: " + node.digest().toString());
        //System.out.println("digest val : " + node.digest().toString());
    }
}