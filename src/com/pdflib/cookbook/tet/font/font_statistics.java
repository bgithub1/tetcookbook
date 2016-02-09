package com.pdflib.cookbook.tet.font;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * For each font in a document display the following information:
 * <p>
 * - embedding status<br>
 * - number of glyphs and Unicode characters (if different suggests the
 * existence of ligatures)<br>
 * - total number of unmapped glyphs, i.e. glyphs for which TET could
 * not determine any Unicode mapping<br>
 * - number of unique glyphs with Unicode mappings in the PUA range
 * (U+E000-U+F8FF); many PUA mappings indicate a symbolic font<br>
 * - percentage of glyphs in this font based on the total number of glyphs in
 * the document<br>
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 *
 * @version $Id: font_statistics.java,v 1.14 2015/12/03 11:44:23 stm Exp $
 */
class font_statistics {
    /**
     * Global option list. The program expects the "resource" directory parallel
     * to the "java" directory.
     */
    private static final String GLOBAL_OPTLIST = "searchpath={../resource/cmap "
            + "../resource/glyphlist ../input}";

    /**
     * Document specific option list. As we want to count the PUA characters,
     * TET must be instructed not to replace them.
     */
    private static final String DOC_OPTLIST = "keeppua";

    /**
     * Page-specific option list.
     */
    private static final String PAGE_OPTLIST = "granularity=glyph";

    /**
     * The encoding in which the output is sent to System.out. For running
     * the example in a Windows command window, you can set this for example to
     * "windows-1252" for getting Latin-1 output.
     */
    private static final String OUTPUT_ENCODING = System.getProperty("file.encoding");

    /**
     * For printing to System.out in the encoding specified via OUTPUT_ENCODING.
     */
    private static PrintStream out;

    /**
     * Start of the Unicode PUA range.
     */
    private static final int PUA_RANGE_START = (int) '\ue000';

    /**
     * End of the Unicode PUA range.
     */
    private static final int PUA_RANGE_END = (int) '\uf8ff';

    private class Font implements Comparable<Object> {
        /**
         * The font id, which is the index in the pCOS "fonts" pseudo object.
         */
        int id;

        /**
         * The number of glyphs used from this font.
         */
        int glyphCount;

        /**
         * The number of Unicode characters used from this font.
         */
        int unicodeCharacterCount;

        /**
         * The number of unmapped glyphs from this font.
         */
        int unmappedGlyphCount;

        /**
         * A Map<int, int> to count the unique glyphs with Unicode mappings in
         * the PUA range (U+E000-U+F8FF). The key is the PUA value, the value is
         * the number of occurrences.
         */
        Map<Integer, Integer> puaGlyphs = new HashMap<Integer, Integer>();

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        public int compareTo(Object o) {
            Font other = (Font) o;
            return glyphCount < other.glyphCount ? -1
                    : (glyphCount == other.glyphCount ? 0 : 1);
        }
    }

    /**
     * The name of the file to process.
     */
    private String filename;

    /**
     * An array of Font instances to collect information about the fonts. The
     * length of the array corresponds to the length of the "fonts[]" pCOS
     * pseudo object array.
     */
    private Font[] fontInfos = null;

    /**
     * The total number of glyphs in the document
     */
    private int totalGlyphCount = 0;

    /**
     * The total number of Unicode characters in the document.
     */
    private int totalUnicodeCharacterCount = 0;
    
    /**
     * The total number of unmapped glyphs in the document.
     */
    private int totalUnmappedGlyphCount = 0;

