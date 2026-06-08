package com.cecurity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.codec.cli.Digest;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArchiveNode(
        String nodeType,
        String id,
        String name,
        String parent,
        String path,
        long archiveId,
        String identifier,
        String date,
        String dateISO8601,
        long size,
        String depositor,
        String firstRead,
        String check,
        //Digest digest,
        //Digest digestCrypt,
        boolean isCrypted,
        Object metas,
        String mimetype,
        int version,
        Object basket,
        Object alert,
        Object metacrypt
) {}