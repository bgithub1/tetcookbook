package com.pdflib.cookbook.tet.tet_and_pdflib;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pdflib.PDFlibException;
import com.pdflib.TET;
import com.pdflib.TETException;
import com.pdflib.pdflib;

/**
 * Find text with TET, hide it with a white rectangle, and add the replacement
 * text on top of it, to approximate a search-and-replace operation. Note that
 * the replaced text will still be retrievable from the output file.
 * <p>
 * The program has a basic algorithm to handle fragmented words, e.g. hyphenated
 * words or words with "drop caps". It is important to understand the
 * limitations of this approach, as it will produce poor results in some
 * situations. Hyphenations for the replacement word are most likely wrong, the
 * white rectangle could be too large or too small, etc.
 * <p>
 * Having said that, it is generally a bad idea to take this approach to replace
 * text in existing PDF documents, and it should only be used when preparing
 * print documents in certain situations, or as a last resort for online documents.
 * <p>
 * Required software: TET 4 and PDFlib+PDI 8
 * <p>
 * Required data: PDF document
 *
 * @version
 * $Id: search_and_replace_text.java,v 1.11 2015/12/03 14:08:16 stm Exp $
 */
class search_and_replace_text {
    /**
     * Common search path for PDI and TET to find the input document.
     */
    private static final String DOC_SEARCH_PATH = "../input";

    /**
     * Global option list. The program expects the "resource" directory parallel
     * to the "java" directory.
     */
    private static final String GLOBAL_OPTLIST =
        "searchpath={../resource/cmap ../resource/glyphlist "
            + DOC_SEARCH_PATH + "}";

    /**
     * Document specific option list.
     */
    private static final String DOC_OPTLIST = "";

    /**
     * Page-specific option list. The program uses granularity "word" because
     * it matches word-wise with the regular expression defined in
     * constant SEARCH_TERM_REGEX.
     * 
     * "contentanalysis={keephyphenglyphs}" is specified because we want to
     * capture the geometry of hyphens as well, in order to be able to
     * overpaint them in the replacement color.
     */
    private static final String PAGE_OPTLIST = 
    	"granularity=word contentanalysis={keephyphenglyphs}";

    /**
     * The encoding in which the output is sent to System.out. For running the
     * example in a Windows command window, you can set this for example to
     * "windows-1252" for getting Latin-1 output.
     */
    private static final String OUTPUT_ENCODING = System
            .getProperty("file.encoding");

    /**
     * Because of rounding errors, there can be small variations in the
     * baseline information. We use an epsilon value of 0.01 to ignore
     * variations that are too small to be meaningful.
     */
    private static final double BASELINE_EPSILON = 0.01;
    
    /**
     * For printing to System.out in the encoding specified via OUTPUT_ENCODING.
     */
    private static PrintStream out;

    /**
     * The name of the input file
     */
    private String infilename;

    /**
     * The name of the output file
     */
    private String outfilename;

    /**
     * The format for printing the x and y coordinate values.
     */
    private NumberFormat coordFormat;

    /**
     * The search terms to replace, specified as a regular expression. In
     * the example we search for "metadata", and replace it by its uppercase
     * form.
     */
    private static final Pattern SEARCH_TERM_REGEX =
        							Pattern.compile("(?i)metadata");

    /**
     * Font for replacement text.
     */
    private static final String REPLACEMENT_FONT = "Times";

    /**
     * Counter for total replacements.
     */
    private int replacements = 0;

    /**
     * Counter for fragmented words.
     */
    private int fragmented = 0;

    /**
     * Set to true for more verbose output regarding the identified rectangles.
     */
    private static boolean verbose = false;

    /**
     * Helper class to store rectangle data.
     */
    private class rectangle {
        rectangle(double baseline, double fontsize,
                double llx, double lly, double urx, double ury, boolean hyphenated) {
            this.llx = llx;
            this.lly = lly;
            this.urx = urx;
            this.ury = ury;

            this.baseline = baseline;
            this.fontsize = fontsize;
            
            this.hyphenated = hyphenated;
        }

        double width() {
            return urx - llx;
        }

        double height() {
            return ury - lly;
        }

        double llx;
        double lly;
        double urx;
        double ury;

        double fontsize;
        double baseline;
        
        boolean hyphenated;
    }

