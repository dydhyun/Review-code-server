package com.yh.reviewcodeserver.service.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ChangedFilePathExtractor {

    private static final Pattern DIFF_FILE_PATTERN = Pattern.compile("^\\+\\+\\+ b/(.+\\.java)$", Pattern.MULTILINE);

    public List<String> extract(String diff) {
        Matcher matcher = DIFF_FILE_PATTERN.matcher(diff);
        return matcher.results()
                .map(m -> m.group(1))
                .distinct()
                .toList();
    }

}
