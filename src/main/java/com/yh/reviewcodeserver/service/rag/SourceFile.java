package com.yh.reviewcodeserver.service.rag;

public record SourceFile(
        String filePath,   // 예: "src/main/java/com/yh/.../UserService.java"
        String content     // 파일 전체 텍스트
) {
}