    /**
     * Import the current page from the PDI import document and place it in the
     * ouput document.
     *
     * @param p
     *            the pdflib object
     * @param pdiHandle
     *            the PDI handle for the input document
     * @param pageno
     *            the current page number
     *
     * @throws PDFlibException
     *             an error occurred in the PDFlib API
     */
    private boolean importPdiPage(pdflib p, int pdiHandle, int pageno)
            throws PDFlibException {
        /*
         * The page size will be adjusted later to match the size of the input
         * pages
         */
        p.begin_page_ext(10, 10, "");
        int pdiPage = p.open_pdi_page(pdiHandle, pageno, "");

        if (pdiPage == -1) {
            System.err.println("Error: " + p.get_errmsg());
            return false;
        }

        /* Place the input page and adjust the page size */
        p.fit_pdi_page(pdiPage, 0, 0, "adjustpage");
        p.close_pdi_page(pdiPage);

        return true;
    }

    /**
     * Split the matched word into fragments. A fragment is defined by
     * having the same baseline and the same fontsize. As soon as one of
     * these values changes, a new fragment starts.
     *
     * @param tet
     *            The TET object
     * @param doc 
     *            The TET document handle for the input document
     * @param page
     *            The page handle for the current page
     * @param pageno
     *            The number of the current page
     * @param matchedText
     *            The currently matched word
     *
     * @return A List containing fragment rectangles
     *
     * @throws TETException
     *             An error occurred in the TET API
     */
    private List<rectangle> analyze_word_fragments(TET tet, final int doc,
        final int page, final int pageno, final String matchedText)
            throws TETException {
        List<rectangle> result = new LinkedList<rectangle>();
        boolean first = true;
        double llx = 0, lly = 0, urx = 0, ury = 0;
        double baseline = 0, fontsize = 0;

        /*
         * Loop over all characters, watch the y position for a jump or a change
         * in the fontsize to detect a word that spreads over two lines or split
         * by other conditions, e.g. "drop caps".
         */
        while (tet.get_char_info(page) != -1) {
            /*
             * Get ascender and descender, which are expressed relative to a
             * font scaling factor of 1000. Descender will be returned as a
             * negative number, therefore it will be added to the baseline y
             * position to get the lower left y value.
             */
            final double descender = tet.pcos_get_number(doc,
                "fonts[" + tet.fontid + "]/descender") / 1000;
            final double ascender = tet.pcos_get_number(doc,
                "fonts[" + tet.fontid + "]/ascender") / 1000;

            if (first) {
                llx = tet.x;
                baseline = tet.y;
                fontsize = tet.fontsize;
                lly = tet.y + descender * tet.fontsize;
                first = false;
            }
            else if (Math.abs(baseline - tet.y) > BASELINE_EPSILON
                || fontsize != tet.fontsize) {
                /*
                 * y value jumped or fontsize changed, so complete the previous
                 * rectangle. Bit 6 of the attributes indicates whether the
                 * previous character was a hyphenation artifact.
                 */
                boolean hyphenated = (tet.attributes & (1 << 6)) != 0;
                result.add(new rectangle(baseline, fontsize, llx, lly, urx, ury,
                    hyphenated));
                baseline = tet.y;
                fontsize = tet.fontsize;
                llx = tet.x;
                lly = tet.y + descender * tet.fontsize;
            }

            urx = tet.x + tet.width;
            ury = tet.y + ascender * tet.fontsize;
        }

        /*
         * Add the last identified rectangle, which can by definition not be
         * hyphenated.
         */
        result
            .add(new rectangle(baseline, fontsize, llx, lly, urx, ury, false));

        if (result.size() > 1) {
            fragmented += 1;

            System.err.println("Warning: On page " + pageno
                + " the search text \"" + matchedText + "\" extends over "
                + "multiple rectangles, starting at " + "x="
                + coordFormat.format(llx) + ", y=" + coordFormat.format(lly)
                + ", result is questionable.");
        }

        return result;
    }

    /**
     * Paint the given rectangle in white.
     *
     * @param p
     *            The pdflib object
     * @param pageno
     *            The number of the current page
     * @param r
     *            The rectangle to paint
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */
    private void paint_rectangle(pdflib p, int pageno, rectangle r)
            throws PDFlibException {
        p.save();
        p.setcolor("fillstroke", "gray", 1, 0, 0, 0);
        p.rect(r.llx, r.lly, r.width(), r.height());
        p.fill();
        p.restore();
        if (verbose) {
            out.println("Painted white rectangle at " + "x="
                    + coordFormat.format(r.llx) + ", y="
                    + coordFormat.format(r.lly) + ", width="
                    + coordFormat.format(r.width()) + ", height="
                    + coordFormat.format(r.height()));
        }
    }

