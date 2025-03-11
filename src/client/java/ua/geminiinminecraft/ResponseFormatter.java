package ua.geminiinminecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseFormatter {
    private static final String PREFIX = "§b>§r ";

    public static String processResponse(String response) {
        if (response.contains("```minecraft")) {
            return PREFIX + response;
        }

        List<String> codeBlocks = new ArrayList<>();
        Pattern codeBlockPattern = Pattern.compile("```(?:.*?)\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher codeBlockMatcher = codeBlockPattern.matcher(response);

        StringBuilder sb = new StringBuilder();
        int codeBlockIndex = 0;

        while (codeBlockMatcher.find()) {
            String placeholder = "##CODE_BLOCK_" + codeBlockIndex + "##";
            codeBlocks.add("§7§o" + codeBlockMatcher.group(1) + "§r");
            codeBlockMatcher.appendReplacement(sb, placeholder);
            codeBlockIndex++;
        }
        codeBlockMatcher.appendTail(sb);
        String formattedResponse = sb.toString();

        List<String> inlineCodes = new ArrayList<>();
        Pattern inlineCodePattern = Pattern.compile("`([^`]*)`");
        Matcher inlineCodeMatcher = inlineCodePattern.matcher(formattedResponse);

        sb = new StringBuilder();
        int inlineCodeIndex = 0;

        while (inlineCodeMatcher.find()) {
            String placeholder = "##INLINE_CODE_" + inlineCodeIndex + "##";
            inlineCodes.add("§8§o" + inlineCodeMatcher.group(1) + "§r");
            inlineCodeMatcher.appendReplacement(sb, placeholder);
            inlineCodeIndex++;
        }
        inlineCodeMatcher.appendTail(sb);
        formattedResponse = sb.toString();

        formattedResponse = formattedResponse
                .replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "§l§o$1§r") // Bold italic
                .replaceAll("\\*\\*(.+?)\\*\\*", "§l$1§r") // Bold
                .replaceAll("\\*(.+?)\\*", "§o$1§r") // Italic
                .replaceAll("__(.+?)__", "§n$1§r") // Underline
                .replaceAll("~~(.+?)~~", "§m$1§r") // Strikethrough
                .replaceAll("==(.+?)==", "§e$1§r"); // Highlight with yellow

        formattedResponse = formattedResponse
                .replaceAll("\\\\\\*", "*")
                .replaceAll("\\\\_", "_")
                .replaceAll("\\\\~", "~")
                .replaceAll("\\\\=", "=")
                .replaceAll("\\\\`", "`")
                .replaceAll("(§[lonme])([^§]+)(?!§r)", "$1$2§r");

        formattedResponse = formattedResponse
                .replaceAll("(?m)^- (.+)$", "§8• §f$1")
                .replaceAll("(?m)^\\d+\\. (.+)$", "§8• §f$1");

        for (int i = 0; i < codeBlocks.size(); i++) {
            formattedResponse = formattedResponse.replace("##CODE_BLOCK_" + i + "##", "\n" + codeBlocks.get(i) + "\n");
        }

        for (int i = 0; i < inlineCodes.size(); i++) {
            formattedResponse = formattedResponse.replace("##INLINE_CODE_" + i + "##", inlineCodes.get(i));
        }

        if (formattedResponse.lastIndexOf("§") > formattedResponse.lastIndexOf("§r")) {
            formattedResponse += "§r";
        }

        return PREFIX + formattedResponse;
    }
}