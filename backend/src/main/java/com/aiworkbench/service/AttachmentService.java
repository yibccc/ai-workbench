package com.aiworkbench.service;

import com.aiworkbench.dto.community.AttachmentModels.*;
import com.aiworkbench.entity.community.AttachmentRow;
import com.aiworkbench.storage.ObjectStorage.StoredObject;
import java.io.InputStream;
import java.util.UUID;

public interface AttachmentService {
    void authorizeUpload(UUID postId);
    Upload upload(UUID postId, long version, UUID requestId, String fileName, InputStream input);
    Download download(UUID postId, UUID attachmentId, boolean author);
    Maintenance recover(UUID postId);
    Maintenance cleanup(UUID postId);
    record Download(AttachmentRow attachment, StoredObject object) {}
}