    /**
     * Method that implements the actual replacement.
     *
     * @param matchedText
     *            The text to replace
     * @return The replacement for the matchetText
     */
    private String get_replacement_text(String matchedText) {
        return matchedText.toUpperCase();
    }

    /**
     * Paint the rectangles in white, and fill the rectangles sequentially with
     * text, with the following strategy:
     * <p>
     * - Put at least one character in a rectangle<br>
     * - If this is the last rectangle, fill in the rest of the text<br>
     * - Otherwise fill the rectangle by adding characters until the next
     * character would exceed the rectangle<br>
     *
     * @param font
     *            The font handle
     * @param p
     *            The pdflib object
     * @param pageno
     *            The number of the current page
     * @param matchedText
     *            The matched text
     * @param rectangles
     *            The list of word fragments to replace
     *
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */

    private void replace_fragments(int font, pdflib p, int pageno,
            String matchedText, List<rectangle> rectangles) throws PDFlibException {
        /*
         * Compute the total length of the fragments.
         */
        Iterator<rectangle> i = rectangles.iterator();
        String replacementText = get_replacement_text(matchedText);
        int replacementIndex = 0;
        while (i.hasNext()) {
            rectangle r = (rectangle) i.next();

            paint_rectangle(p, pageno, r);

            int matchedLength = matchedText.length();
            int fragBegin = replacementIndex;
            int fragEnd;

            if (i.hasNext()) {
                /*
                 * Not the last fragment, compute how man characters fit into
                 * the current rectangle.
                 */
                fragEnd = fragBegin;

                String optlist = "font=" + font + " fontsize=" + r.fontsize;
                double filledWidth = 0;

                /*
                 * At least one character is put into the box, plus a hyphen
                 * if the original rectangle ended with a hyphen.
                 */
                do {
                    fragEnd += 1;
                    
                    String fragment = matchedText.substring(fragBegin, fragEnd);
                    if (r.hyphenated) {
                    	fragment += "-";
                    }
                    
                    filledWidth = p.info_textline(fragment, "width", optlist);
                }
                while (filledWidth <= r.width() && fragEnd < matchedLength);
            }
            else {
                /*
                 * The rest of the text.
                 */
                fragEnd = replacementText.length();
            }

            p.save();

            /*
             * The text must be positioned vertically at the same baseline as
             * the original text.
             *
             * PDFlib calculates the scaling for the replacement text so it fits
             * into the box (fitmethod=auto).
             *
             * The setcolor call is intended for highlighting the replacement
             * text, delete this for getting the replacement text in the default
             * color.
             */
            p.setcolor("fillstroke", "rgb", 1, 0, 0, 0);

            String replacementFragment = 
            			replacementText.substring(fragBegin, fragEnd);
            if (r.hyphenated) {
            	replacementFragment += "-";
            }

            String optlist = "font=" + font + " " + "boxsize={" + r.width()
                    + " " + r.fontsize + "} " + "position={left bottom} "
                    + "fitmethod=auto fontsize=" + r.fontsize + " "
                    + "shrinklimit=65%";
            p.fit_textline(replacementFragment, r.llx, r.baseline, optlist);
            p.restore();
            if (verbose) {
                out.println("Replaced \"" + matchedText + "\" with \""
                        + replacementText + "\"");
            }

            replacementIndex = fragEnd;
        }
    }

    /**
     * Check whether the given word matches the search term regular expression,
     * analyze the geometry of the word, replace the fragments with white
     * rectangles and put the replacement word into the fragments.
     *
     * @param tet
     *            The TET object
     * @param doc 
     *            The TET document handle for the input document
     * @param font
     *            Font handle
     * @param p
     *            pdflib object
     * @param page
     *            Handle for the current page
     * @param pageno
     *            The current page number
     * @param word
     *            The current word that potentially will be replaced
     *
     * @throws TETException
     *             An error occurred in the TET API
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */
    private void replace_text(final TET tet, final int doc, final int font, 
    		final pdflib p, final int page,
            final int pageno, final String word) throws TETException, PDFlibException {
        /*
         * Check whether this is text that we want to replace.
         */
        Matcher matcher = SEARCH_TERM_REGEX.matcher(word);

        if (matcher.matches()) {
            replacements += 1;

            String matchedText = matcher.group(0);

            /*
             * List for collecting the rectangles that belong to an instance of
             * the search term
             */
            List<rectangle> rectangles = analyze_word_fragments(tet, doc, page, pageno,
                    matchedText);

            replace_fragments(font, p, pageno, matchedText, rectangles);
        }
    }

