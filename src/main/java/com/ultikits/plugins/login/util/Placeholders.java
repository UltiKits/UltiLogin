package com.ultikits.plugins.login.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the {@code {NAME}} placeholders of a message line in one pass.
 * <p>
 * Filling one placeholder after another with {@code String#replace} rewrites a value already
 * inserted whenever that value itself contains a later placeholder's token -- an operator's file
 * path, or a value as the operator wrote it. Here the template is scanned once, so each value is
 * inserted exactly as written (the same fix as UltiKits/UltiMail#37, applied to this module).
 *
 * @author wisdomme
 * @version 1.0.0
 */
public final class Placeholders {

    private Placeholders() {
    }

    /**
     * Fills {@code template}'s placeholders in one pass; a value is never expanded again.
     *
     * @param template       the message line, for example from a language file
     * @param namesAndValues pairs of a placeholder token (such as {@code "{FILE}"}) and its value
     * @return the filled line; a token with no value is left as written
     */
    public static String fill(String template, String... namesAndValues) {
        Map<String, String> values = new HashMap<>();
        StringBuilder alternatives = new StringBuilder();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            values.put(namesAndValues[i], String.valueOf(namesAndValues[i + 1]));
            if (alternatives.length() > 0) {
                alternatives.append('|');
            }
            alternatives.append(Pattern.quote(namesAndValues[i]));
        }
        if (values.isEmpty()) {
            return template;
        }
        Matcher m = Pattern.compile(alternatives.toString()).matcher(template);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(values.get(m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }
}
