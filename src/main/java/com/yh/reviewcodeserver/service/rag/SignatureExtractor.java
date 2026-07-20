package com.yh.reviewcodeserver.service.rag;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Component;

@Component
public class SignatureExtractor {

    public String extract(SourceFile file) {
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
        return sb.toString();
    }
}