    /**
     * Process a page: Create a new page in the output document, place the page
     * from the input document in the output document, and replace all
     * occurrences of the search term with its uppercase form.
     *
     * @param tet
     *            TET object
     * @param doc
     *            TET document handle
     * @param font
     *            Font for replacement text
     * @param p
     *            pdflib object
     * @param pdiHandle
     *            PDI document handle
     * @param pageno
     *            The current page number
     * @throws TETException
     *             An error occurred in the TET API
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */
    private void process_page(TET tet, final int doc, int font, pdflib p,
            int pdiHandle, int pageno) throws TETException, PDFlibException {
        /*
         * Copy page from input document to output document.
         */
        importPdiPage(p, pdiHandle, pageno);

        final int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

        if (page == -1) {
            System.err.println("Error " + tet.get_errnum() + " in "
                    + tet.get_apiname() + "(): " + tet.get_errmsg());
        }
        else {
            /* Retrieve all text fragments for the page */
            for (String text = tet.get_text(page); text != null; text = tet
                    .get_text(page)) {
                replace_text(tet, doc, font, p, page, pageno, text);
            }

            if (tet.get_errnum() != 0) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }

            /*
             * Close page in the input and output documents.
             */
            p.end_page_ext("");
            tet.close_page(page);
        }
    }

    private void execute() {
        TET tet = null;
        pdflib p = null;
        int pageno = 0;

        try {
            tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

            p = new pdflib();
            p.set_option("searchpath={" + DOC_SEARCH_PATH + "}");

            if (p.begin_document(outfilename, "") == -1) {
                System.err.println("Error: " + p.get_errmsg());
                return;
            }

            /* add document info entries */
            p.set_info("Creator", "Search and Replace TET Cookbook Example");
            p.set_info("Author", "PDFlib GmbH");
            p.set_info("Title", infilename);
            p.set_info("Subject", "Replace text matched by regex \""
                    + SEARCH_TERM_REGEX.pattern()
                    + "\" with its uppercase form" );

            int pdiHandle = p.open_pdi_document(infilename, "");
            if (pdiHandle == -1) {
                System.err.println("Error: " + p.get_errmsg());
                return;
            }

            /*
             * Load font and set desired font size.
             */
            int font = p.load_font(REPLACEMENT_FONT, "unicode", "");
            if (font == -1) {
                System.err.println("Error loading font: " + p.get_errmsg());
                return;
            }

            final int doc = tet.open_document(infilename, DOC_OPTLIST);
            if (doc == -1) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
                return;
            }

            /*
             * Loop over pages in the document
             */
            final int n_pages = (int) tet.pcos_get_number(doc, "length:pages");
            for (pageno = 1; pageno <= n_pages; ++pageno) {
                process_page(tet, doc, font, p, pdiHandle, pageno);
            }

            out.println("Replaced " + replacements + " words, "
                    + fragmented + " words were fragmented");

            p.end_document("");
            p.close_pdi_document(pdiHandle);
            tet.close_document(doc);
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
        }
        catch (PDFlibException e) {
            if (pageno == 0) {
                System.err.println("Error " + e.get_errnum() + " in "
                        + e.get_apiname() + "(): " + e.get_errmsg() + "\n");
            }
            else {
                System.err.println("Error " + e.get_errnum() + " in "
                        + e.get_apiname() + "() on page " + pageno + ": "
                        + e.get_errmsg() + "\n");
            }
        }
        finally {
            tet.delete();
            p.delete();
        }
    }

    /**
     * @param infilename
     *            the name of the file for which the file with replaced text
     *            will be generated
     * @param outfilename
     *            the name of the output file
     */
    private search_and_replace_text(String infilename, String outfilename) {
        this.infilename = infilename;
        this.outfilename = outfilename;

        this.coordFormat = NumberFormat.getInstance();
        coordFormat.setMinimumFractionDigits(0);
        coordFormat.setMaximumFractionDigits(2);
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 2) {
            out.println("usage: search_and_replace_text <infilename> <outfilename>");
            return;
        }

        search_and_replace_text t = new search_and_replace_text(args[0], args[1]);
        t.execute();
    }
}