    /**
     * Comment
     *
     * @param tet
     *            TET object
     * @param doc
     *            TET document handle
     * @param pageno
     *            Page to process
     *
     * @throws TETException
     *             An error occurred in the TET API
     */
    private void process_page(TET tet, final int doc, int pageno)
            throws TETException {
        final int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

        if (page == -1) {
            System.err.println("Error " + tet.get_errnum() + " in "
                    + tet.get_apiname() + "(): " + tet.get_errmsg());
        }
        else {
            /*
             * Retrieve all glyphs for the page and count the characters and
             * glyphs.
             */
            for (String text = tet.get_text(page); text != null; text = tet
                    .get_text(page)) {
                for (int ci = tet.get_char_info(page); ci != -1; ci = tet
                        .get_char_info(page)) {
                    Font fontInfo = fontInfos[tet.fontid];

                    switch (tet.type) {
                    case 0:
                    case 1:
                        /*
                         * Normal character which corresponds to exactly one
                         * glyph (0), or start of a sequence (1, e.g. ligature)
                         */
                        fontInfo.glyphCount += 1;
                        totalGlyphCount += 1;
                        
                        if (tet.unknown) {
                            fontInfo.unmappedGlyphCount += 1;
                            totalUnmappedGlyphCount += 1;
                        }
                        else {
                            fontInfo.unicodeCharacterCount += 1;
                            totalUnicodeCharacterCount += 1;
                        }

                        count_pua(tet, fontInfo);
                        break;

                    case 10:
                        /*
                         * Continuation of a sequence (e.g. ligature). If a
                         * glyph can be mapped to a sequence of Unicode
                         * characters, it can by definition not be unknown.
                         */
                        fontInfo.unicodeCharacterCount += 1;
                        totalUnicodeCharacterCount += 1;
                        count_pua(tet, fontInfo);
                        break;

                    case 11:
                        // Trailing value of a surrogate pair; the leading value
                        // has type=0, 1, or 10.
                        break;

                    case 12:
                        // Inserted word, line, or zone separator
                        break;
                    }
                }
            }

            if (tet.get_errnum() != 0) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }

            tet.close_page(page);
        }
    }

    /**
     * Analyze the current Unicode character, and update the PUA statistics if
     * it is inside the PUA range.
     *
     * @param tet
     *            The TET object describing the current Unicode character
     * @param fontInfo
     *            The FontInfo object for the font of the current character
     */
    private void count_pua(TET tet, Font fontInfo) {
        if (tet.uv >= PUA_RANGE_START && tet.uv <= PUA_RANGE_END) {
            Integer uv = new Integer(tet.uv);
            Integer newValue;
            if (fontInfo.puaGlyphs.containsKey(uv)) {
                // Increment counter
                Integer oldValue = fontInfo.puaGlyphs.get(uv);
                newValue = new Integer(oldValue.intValue() + 1);
            }
            else {
                // Initialize with first counted character
                newValue = new Integer(1);
            }
            fontInfo.puaGlyphs.put(uv, newValue);
        }
    }

    /**
     * Constructor for font_statistics object
     *
     * @param filename
     *            The name of the file for which the statistics shall be
     *            generated.
     */
    private font_statistics(String filename) {
        this.filename = filename;
    }

    /**
     * Print out the results.
     *
     * @throws TETException
     */
    private void print_statistics(TET tet, int doc) throws TETException {
        out.println("Font statistics for document \"" + filename + "\"");
        out.println(totalGlyphCount + " total glyphs in the document, "
                + totalUnicodeCharacterCount
                + " total Unicode characters, "
                + totalUnmappedGlyphCount
                + " unmapped glyphs; breakdown by font:");
        out.println();

        // Sort the fonts according to their glyph counts.
        Arrays.sort(fontInfos);

        // Print the font information in descending order
        for (int i = fontInfos.length - 1; i >= 0; i -= 1) {
            Font font = fontInfos[i];

            // Get name of font from pCOS
            String fontName = tet.pcos_get_string(doc, "fonts[" + font.id
                    + "]/name");
            double percentage = ((double) font.glyphCount) / totalGlyphCount
                    * 100.0;

            // Get embedding status
            boolean embedded = tet.pcos_get_number(doc, "fonts[" + font.id
                    + "]/embedded") != 0;

            NumberFormat format = NumberFormat.getInstance();
            format.setMinimumFractionDigits(0);
            format.setMaximumFractionDigits(2);

            out.print(format.format(percentage) + "% " + fontName);

            out.print(": " + font.glyphCount + " glyphs, " + font.unicodeCharacterCount + " Unicode characters (");

            out.print(embedded ? "embedded" : "not embedded");

            boolean hasUnmapped = font.unmappedGlyphCount > 0;
            boolean hasPua = font.puaGlyphs.size() > 0;
            if (hasUnmapped || hasPua) {
                out.print(", " + font.unmappedGlyphCount + " unknown, ");

                // Sum up the total number of PUA characters for this font.
                int puaGlyphs = 0;
                Set<Entry<Integer, Integer>> entrySet = font.puaGlyphs.entrySet();
                Iterator<Entry<Integer, Integer>> iterator = entrySet.iterator();
                while (iterator.hasNext()) {
                    Entry<Integer, Integer> entry = iterator.next();
                    Integer value = (Integer) entry.getValue();
                    puaGlyphs += value.intValue();
                }
                out.print(puaGlyphs + " PUA characters, ");

                out.print(font.puaGlyphs.size()
                        + " unique PUA characters)");
            }
            out.println(")");
        }
    }

    /**
     * Generate the statistics for the given file.
     */
    private void execute() {
        TET tet = null;
        int pageno = 0;

        try {
            tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

            final int doc = tet.open_document(filename, DOC_OPTLIST);
            if (doc == -1) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }
            else {
                /*
                 * Prepare the fontInfo array to collect the data for the
                 * statistics.
                 */
                int fontCount = (int) tet.pcos_get_number(doc, "length:fonts");
                fontInfos = new Font[fontCount];

                /*
                 * Save the id inside each FontInfo instance, as we will later
                 * sort the array according to the glyph count.
                 */
                for (int i = 0; i < fontCount; i += 1) {
                    fontInfos[i] = new Font();
                    fontInfos[i].id = i;
                }

                /*
                 * Loop over pages in the document
                 */
                final int n_pages = (int) tet.pcos_get_number(doc,
                        "length:pages");
                for (pageno = 1; pageno <= n_pages; ++pageno) {
                    process_page(tet, doc, pageno);
                }

                print_statistics(tet, doc);

                tet.close_document(doc);
            }
        }
        catch (TETException e) {
            if (pageno == 0) {
                System.err.println("Error " + e.get_errnum() + " in "
                        + e.get_apiname() + "(): " + e.get_errmsg() + "\n");
            }
            else {
                System.err.println("Error " + e.get_errnum() + " in "
                        + e.get_apiname() + "() on page " + pageno + ": "
                        + e.get_errmsg() + "\n");
            }
	    System.exit(1);
        }
        finally {
            tet.delete();
        }
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 1) {
            out.println("usage: font_statistics <infilename>");
	    System.exit(1);
        }

        font_statistics fs = new font_statistics(args[0]);
        fs.execute();
    }
}
