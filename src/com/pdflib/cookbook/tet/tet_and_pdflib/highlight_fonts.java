package com.pdflib.cookbook.tet.tet_and_pdflib;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import com.pdflib.PDFlibException;
import com.pdflib.TET;
import com.pdflib.TETException;
import com.pdflib.pdflib;

/**
 * Font highlighting: Search for all fonts that are not ignored (option
 * "-ignorefonts") or that are explicitly identified (option "-includefonts"),
 * and make them visible with the "Highlight" annotation.
 * <p>
 * Required software: TET 3 and PDFlib+PDI 8
 * <p>
 * Required data: PDF document
 *
 * @version $Id: highlight_fonts.java,v 1.7 2015/12/03 13:53:17 stm Exp $
 */
class highlight_fonts {
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
     * Page-specific option list.
     */
    private static final String PAGE_OPTLIST = "granularity=page";

    /**
     * Command line flag for fonts to ignore.
     */
    private static final String IGNORE_OPT = "-ignorefonts";

    /**
     * Command line flag for fonts to include.
     */
    private final static String INCLUDE_OPT = "-includefonts";

    /**
     * The encoding in which the output is sent to System.out. For running the
     * example in a Windows command window, you can set this for example to
     * "windows-1252" for getting Latin-1 output.
     */
    private static final String OUTPUT_ENCODING = System
            .getProperty("file.encoding");

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
     * The list of fonts that are either included or ignored, depending on the
     * value of member "ignore".
     */
    private Set<String> fonts;

    /**
     * If ignore is true, only the fonts not present in the font list are
     * highlighted. If ignore is false, only the fonts in the fonts list are
     * highlighted.
     */
    private boolean ignore;

    /**
     * Nudge factor for ascender height of the annotations (relative to the font
     * size)
     */
    private static final double ASCENDER = 0.85;

    /**
     * Nudge factor for descender height of annotations (relative to the font
     * size)
     */
    private static final double DESCENDER = 0.25;

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
     * Whether to include the font in the output.
     *
     * @param tet
     *            The TET object
     * @param doc
     *            The TET document handle
     * @param pcosId
     *            The pCOS id of the font to check
     *
     * @return true if the font has to be included in the output, otherwise
     *         false
     * @throws TETException
     *             An error occurred in the TET API
     */
    private boolean includeFontInOutput(TET tet, int doc, int pcosId)
            throws TETException {
        String fontName = getFontName(tet, doc, pcosId);
        return ignore != fonts.contains(fontName);
    }

    /**
     * Get the font name for the pCOS id of a font
     *
     * @param tet
     *            The TET object
     * @param doc
     *            The TET document handle
     * @param pcosId
     *            The pCOS id of the font to check
     * @return The name of the font
     * @throws TETException
     *             An error occurred in the TET API
     */
    private String getFontName(TET tet, int doc, int pcosId)
            throws TETException {
        String fontName = tet.pcos_get_string(doc, "fonts["
                + pcosId + "]/name");
        return fontName;
    }

    /**
     * Helper class to store rectangle data.
     */
    private class rectangle {
        rectangle(double llx, double lly, double urx, double ury) {
            this.llx = llx;
            this.lly = lly;
            this.urx = urx;
            this.ury = ury;
        }

        double llx;
        double lly;
        double urx;
        double ury;
    }

    /**
     * Create annotations for a given list of rectangles.
     *
     * @param tet
     *            The TET object
     * @param doc
     *            The TET handle
     * @param p
     *            The pdflib object
     * @param rectangles
     *            The list of rectangles
     * @throws TETException
     *             An error occurred in the TET API
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */
    private void create_annotations(TET tet, final int doc, pdflib p,
            List<rectangle> rectangles, int fontId) throws TETException, PDFlibException {

        StringBuffer optlist = new StringBuffer(
                "annotcolor {rgb 0.68 0.85 0.90} linewidth 1 ")
                .append("title {TET/PDFlib Font Highlighting} ")
                .append("contents {Font: ")
                .append(getFontName(tet, doc, fontId))
                .append("} polylinelist {");

        /*
         * Build the option list for the highlight annotation,
         * including the "polylinelist" option that describes one or
         * multiple rectangles for the highlighting annotation for
         * the potentially hyphenated word.
         *
         * We still need the rectangle that surrounds the separate
         * sub-rectangles of the annotation, for passing it to the
         * function create_annotation(). To get the actual values,
         * we start with impossible values and compute the minimum
         * and maximum accross the relevant values.
         */
        double minx = 1E10, miny = 1E10, maxx = -1, maxy = -1;

        Iterator<rectangle> i = rectangles.iterator();
        while (i.hasNext()) {
            /*
             * The quadrilaterals have to be built in the following
             * order: upper left corner -> upper right corner -> lower
             * left corner -> lower right corner
             */
            rectangle r = (rectangle) i.next();

            minx = Math.min(minx, r.llx);
            miny = Math.min(miny, r.lly);
            maxx = Math.max(maxx, r.urx);
            maxy = Math.max(maxy, r.ury);

            optlist.append("{");

            // upper left corner
            optlist.append(r.llx).append(" ").append(r.ury);
            // upper right corner
            optlist.append(" ").append(r.urx).append(" ").append(r.ury);
            // lower left corner
            optlist.append(" ").append(r.llx).append(" ").append(r.lly);
            // lower right corner
            optlist.append(" ").append(r.urx).append(" ").append(r.lly);

            optlist.append("} ");
        }
        optlist.append("}");

        p.create_annotation(minx, miny, maxx, maxy, "Highlight",
                optlist.toString());
    }

