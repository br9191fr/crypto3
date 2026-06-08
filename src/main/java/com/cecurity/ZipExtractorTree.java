package com.cecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.compress.archivers.zip.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

public class ZipExtractorTree {

    /* ---------- JSON NODE ---------- */

    static class Node {
        public String name;
        public boolean directory;
        public Long size;
        public Long lastModified;
        public String permissions;
        public List<Node> children = new ArrayList<>();

        public Node(String name, boolean directory) {
            this.name = name;
            this.directory = directory;
        }
    }

    /* ---------- MAIN ---------- */

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println(
                    "Usage: java ZipExtractorTree zipfile outputDir");
            return;
        }

        String zipPath = args[0];
        String outputDir = args[1];

        Node root = new Node("root", true);

        extract(zipPath, outputDir, root);

        writeJson(root,
                Paths.get(outputDir, "files.json").toFile());

        System.out.println("Done.");
    }

    /* ---------- EXTRACTION ---------- */

    private static void extract(
            String zipFile,
            String outputDir,
            Node root) throws IOException {

        Map<String, Node> nodeMap = new HashMap<>();
        nodeMap.put("", root);

        try (ZipFile zip = new ZipFile(zipFile)) {

            Enumeration<ZipArchiveEntry> entries =
                    zip.getEntries();

            while (entries.hasMoreElements()) {

                ZipArchiveEntry entry = entries.nextElement();

                Path outPath =
                        Paths.get(outputDir, entry.getName());

                if (entry.isDirectory()) {

                    Files.createDirectories(outPath);

                } else {

                    Files.createDirectories(outPath.getParent());

                    try (InputStream is =
                                 zip.getInputStream(entry)) {
                        Files.copy(is, outPath,
                                StandardCopyOption.REPLACE_EXISTING);
                        //System.out.println("Found:"+Paths.get("/HOME/",entry.getName()).getParent());
                    }
                }

                preserveTimestamp(entry, outPath);
                preservePermissions(entry, outPath);

                addToTree(entry, root, nodeMap);
            }
        }
    }

    /* ---------- TREE BUILDER ---------- */

    private static void addToTree(
            ZipArchiveEntry entry,
            Node root,
            Map<String, Node> nodeMap) {

        String path = entry.getName();
        String[] parts = path.split("/");

        String currentPath = "";
        Node parent = root;

        for (int i = 0; i < parts.length; i++) {

            if (parts[i].isEmpty()) continue;

            currentPath += parts[i];

            Node node = nodeMap.get(currentPath);

            boolean isLast = (i == parts.length - 1);

            if (node == null) {

                node = new Node(
                        parts[i],
                        !isLast || entry.isDirectory());

                if (isLast && !entry.isDirectory()) {
                    node.size = entry.getSize();
                    node.lastModified =
                            entry.getLastModifiedDate().getTime();
                    node.permissions =
                            unixPermToString(entry.getUnixMode());
                }

                parent.children.add(node);
                nodeMap.put(currentPath, node);
            }

            parent = node;
            currentPath += "/";
        }
    }

    /* ---------- TIMESTAMP ---------- */

    private static void preserveTimestamp(
            ZipArchiveEntry entry,
            Path path) {

        try {
            Files.setLastModifiedTime(
                    path,
                    FileTime.fromMillis(
                            entry.getLastModifiedDate().getTime()));
        } catch (Exception ignored) {}
    }

    /* ---------- PERMISSIONS ---------- */

    private static void preservePermissions(
            ZipArchiveEntry entry,
            Path path) {

        try {
            int mode = entry.getUnixMode();
            if (mode == 0) return;

            Set<PosixFilePermission> perms =
                    permissionsFromUnixMode(mode);

            Files.setPosixFilePermissions(path, perms);

        } catch (Exception ignored) {
            // Windows or unsupported FS
        }
    }

    private static Set<PosixFilePermission>
    permissionsFromUnixMode(int mode) {

        Set<PosixFilePermission> perms = new HashSet<>();

        if ((mode & 0400) != 0)
            perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0)
            perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0)
            perms.add(PosixFilePermission.OWNER_EXECUTE);

        if ((mode & 0040) != 0)
            perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0)
            perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0)
            perms.add(PosixFilePermission.GROUP_EXECUTE);

        if ((mode & 0004) != 0)
            perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0)
            perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0)
            perms.add(PosixFilePermission.OTHERS_EXECUTE);

        return perms;
    }

    private static String unixPermToString(int mode) {

        StringBuilder sb = new StringBuilder();

        int[] flags = {0400,0200,0100,040,020,010,04,02,01};
        char[] chars =
                {'r','w','x','r','w','x','r','w','x'};

        for (int i = 0; i < flags.length; i++) {
            sb.append((mode & flags[i]) != 0
                    ? chars[i] : '-');
        }

        return sb.toString();
    }

    /* ---------- JSON ---------- */

    private static void writeJson(Node root, File file)
            throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        mapper.writeValue(file, root);
    }
}
