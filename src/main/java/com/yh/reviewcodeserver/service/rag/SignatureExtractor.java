package com.yh.reviewcodeserver.service.rag;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import org.springframework.stereotype.Component;

@Component
public class SignatureExtractor {

    public String extract(SourceFile file) {
        // 파싱전에 자바 17 레벨로 전역 설정을 변경
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

        CompilationUnit compilationUnit = StaticJavaParser.parse(file.content());
        StringBuilder sb = new StringBuilder();

        compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            // 어노테이션 (@Service, @Entity 등)
            clazz.getAnnotations().forEach(a -> sb.append(a).append("\n"));

            // 필드
            for (FieldDeclaration field : clazz.getFields()) {
                sb.append("- ").append(field.toString().replace("\n", " ")).append("\n");
            }

            // 메서드 시그니처 (본문 제외)
            for (MethodDeclaration method : clazz.getMethods()) {
                sb.append("- ")
                        .append(method.getDeclarationAsString(true, true, true))
                        .append("\n");
            }
        });

        compilationUnit.findAll(RecordDeclaration.class).forEach(record -> {
            record.getAnnotations().forEach(a -> sb.append(a).append("\n"));

            sb.append("record ").append(record.getNameAsString()).append("\n");

            record.getParameters().forEach(param ->
                    sb.append("- ").append(param.toString()).append("\n"));

            for (MethodDeclaration method : record.getMethods()) {
                sb.append("- ")
                        .append(method.getDeclarationAsString(true, true, true))
                        .append("\n");
            }
        });

        return sb.toString();
    }
}