    /**
     * Process a page: Create a new page in the output document, place the page
     * from the input document in the output document, and highlight the
     * relevant text.
     *
     * @param tet
     *            TET object
     * @param doc
     *            TET document handle
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
    private void process_page(TET tet, final int doc, pdflib p, int pdiHandle,
            int pageno) throws TETException, PDFlibException {
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
                /*
                 * List for collecting the rectangles that belong to an instance
                 * of the search term
                 */
                List<rectangle> rectangles = new LinkedList<rectangle>();

                double llx = 0, lly = 0, urx = 0, ury = 0, lasty = 0;
                int fontId = -1;

                /*
                 * Loop over all characters, watch the y position for a jump
                 * and the font id for a change to detect word fragments that
                 * have the same font. Recangles from multiple lines that have
                 * the same font belong to a common annotation.
                 */
                boolean inHighlightSequence = false;
                while (tet.get_char_info(page) != -1) {
                    boolean jumped = lasty != tet.y;
                    boolean fontChange = fontId != tet.fontid;

                    if (jumped || fontChange) {
                        if (inHighlightSequence) {
                            /*
                             * y value jumped or font changed, we have to start
                             * a new rectangle
                             */
                            rectangles.add(new rectangle(llx, lly, urx, ury));

                            /*
                             * If the font changed, the current annotation is
                             * complete.
                             */
                            if (fontChange) {
                                create_annotations(tet, doc, p, rectangles,
                                        fontId);
                                rectangles = new LinkedList<rectangle>();
                            }
                        }
                        inHighlightSequence =
                            includeFontInOutput(tet, doc, tet.fontid);

                        llx = tet.x;
                        lasty = tet.y;
                        lly = tet.y - DESCENDER * tet.fontsize;
                    }
                    fontId = tet.fontid;
                    urx = tet.x + tet.width;
                    ury = tet.y + ASCENDER * tet.fontsize;
                }

                /*
                 * Add the last identified rectangle.
                 */
                if (inHighlightSequence) {
                    rectangles.add(new rectangle(llx, lly, urx, ury));
                    create_annotations(tet, doc, p, rectangles, fontId);
                }
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

    /**
     * Join element of a collection into a string, delimeted by delimiter
     *
     * @param c
     *            Collection of items to join
     * @param delimiter
     *            Delimiter to put between the items
     * @return The joined string
     */
    public static String join(Collection<String> c, String delimiter) {
        StringBuffer buffer = new StringBuffer();
        Iterator<String> iter = c.iterator();
        while (iter.hasNext()) {
            buffer.append(iter.next());
            if (iter.hasNext()) {
                buffer.append(delimiter);
            }
        }
        return buffer.toString();
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
            p.set_info("Creator", "Highlight Fonts TET Cookbook Example");
            p.set_info("Author", "PDFlib GmbH");
            p.set_info("Title", infilename);
            String subjectFonts = join(fonts, ", ");
            String subject = (ignore ? "Ignored Fonts: " : "Included Fonts: ")
                    + subjectFonts;
            p.set_info("Subject", subject.toString());

            int pdiHandle = p.open_pdi_document(infilename, "");
            if (pdiHandle == -1) {
                System.err.println("Error: " + p.get_errmsg());
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
                process_page(tet, doc, p, pdiHandle, pageno);
            }

            p.end_document("");
            p.close_pdi_document(pdiHandle);
            tet.close_document(doc);

            if (ignore) {
                out.println("Created PDF output document \"" + outfilename
                        + "\" with all fonts highlighted except: "
                        + subjectFonts);
            }
            else {
                out.println("Created PDF output document \"" + outfilename
                        + "\" with the following fonts highlighted: "
                        + subjectFonts);
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
     * @param fonts
     *            The list of fonts to be either included or ignored
     * @param ignore
     *            If ignore is true, only the fonts not present in the font list
     *            are highlighted. If ignore is false, only the fonts in the
     *            fonts list are highlighted.
     * @param infilename
     *            The name of the file for which the file with highlighted text
     *            will be generated
     * @param outfilename
     *            The name of the output file
     */
    private highlight_fonts(Set<String> fonts, boolean ignore, String infilename,
            String outfilename) {
        this.infilename = infilename;
        this.outfilename = outfilename;
        this.fonts = fonts;
        this.ignore = ignore;
    }

    /**
     * Splits the list of font names and generates a Set of font names from
     * them.
     *
     * @param fontList
     *            A comma-separated list of font names.
     *
     * @return A Set containing the elements of the font list
     */
    private static Set<String> parse_font_list(String fontList) {
        Set<String> retval = new TreeSet<String>();

        StringTokenizer tokenizer = new StringTokenizer(fontList, ",");

        while (tokenizer.hasMoreTokens()) {
            retval.add(tokenizer.nextToken());
        }

        return retval;
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 4
                || !(args[0].equals(IGNORE_OPT)
                        || args[0].equals(INCLUDE_OPT))) {
            usage();
        }

        Set<String> fonts = parse_font_list(args[1]);

        highlight_fonts t = new highlight_fonts(fonts, args[0].equals(IGNORE_OPT),
                    args[2], args[3]);
        t.execute();
    }

    private static void usage() {
        System.err.println("usage: highlight_fonts [ -ignorefonts <font list> | "
            + " -includefonts <font list> ] <input document> <output document>");
        System.exit(1);
    }
}
