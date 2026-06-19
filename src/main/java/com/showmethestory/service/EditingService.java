package com.showmethestory.service;

import com.showmethestory.model.ChapterState;
import com.showmethestory.model.EditChapterContentRequest;
import com.showmethestory.model.Progress;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Surgical chapter content editing.
 * Maps to Go: editing.go -- EditChapterContent and helpers.
 *
 * Supports 4 operations:
 * - replace_lines: replace a range of lines (1-indexed, inclusive)
 * - replace_text: find and replace an exact text snippet
 * - insert_after_line: insert content after a given line (0 = before first line)
 * - append: append content at the end
 */
@Service
public class EditingService {

    /**
     * Perform a surgical edit on a chapter's content.
     *
     * @param progress the current progress state (modified in place)
     * @param req      the edit request
     * @return the number of lines in the resulting content
     * @throws IllegalArgumentException on validation errors
     */
    public int editChapterContent(Progress progress, EditChapterContentRequest req) {
        // Find the chapter
        ChapterState ch = null;
        for (ChapterState c : progress.getChapters()) {
            if (c.getNum() == req.getChapterNum()) {
                ch = c;
                break;
            }
        }
        if (ch == null) {
            throw new IllegalArgumentException("第 " + req.getChapterNum() + " 章不存在");
        }
        if (ch.getContent() == null || ch.getContent().isEmpty()) {
            throw new IllegalArgumentException("第 " + req.getChapterNum() + " 章正文为空，无法编辑");
        }

        String[] lines = ch.getContent().split("\n", -1);
        int totalLines = lines.length;
        String op = req.getOperation();

        switch (op) {
            case "replace_lines" -> {
                if (req.getStartLine() < 1 || req.getStartLine() > totalLines) {
                    throw new IllegalArgumentException(
                            "起始行 " + req.getStartLine() + " 超出范围（共 " + totalLines + " 行）");
                }
                if (req.getEndLine() < req.getStartLine() || req.getEndLine() > totalLines) {
                    throw new IllegalArgumentException(
                            "结束行 " + req.getEndLine() + " 超出范围（起始行 " + req.getStartLine()
                                    + "，共 " + totalLines + " 行）");
                }
                String[] newLines = req.getNewText().split("\n", -1);
                List<String> result = new ArrayList<>();
                for (int i = 0; i < req.getStartLine() - 1; i++) {
                    result.add(lines[i]);
                }
                result.addAll(Arrays.asList(newLines));
                for (int i = req.getEndLine(); i < totalLines; i++) {
                    result.add(lines[i]);
                }
                ch.setContent(String.join("\n", result));
            }

            case "replace_text" -> {
                if (req.getOldText() == null || req.getOldText().isEmpty()) {
                    throw new IllegalArgumentException("old_text 不能为空");
                }
                int idx = ch.getContent().indexOf(req.getOldText());
                if (idx < 0) {
                    throw new IllegalArgumentException(
                            "未找到匹配文本（长度 " + req.getOldText().length() + " 字符）");
                }
                ch.setContent(
                        ch.getContent().substring(0, idx)
                                + req.getNewText()
                                + ch.getContent().substring(idx + req.getOldText().length()));
            }

            case "insert_after_line" -> {
                if (req.getLine() < 0 || req.getLine() > totalLines) {
                    throw new IllegalArgumentException(
                            "行号 " + req.getLine() + " 超出范围（共 " + totalLines
                                    + " 行，0 表示文件开头）");
                }
                String[] newLines = req.getNewText().split("\n", -1);
                List<String> result = new ArrayList<>();
                for (int i = 0; i < req.getLine(); i++) {
                    result.add(lines[i]);
                }
                result.addAll(Arrays.asList(newLines));
                for (int i = req.getLine(); i < totalLines; i++) {
                    result.add(lines[i]);
                }
                ch.setContent(String.join("\n", result));
            }

            case "append" -> {
                String content = ch.getContent();
                if (!content.isEmpty() && !content.endsWith("\n")) {
                    content += "\n";
                }
                content += req.getNewText();
                ch.setContent(content);
            }

            default -> throw new IllegalArgumentException("未知编辑操作: " + op);
        }

        return ch.getContent().split("\n", -1).length;
    }

    /**
     * Find the index of the chapter with the given num, or -1 if not found.
     */
    public int findChapterIdx(Progress progress, int num) {
        List<ChapterState> chapters = progress.getChapters();
        if (chapters == null) return -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).getNum() == num) {
                return i;
            }
        }
        return -1;
    }
}
