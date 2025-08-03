package org.example;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FieldBasedPDFMasker {

    private Map<String, FieldMaskingRule> fieldRules;
    private static final int MAX_MEMORY_PAGES = 50; // Process in chunks for large PDFs
    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 14f;
    private static final int FONT_SIZE = 11;

    public FieldBasedPDFMasker() {
        initializeFieldRules();
    }

    // Define masking rules for different field types
    private void initializeFieldRules() {
        fieldRules = new HashMap<>();

        // Enhanced field patterns with better matching
        addFieldRule("name", "XXXXX",
                "(?i)(name|full\\s*name|first\\s*name|last\\s*name|employee\\s*name|customer\\s*name)\\s*[:=]?\\s*([^\\n\\r,;]+)",
                "(?i)(name|full\\s*name|first\\s*name|last\\s*name|employee\\s*name|customer\\s*name)");



        addFieldRule("email", "XXXXX@XXXXX.com",
                "(?i)(email|e-mail|mail|e\\.mail)\\s*[:=]?\\s*([\\w._%+-]+@[\\w.-]+\\.[A-Z]{2,})",
                "(?i)(email|e-mail|mail|e\\.mail)");

        addFieldRule("address", "XXXXX",
                "(?i)(address|addr|location|residence|street|home)\\s*[:=]?\\s*([^\\n\\r]+)",
                "(?i)(address|addr|location|residence|street|home)");

        addFieldRule("dob", "XX/XX/XXXX",
                "(?i)(dob|date\\s*of\\s*birth|birth\\s*date|born|birthday)\\s*[:=]?\\s*(\\d{1,2}[/\\-\\.]\\d{1,2}[/\\-\\.]\\d{2,4})",
                "(?i)(dob|date\\s*of\\s*birth|birth\\s*date|born|birthday)");


    }

    private void addFieldRule(String fieldType, String maskValue, String fullPattern, String fieldPattern) {
        fieldRules.put(fieldType.toLowerCase(), new FieldMaskingRule(fieldType, maskValue, fullPattern, fieldPattern));
    }

    // Enhanced main method to mask PDF with better memory management
    public void maskPDFByFieldNames(String inputPath, String outputPath, List<String> fieldsToMask) {
        try (PDDocument inputDocument = Loader.loadPDF(new File(inputPath))) {

            int totalPages = inputDocument.getNumberOfPages();
            System.out.println("Processing PDF with " + totalPages + " pages...");

            try (PDDocument outputDocument = new PDDocument()) {

                // Process pages in chunks to handle large PDFs
                int processed = 0;
                while (processed < totalPages) {
                    int endPage = Math.min(processed + MAX_MEMORY_PAGES, totalPages);

                    System.out.println("Processing pages " + (processed + 1) + " to " + endPage + "...");

                    // Extract text from current chunk
                    String chunkText = extractTextFromPages(inputDocument, processed, endPage);

                    // Mask the text
                    String maskedText = maskFieldsInText(chunkText, fieldsToMask);

                    // Add masked pages to output document
                    addMaskedPagesToDocument(outputDocument, maskedText);

                    processed = endPage;

                    // Force garbage collection for large documents
                    if (totalPages > 100) {
                        System.gc();
                    }
                }

                outputDocument.save(outputPath);
                System.out.println("Successfully processed and saved " + totalPages + " pages to " + outputPath);
            }

        } catch (IOException e) {
            System.err.println("Error processing PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Extract text from specific page range
    private String extractTextFromPages(PDDocument document, int startPage, int endPage) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(startPage + 1); // PDFBox uses 1-based indexing
        stripper.setEndPage(endPage);
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }

    // Process each page individually for better pattern matching
    public void maskPDFByFieldNamesPerPage(String inputPath, String outputPath, List<String> fieldsToMask) {
        try (PDDocument inputDocument = Loader.loadPDF(new File(inputPath));
             PDDocument outputDocument = new PDDocument()) {

            int totalPages = inputDocument.getNumberOfPages();
            System.out.println("Processing PDF page by page. Total pages: " + totalPages);

            PDFTextStripper stripper = new PDFTextStripper();

            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                System.out.println("Processing page " + (pageNum + 1) + "/" + totalPages);

                // Extract text from current page only
                stripper.setStartPage(pageNum + 1);
                stripper.setEndPage(pageNum + 1);
                String pageText = stripper.getText(inputDocument);

                // Mask fields in current page
                String maskedPageText = maskFieldsInText(pageText, fieldsToMask);

                // Create page with masked content
                createSinglePageWithContent(outputDocument, maskedPageText);

                // Clear memory for large documents
                if (pageNum % 20 == 0 && pageNum > 0) {
                    System.gc();
                }
            }

            outputDocument.save(outputPath);
            System.out.println("Successfully processed all pages!");

        } catch (IOException e) {
            System.err.println("Error processing PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Create a single page with content
    private void createSinglePageWithContent(PDDocument document, String content) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            addContentToPage(contentStream, content, page);
        }
    }

    // Enhanced method to mask specific field with better error handling
    public void maskSpecificField(String inputPath, String outputPath, String fieldName, String maskValue) {
        try (PDDocument inputDocument = Loader.loadPDF(new File(inputPath))) {

            int totalPages = inputDocument.getNumberOfPages();
            System.out.println("Masking field '" + fieldName + "' in " + totalPages + " pages...");

            try (PDDocument outputDocument = new PDDocument()) {
                PDFTextStripper stripper = new PDFTextStripper();

                // Process all pages
                for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                    stripper.setStartPage(pageNum + 1);
                    stripper.setEndPage(pageNum + 1);
                    String pageText = stripper.getText(inputDocument);

                    // Mask the specific field
                    String maskedText = maskDynamicField(pageText, fieldName, maskValue);

                    // Create page with masked content
                    createSinglePageWithContent(outputDocument, maskedText);
                }

                outputDocument.save(outputPath);
                System.out.println("Field masking completed for all pages!");
            }

        } catch (IOException e) {
            System.err.println("Error masking specific field: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Overloaded method with default mask value
    public void maskSpecificField(String inputPath, String outputPath, String fieldName) {
        maskSpecificField(inputPath, outputPath, fieldName, "XXXXX");
    }

    // Enhanced text masking with better pattern matching
    private String maskFieldsInText(String text, List<String> fieldsToMask) {
        String maskedText = text;

        for (String fieldName : fieldsToMask) {
            String fieldKey = fieldName.toLowerCase().trim();

            // Check if we have a predefined rule for this field
            if (fieldRules.containsKey(fieldKey)) {
                FieldMaskingRule rule = fieldRules.get(fieldKey);
                maskedText = applyMaskingRule(maskedText, rule);
            } else {
                // Create dynamic rule for unknown field
                maskedText = maskDynamicField(maskedText, fieldName, "XXXXX");
            }
        }

        return maskedText;
    }

    // Enhanced masking rule application with better formatting preservation
    private String applyMaskingRule(String text, FieldMaskingRule rule) {
        Pattern pattern = Pattern.compile(rule.getFullPattern(), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        StringBuffer result = new StringBuffer();
        int maskCount = 0;

        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String originalValue = matcher.group(2);

            // Preserve the original format structure
            String fullMatch = matcher.group(0);
            String separator = extractSeparator(fullMatch);

            String replacement = fieldName + separator + rule.getMaskValue();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));

            maskCount++;
            System.out.println("Masked field '" + fieldName.trim() + "': '" + originalValue.trim() + "' -> '" + rule.getMaskValue() + "'");
        }
        matcher.appendTail(result);

        if (maskCount > 0) {
            System.out.println("Total instances of '" + rule.getFieldType() + "' masked: " + maskCount);
        }

        return result.toString();
    }

    // Extract separator from matched text
    private String extractSeparator(String fullMatch) {
        if (fullMatch.contains(" = ")) return " = ";
        if (fullMatch.contains(": ")) return ": ";
        if (fullMatch.contains(":")) return ": ";
        if (fullMatch.contains(" =")) return " = ";
        if (fullMatch.contains("=")) return " = ";
        if (fullMatch.contains("-")) return " - ";
        return " ";
    }

    // Enhanced dynamic field masking
    private String maskDynamicField(String text, String fieldName, String maskValue) {
        List<String> patterns = createDynamicPatterns(fieldName);
        String maskedText = text;
        boolean foundMatch = false;
        int totalMasks = 0;

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(maskedText);

            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                foundMatch = true;
                totalMasks++;

                String matchedFieldName = matcher.group(1);
                String originalValue = matcher.group(2);
                String separator = extractSeparator(matcher.group(0));

                String replacement = matchedFieldName + separator + maskValue;
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));

                System.out.println("Masked field '" + matchedFieldName.trim() + "': '" + originalValue.trim() + "' -> '" + maskValue + "'");
            }
            matcher.appendTail(result);
            maskedText = result.toString();

            if (foundMatch) break;
        }

        if (!foundMatch) {
            System.out.println("Warning: Field '" + fieldName + "' not found in the document.");
        } else {
            System.out.println("Total instances of '" + fieldName + "' masked: " + totalMasks);
        }

        return maskedText;
    }

    // Enhanced pattern creation with more comprehensive matching
    private List<String> createDynamicPatterns(String fieldName) {
        List<String> patterns = new ArrayList<>();
        String escapedFieldName = Pattern.quote(fieldName.trim());

        // More flexible patterns to handle various formats
        patterns.add("(?i)(" + escapedFieldName + ")\\s*:\\s*([^\\n\\r]+?)(?=\\s*\\n|\\s*$|\\s{3,})");
        patterns.add("(?i)(" + escapedFieldName + ")\\s*=\\s*([^\\n\\r]+?)(?=\\s*\\n|\\s*$|\\s{3,})");
        patterns.add("(?i)(" + escapedFieldName + ")\\s*-\\s*([^\\n\\r]+?)(?=\\s*\\n|\\s*$|\\s{3,})");
        patterns.add("(?i)(" + escapedFieldName + ")\\s{2,}([^\\n\\r]+?)(?=\\s*\\n|\\s*$|\\s{3,})");
        patterns.add("(?i)(" + escapedFieldName + ")\\s*[:\\-=]\\s*([^\\n\\r,;]+?)(?=\\s*\\n|\\s*$|\\s{3,})");

        return patterns;
    }

    // Auto-detect and mask all sensitive fields with page-by-page processing
    public void maskAllDetectedFields(String inputPath, String outputPath) {
        try (PDDocument inputDocument = Loader.loadPDF(new File(inputPath));
             PDDocument outputDocument = new PDDocument()) {

            int totalPages = inputDocument.getNumberOfPages();
            System.out.println("Auto-masking all detected fields in " + totalPages + " pages...");

            PDFTextStripper stripper = new PDFTextStripper();

            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                stripper.setStartPage(pageNum + 1);
                stripper.setEndPage(pageNum + 1);
                String pageText = stripper.getText(inputDocument);

                String maskedText = pageText;

                // Apply all predefined rules
                for (FieldMaskingRule rule : fieldRules.values()) {
                    maskedText = applyMaskingRule(maskedText, rule);
                }

                createSinglePageWithContent(outputDocument, maskedText);
            }

            outputDocument.save(outputPath);
            System.out.println("Auto-masking completed for all pages!");

        } catch (IOException e) {
            System.err.println("Error in auto-masking: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Enhanced field detection across all pages
    public Set<String> detectFieldNames(String inputPath) {
        Set<String> detectedFields = new HashSet<>();

        try (PDDocument document = Loader.loadPDF(new File(inputPath))) {
            int totalPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                stripper.setStartPage(pageNum + 1);
                stripper.setEndPage(pageNum + 1);
                String pageText = stripper.getText(document);

                // Enhanced pattern to find potential field names
                Pattern fieldPattern = Pattern.compile("([A-Za-z][A-Za-z\\s]{1,30})\\s*[:\\-=]", Pattern.MULTILINE);
                Matcher matcher = fieldPattern.matcher(pageText);

                while (matcher.find()) {
                    String fieldName = matcher.group(1).trim();
                    if (fieldName.length() > 2 && fieldName.length() < 35 && !isCommonWord(fieldName)) {
                        detectedFields.add(fieldName);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error detecting field names: " + e.getMessage());
            e.printStackTrace();
        }

        return detectedFields;
    }

    // Helper method to filter out common words that aren't field names
    private boolean isCommonWord(String word) {
        String lowerWord = word.toLowerCase();
        String[] commonWords = {"the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "this", "that", "these", "those"};
        for (String common : commonWords) {
            if (lowerWord.equals(common)) return true;
        }
        return false;
    }

    // Enhanced method to add masked pages to document with better formatting
    private void addMaskedPagesToDocument(PDDocument document, String maskedText) throws IOException {
        String[] lines = maskedText.split("\\n");
        List<String> currentPageLines = new ArrayList<>();

        for (String line : lines) {
            currentPageLines.add(line);

            // Create new page when we have enough content (approximate)
            if (currentPageLines.size() >= 40) {
                createPageFromLines(document, currentPageLines);
                currentPageLines.clear();
            }
        }

        // Add remaining lines as final page
        if (!currentPageLines.isEmpty()) {
            createPageFromLines(document, currentPageLines);
        }
    }

    // Create a page from list of lines
    private void createPageFromLines(PDDocument document, List<String> lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            addLinesToPage(contentStream, lines, page);
        }
    }

    // Enhanced method to add content to page with better formatting
    private void addContentToPage(PDPageContentStream contentStream, String content, PDPage page) throws IOException {
        String[] lines = content.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n").split("\\n");
        List<String> linesList = Arrays.asList(lines);
        addLinesToPage(contentStream, linesList, page);
    }

    // Add lines to page with proper formatting and page breaks
    private void addLinesToPage(PDPageContentStream contentStream, List<String> lines, PDPage page) throws IOException {
        final float pageHeight = page.getMediaBox().getHeight();
        final float maxWidth = page.getMediaBox().getWidth() - (2 * MARGIN);

        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
        contentStream.setLeading(LINE_HEIGHT);
        contentStream.newLineAtOffset(MARGIN, pageHeight - MARGIN);

        float yPosition = pageHeight - MARGIN;

        for (String line : lines) {
            // Check if we need a new page
            if (yPosition < MARGIN + LINE_HEIGHT) {
                break; // Stop adding lines to this page
            }

            // Handle empty lines
            if (line.trim().isEmpty()) {
                contentStream.newLine();
                yPosition -= LINE_HEIGHT;
                continue;
            }

            // Clean line of control characters
            String safeLine = line.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");

            // Handle long lines by wrapping
            String[] wrappedLines = wrapText(safeLine, 80);
            for (String wrappedLine : wrappedLines) {
                if (yPosition < MARGIN + LINE_HEIGHT) {
                    break;
                }

                contentStream.showText(wrappedLine);
                contentStream.newLine();
                yPosition -= LINE_HEIGHT;
            }
        }

        contentStream.endText();
    }

    // Enhanced text wrapping method
    private String[] wrapText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return new String[]{text};
        }

        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 <= maxLength) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                // Handle very long words
                if (word.length() > maxLength) {
                    // Break long word
                    while (word.length() > maxLength) {
                        lines.add(word.substring(0, maxLength));
                        word = word.substring(maxLength);
                    }
                }

                currentLine.append(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }

    // Helper class to store field masking rules
    private static class FieldMaskingRule {
        private final String fieldType;
        private final String maskValue;
        private final String fullPattern;
        private final String fieldPattern;

        public FieldMaskingRule(String fieldType, String maskValue, String fullPattern, String fieldPattern) {
            this.fieldType = fieldType;
            this.maskValue = maskValue;
            this.fullPattern = fullPattern;
            this.fieldPattern = fieldPattern;
        }

        public String getFieldType() { return fieldType; }
        public String getMaskValue() { return maskValue; }
        public String getFullPattern() { return fullPattern; }
        public String getFieldPattern() { return fieldPattern; }
    }

    // Enhanced main method with multiple usage examples
    public static void main(String[] args) {
        FieldBasedPDFMasker masker = new FieldBasedPDFMasker();

        // Example 1: Mask specific field
        System.out.println("=== Example 1: Masking specific field ===");
        masker.maskSpecificField("C:\\Users\\Avik\\Downloads\\Name_test.pdf", "output_name_masked.pdf", "Name", "----------");


    }
}