package com.yh.reviewcodeserver.service.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Component
public class LocalFileSystemProvider implements CodeSourceProvider{

    @Value("${app.rag.local-source-path}")
    private String basePath;

    @Override
    public List<SourceFile> fetchAllFiles(String repoName) {
        Path root = Paths.get(basePath);
        try (Stream<Path> pathStream = Files.walk(root)){
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> new SourceFile(
                            root.relativize(p).toString(),
                            readContent(p)
                    )).toList();

        } catch (IOException e) {
            throw new RuntimeException("코드 스캔 실패: " + repoName, e);
        }

    }

    @Override
    public SourceFile fetchFile(String repoName, String filePath) {
        Path target = Paths.get(basePath, filePath);
        return new SourceFile(filePath, readContent(target));
    }

    private String readContent(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("파일 읽기 실패: " + path, e);
        }
    }

}
