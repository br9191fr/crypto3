package com.cecurity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FolderNode(
        String nodeType,
        String id,
        String name,
        String parent,
        String path,
        String folderType,
        int folderId,
        int numChilds,
        int numChildFolders,
        Object childs
) {}