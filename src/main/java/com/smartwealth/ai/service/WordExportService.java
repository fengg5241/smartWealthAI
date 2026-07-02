package com.smartwealth.ai.service;

import com.smartwealth.ai.domain.MistakeQuestion;
import com.smartwealth.ai.repository.MistakeQuestionRepository;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class WordExportService {

    private static final Logger log = LoggerFactory.getLogger(WordExportService.class);

    private final MistakeQuestionRepository mistakeRepo;

    public WordExportService(MistakeQuestionRepository mistakeRepo) {
        this.mistakeRepo = mistakeRepo;
    }

    /**
     * Export selected mistakes to a Word document.
     * @param mode "questions-only" | "questions-answers" | "full-analysis"
     */
    public byte[] export(String tenantId, List<Long> mistakeIds, String mode, String notebookName) {
        List<MistakeQuestion> mistakes = new ArrayList<>();
        for (Long id : mistakeIds) {
            mistakeRepo.findByIdAndTenantId(id, tenantId).ifPresent(mistakes::add);
        }

        if (mistakes.isEmpty()) throw new IllegalArgumentException("No mistakes found");

        try (XWPFDocument doc = new XWPFDocument()) {
            // ---- Title ----
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setText(notebookName != null ? notebookName : "错题本");

            XWPFParagraph subtitle = doc.createParagraph();
            subtitle.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subRun = subtitle.createRun();
            subRun.setFontSize(10);
            subRun.setColor("666666");
            subRun.setText("导出日期：" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                    " | 题目数：" + mistakes.size());

            // Spacer
            doc.createParagraph();

            // ---- Questions ----
            int number = 1;
            for (MistakeQuestion m : mistakes) {
                XWPFParagraph qPara = doc.createParagraph();
                qPara.setSpacingBefore(200);

                // Question number + subject + type
                XWPFRun headerRun = qPara.createRun();
                headerRun.setBold(true);
                headerRun.setFontSize(12);
                StringBuilder header = new StringBuilder(number + ". ");
                if (m.getSubject() != null) header.append("[").append(m.getSubject()).append("] ");
                if (m.getQuestionType() != null) header.append("[").append(m.getQuestionType()).append("] ");
                headerRun.setText(header.toString());

                // Question content
                if (m.getContent() != null && !m.getContent().isBlank()) {
                    XWPFParagraph contentPara = doc.createParagraph();
                    XWPFRun contentRun = contentPara.createRun();
                    contentRun.setFontSize(11);
                    contentRun.setText(m.getContent());
                }

                // Answer section
                if ("questions-answers".equals(mode)) {
                    XWPFParagraph ansPara = doc.createParagraph();
                    XWPFRun ansLabel = ansPara.createRun();
                    ansLabel.setBold(true);
                    ansLabel.setFontSize(11);
                    ansLabel.setColor("2563EB");
                    ansLabel.setText("答案：" + (m.getCorrectAnswer() != null ? m.getCorrectAnswer() : ""));
                } else if ("full-analysis".equals(mode)) {
                    XWPFParagraph ansPara = doc.createParagraph();
                    XWPFRun ansLabel = ansPara.createRun();
                    ansLabel.setBold(true);
                    ansLabel.setFontSize(11);
                    ansLabel.setColor("2563EB");
                    ansLabel.setText("答案：" + (m.getCorrectAnswer() != null ? m.getCorrectAnswer() : ""));

                    if (m.getErrorReason() != null && !m.getErrorReason().isBlank()) {
                        XWPFParagraph errPara = doc.createParagraph();
                        XWPFRun errRun = errPara.createRun();
                        errRun.setFontSize(10);
                        errRun.setColor("DC2626");
                        errRun.setText("错因分析：" + m.getErrorReason());
                    }
                }

                // Answer blank area (for questions-only mode)
                if ("questions-only".equals(mode)) {
                    for (int i = 0; i < 4; i++) doc.createParagraph(); // answer space
                }

                // Source / meta
                if (m.getSource() != null && !m.getSource().isBlank()) {
                    XWPFParagraph srcPara = doc.createParagraph();
                    XWPFRun srcRun = srcPara.createRun();
                    srcRun.setFontSize(9);
                    srcRun.setColor("999999");
                    srcRun.setText("来源：" + m.getSource() +
                            (m.getMasteryLevel() != null ? " | 掌握程度：" + m.getMasteryLevel() : ""));
                }

                number++;
            }

            // ---- Answer page (separate, for questions-only) ----
            if ("questions-only".equals(mode)) {
                doc.createParagraph().setPageBreak(true);
                XWPFParagraph ansTitle = doc.createParagraph();
                ansTitle.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun ansTitleRun = ansTitle.createRun();
                ansTitleRun.setBold(true);
                ansTitleRun.setFontSize(16);
                ansTitleRun.setText("参考答案");

                doc.createParagraph();

                int ansNum = 1;
                for (MistakeQuestion m : mistakes) {
                    XWPFParagraph ap = doc.createParagraph();
                    XWPFRun ar = ap.createRun();
                    ar.setBold(true);
                    ar.setFontSize(11);
                    ar.setText(ansNum + ". " + (m.getCorrectAnswer() != null ? m.getCorrectAnswer() : "无"));
                    ansNum++;
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("Word export failed", e);
            throw new RuntimeException("Word export failed: " + e.getMessage(), e);
        }
    }
}
