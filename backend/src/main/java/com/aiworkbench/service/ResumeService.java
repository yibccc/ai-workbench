package com.aiworkbench.service;

import com.aiworkbench.dto.resume.ResumeModels.*;
import java.io.InputStream;
import java.util.UUID;

public interface ResumeService {
    Current current();
    Receipt save(Save request);
    Receipt importMarkdown(long expectedVersion,UUID requestId,String markdownText,String fileName,InputStream input);
    Receipt delete(long expectedVersion,UUID requestId);
    Download original();
    Maintenance recover();
    Maintenance cleanup();
}
