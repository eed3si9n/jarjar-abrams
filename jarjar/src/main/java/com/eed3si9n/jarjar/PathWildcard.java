package com.eed3si9n.jarjar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathWildcard {
    protected static Pattern dstar = Pattern.compile("\\*\\*");
    protected static Pattern star = Pattern.compile("\\*");
    protected static Pattern estar = Pattern.compile("\\+\\??\\)\\Z");
    protected final Pattern pattern;
    protected final int count;
    protected final ArrayList<Object> parts = new ArrayList<Object>(16); // kept for debugging
    protected final String[] strings;
    protected final int[] refs;

    public PathWildcard(String pattern, String result) {
        if (pattern.equals("**"))
            throw new IllegalArgumentException("'**' is not a valid pattern");
        if (pattern.indexOf("***") >= 0)
            throw new IllegalArgumentException("The sequence '***' is invalid in a package pattern");

        String regex = pattern;
        regex = replaceAllLiteral(dstar, regex, "(.+?)");
        regex = replaceAllLiteral(star, regex, "([^/]+)");
        regex = replaceAllLiteral(estar, regex, "*)");
        this.pattern = Pattern.compile("\\A" + regex + "\\Z");

        // TODO: check for illegal characters
        char[] chars = result.toCharArray();
        int max = 0;
        for (int i = 0, mark = 0, state = 0, len = chars.length; i < len + 1; i++) {
            char ch = (i == len) ? '@' : chars[i];
            if (state == 0) {
                if (ch == '@') {
                    parts.add(new String(chars, mark, i - mark));
                    mark = i + 1;
                    state = 1;
                }
            } else {
                switch (ch) {
                    case '0': case '1': case '2': case '3': case '4':
                    case '5': case '6': case '7': case '8': case '9':
                        break;
                    default:
                        if (i == mark)
                            throw new IllegalArgumentException("Backslash not followed by a digit");
                        int n = Integer.parseInt(new String(chars, mark, i - mark));
                        if (n > max)
                            max = n;
                        parts.add(new Integer(n));
                        mark = i--;
                        state = 0;
                }
            }
        }
        int size = parts.size();
        strings = new String[size];
        refs = new int[size];
        Arrays.fill(refs, -1);
        for (int i = 0; i < size; i++) {
            Object v = parts.get(i);
            if (v instanceof String) {
                strings[i] = ((String)v).replace('.', '/');
            } else {
                refs[i] = ((Integer)v).intValue();
            }
        }
        this.count = this.pattern.matcher("foo").groupCount();
        if (count < max)
            throw new IllegalArgumentException("Result includes impossible placeholder \"@" + max + "\": " + result);
        // System.err.println(this);
    }

    protected static boolean checkIdentifierChars(String expr, String extra) {
        // package-info violates the spec for Java Identifiers.
        // Nevertheless, expressions that end with this string are still legal.
        // See 7.4.1.1 of the Java language spec for discussion.
        if (expr.endsWith("package-info")) {
            expr = expr.substring(0, expr.length() - "package-info".length());
        }
        for (int i = 0, len = expr.length(); i < len; i++) {
            char c = expr.charAt(i);
            if (extra.indexOf(c) >= 0)
                continue;
            // Dash ('-') support added to accommodate class files under META-INF and scala packages.
            if (!(Character.isJavaIdentifierPart(c) || c == '-'))
                return false;
        }
        return true;
    }

    protected static String replaceAllLiteral(Pattern pattern, String value, String replace) {
        replace = replace.replaceAll("([$\\\\])", "\\\\$0");
        return pattern.matcher(value).replaceAll(replace);
    }

    public boolean matches(String value) {
        return getMatcher(value) != null;
    }

    public String replace(String value) {
        Matcher matcher = getMatcher(value);
        if (matcher != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < strings.length; i++)
                sb.append((refs[i] >= 0) ? matcher.group(refs[i]) : strings[i]);
            return sb.toString();
        }
        return null;
    }

    private Matcher getMatcher(String value) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.matches() && PathWildcard.checkIdentifierChars(value, "/."))
            return matcher;
        return null;
    }

    public String toString() {
        return "Wildcard{pattern=" + pattern + ",parts=" + parts + "}";
    }
}
