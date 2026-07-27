package com.yh.reviewcodeserver.dto;
// workflow 로 받을 임베딩 요청 DTO
public record IndexingRequest(
        String repoName,
        String filePath,
        String content

){}
