package colorpicker.i18n;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Parser for the language files (port of {@code LanguageProvider}).
 *
 * <pre>
 * //comment
 * VARIABLE=Whatever, all chars accepted
 * GROUP:VARIABLE=Also accepted
 * #author=Tags defining language file information (author, name, shortname, contact, site)
 * </pre>
 *
 * Everything is case sensitive and {@code \n} in a value becomes a line break.
 */
public final class LanguageProvider {

    private static final List<String> INFO_KEYS = List.of("author", "name", "shortname", "contact", "site");

    private String languageFile;
    private final Map<String, String> entries = new HashMap<>();
    private final Map<String, String> info = new LinkedHashMap<>();
    private final List<Runnable> languageUpdatedListeners = new CopyOnWriteArrayList<>();

    public LanguageProvider() {
        initInfo();
    }

    /** Parses the given language file content and raises {@code LanguageUpdated}. */
    public void openFile(String langFileContent) {
        languageFile = langFileContent == null ? "" : langFileContent;
        read();
        for (Runnable l : languageUpdatedListeners) {
            l.run();
        }
    }

    public String getLanguageFile() {
        return languageFile;
    }

    private static boolean isValidKey(String key) {
        return INFO_KEYS.contains(key);
    }

    private void initInfo() {
        info.clear();
        for (String k : INFO_KEYS) {
            info.put(k, "");
        }
    }

    private void read() {
        entries.clear();
        initInfo();
        String content = languageFile;
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }
        for (String s : content.split("[\\r\\n]+")) {
            if (s.isEmpty() || s.startsWith("//")) {
                continue;
            }
            int index = s.indexOf('=');
            if (index < 0) {
                // The original would crash on such a line; just ignore it.
                continue;
            }
            String name = s.substring(0, index);
            String value = s.substring(index + 1);
            if (name.startsWith("#")) {
                String key = name.substring(1);
                if (isValidKey(key) && !value.isEmpty()) {
                    info.put(key, value);
                }
                continue;
            }
            if (!value.isEmpty()) {
                entries.putIfAbsent(name, value.replace("\\n", "\n"));
            }
        }
    }

    /** @return the translation or {@code null} when the key is missing */
    public String getKey(String key) {
        return entries.get(key);
    }

    /** @return the translation of {@code group:key} or {@code null} when missing */
    public String getKey(String group, String key) {
        return entries.get(group + ":" + key);
    }

    /** @return author, name, shortname, contact or site, or {@code null} for other keys */
    public String getInfo(String key) {
        return isValidKey(key) ? info.get(key) : null;
    }

    public void addLanguageUpdatedListener(Runnable listener) {
        languageUpdatedListeners.add(listener);
    }

    public void removeLanguageUpdatedListener(Runnable listener) {
        languageUpdatedListeners.remove(listener);
    }
}
