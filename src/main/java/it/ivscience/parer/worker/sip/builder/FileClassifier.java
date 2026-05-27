package it.ivscience.parer.worker.sip.builder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Classifies SIP files as master or annotation based on filename suffix and extension.
 *
 * Priority (top-down, all configurable via parer.cfg):
 *  1. name (no ext) ends with a masterSuffix       → master
 *  2. name (no ext) ends with an annotationSuffix  → annotation
 *  3. extension in masterExtensions                → master
 *  4. extension in annotationExtensions            → annotation
 *  5. default                                      → master
 *
 * Wiring: sip-services.xml.
 */
public class FileClassifier {

    private Set<String> masterSuffixes       = Collections.emptySet();
    private Set<String> annotationSuffixes   = Collections.emptySet();
    private Set<String> masterExtensions     = Collections.emptySet();
    private Set<String> annotationExtensions = Collections.emptySet();

    public void setMasterSuffixes(String csv)       { masterSuffixes       = parseSet(csv); }
    public void setAnnotationSuffixes(String csv)   { annotationSuffixes   = parseSet(csv); }
    public void setMasterExtensions(String csv)     { masterExtensions     = parseSet(csv); }
    public void setAnnotationExtensions(String csv) { annotationExtensions = parseSet(csv); }

    public boolean isAnnotazione(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        String nameNoExt = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String ext       = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";

        for (String s : masterSuffixes)       { if (nameNoExt.endsWith(s)) return false; }
        for (String s : annotationSuffixes)   { if (nameNoExt.endsWith(s)) return true;  }
        if (masterExtensions.contains(ext))    return false;
        if (annotationExtensions.contains(ext)) return true;
        return false;
    }

    private static Set<String> parseSet(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }
}
