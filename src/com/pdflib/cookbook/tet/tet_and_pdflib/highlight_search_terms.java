package com.pdflib.cookbook.tet.tet_and_pdflib;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
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
 * Search term highlighting: Identify all occurrences of a particular word
 * on the page, and make them visible with the "Highlight" annotation.
 * <p>
 * Required software: TET 4 and PDFlib+PDI 8
 * <p>
 * Required data: PDF document
 *
 * @version $Id: highlight_search_terms.java,v 1.11 2015/12/03 13:56:42 stm Exp $
 */
class highlight_search_terms {
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
     * 
     * "contentanalysis={keephyphenglyphs}" is specified because we want to
     * capture the geometry of hyphens as well, in order to be able to
     * include them in the highlighting box.
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
     * The search terms to highlight, specified as a regular expression. In
     * the example we search for "XMP", "Metadata" and "PDFlib" in case
     * insensitive mode. The "u" flag for Unicode-aware case folding is not
     * strictly necessary but included for the general case.
     */
    private static final Pattern SEARCH_TERM_REGEX =
        Pattern.compile("(?iu)XMP|Metadata|PDFlib");

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
     * Process a page: Create a new page in the output document, place the page
     * from the input document in the output document, and highlight all
     * occurrences of the search term.
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
                 * Check whether this is text that we want to highlight.
                 */
                Matcher matcher = SEARCH_TERM_REGEX.matcher(text);

                if (matcher.matches()) {
                    /*
                     * List for collecting the rectangles that belong to an instance
                     * of the search term
                     */
                    List<rectangle> rectangles = new LinkedList<rectangle>();
    
                    String matchedText = matcher.group(0);

                    boolean first = true;
                    double llx = 0, lly = 0, urx = 0, ury = 0, lasty = 0;

                    /*
                     * Loop over all characters, watch the y position for a jump
                     * to detect a word that spreads over two lines
                     */
                    while (tet.get_char_info(page) != -1) {
            			/*
            			 * Get ascender and descender, which are expressed relative to a
            			 * font scaling factor of 1000. Descender will be returned as a
            			 * negative number, therefore it will be added to the baseline y
            			 * position to get the lower left y value.
            			 */
            			final double descender = tet.pcos_get_number(doc, "fonts["
            					+ tet.fontid + "]/descender") / 1000;
            			final double ascender = tet.pcos_get_number(doc, "fonts["
            					+ tet.fontid + "]/ascender") / 1000;
            			
                        if (first) {
                            llx = tet.x;
                            lasty = tet.y;
                            lly = tet.y + descender * tet.fontsize;
                            first = false;
                        }
                        else if (lasty != tet.y) {
                            /*
                             * y value jumped, we have to start a new rectangle
                             */
                            rectangles.add(new rectangle(llx, lly, urx, ury));
                            llx = tet.x;
                            lasty = tet.y;
                            lly = tet.y + descender * tet.fontsize;
                        }

                        urx = tet.x + tet.width;
                        ury = tet.y + ascender * tet.fontsize;
                    }

                    /*
                     * Add the last identified rectangle.
                     */
                    rectangles.add(new rectangle(llx, lly, urx, ury));

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

                    StringBuffer optlist = new StringBuffer(
                            "annotcolor {rgb 0.68 0.85 0.90} linewidth 1 ")
                        .append("title {TET/PDFlib Search Term Highlighting} ")
                        .append("contents {Search Term: ")
                        .append(matchedText)
                        .append("} ")
                        .append("polylinelist {");

                    Iterator<rectangle> i = rectangles.iterator();
                    while (i.hasNext()) {
                        /*
                         * The quadrilaterals have to be built in
                         * the following order:
                         * upper left corner -> upper right corner ->
                         * lower left corner -> lower right corner
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
                        optlist.append(" ").append(r.urx).append(" ").append(
                                r.ury);
                        // lower left corner
                        optlist.append(" ").append(r.llx).append(" ").append(
                                r.lly);
                        // lower right corner
                        optlist.append(" ").append(r.urx).append(" ").append(
                                r.lly);

                        optlist.append("} ");
                    }
                    optlist.append("}");

                    p.create_annotation(minx, miny, maxx, maxy, "Highlight",
                            optlist.toString());
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
            p.set_info("Creator", "Highlight Search Terms TET Cookbook Example");
            p.set_info("Author", "PDFlib GmbH");
            p.set_info("Title", infilename);
            p.set_info("Subject", "Search terms matched by "
                    + "regular expression \""
                    + SEARCH_TERM_REGEX.pattern() + "\"");

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
     *            the name of the file for which the file with highlighted text
     *            will be generated
     * @param outfilename
     *            the name of the output file
     */
    private highlight_search_terms(String infilename, String outfilename) {
        this.infilename = infilename;
        this.outfilename = outfilename;
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 2) {
            out.println("usage: highlight_search_terms <infilename> <outfilename>");
            return;
        }

        highlight_search_terms t = new highlight_search_terms(args[0], args[1]);
        t.execute();
    }
}
