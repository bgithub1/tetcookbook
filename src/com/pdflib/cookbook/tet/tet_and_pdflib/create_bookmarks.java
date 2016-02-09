package com.pdflib.cookbook.tet.tet_and_pdflib;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import com.pdflib.PDFlibException;
import com.pdflib.TET;
import com.pdflib.TETException;
import com.pdflib.pdflib;

/**
 * For each page in the document: Process the page with TET, place it in a new
 * output document with PDFlib+PDI, and generate bookmarks based on page
 * content. The text to bookmark is determined with a certain font size and
 * font name.
 * <p>
 * Required software: TET 3 and PDFlib+PDI 8
 * <p>
 * Required data: PDF document
 *
 * @version $Id: create_bookmarks.java,v 1.10 2015/12/03 13:52:34 stm Exp $
 */
class create_bookmarks {
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
     * The encoding in which the output is sent to System.out. For running
     * the example in a Windows command window, you can set this for example to
     * "windows-1252" for getting Latin-1 output.
     */
    private static final String OUTPUT_ENCODING =
                            System.getProperty("file.encoding");

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
     * The number of bookmarks created in the output document.
     */
    private int bookmarkCount = 0;

    /**
     * The name of the font to search for.
     */
    private static final String FONT_NAME = "TheSansBold-Plain";

    /**
     * The font size to search for in points.
     */
    private static final double FONT_SIZE = 9;

    /**
     * The tolerance for the font size in points.
     */
    private static final double FONT_SIZE_TOLERANCE = 0.01;

    /**
     * Nudge factor for ascender height of the Web links (relative to the font
     * size)
     */
    private static final double ASCENDER = 0.85;

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
         * The page size will be adjusted later to match the size of the
         * input pages
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
     * Tests whether the current character matches the criteria for text that
     * shall get a bookmark. get_char_info must have been called before in order
     * to ensure that the TET object contains the information for the current
     * character.
     *
     * @param tet
     *            The TET object
     * @param doc
     *            The TET document handle
     * @throws TETException
     */
    private boolean fontMatches(TET tet, final int doc) throws TETException {
        String name = tet.pcos_get_string(doc,
                "fonts[" + tet.fontid + "]/name");
        return name.equals(FONT_NAME) &&
            (Math.abs(tet.fontsize - FONT_SIZE) <= FONT_SIZE_TOLERANCE);
    }

    /**
     * Process a page: Create a new page in the output document, place the page
     * from the input document in the output document, and create bookmarks for
     * all occurrences of text with the desired properties.
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
    private void process_page(TET tet, final int doc, pdflib p, int pdiHandle, int pageno)
            throws TETException, PDFlibException {
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
                int nextCharPos = 0;

                int matchStart = -1;

                double uly = 0;
                double ulx = 0;

                while (tet.get_char_info(page) != -1) {
                    nextCharPos += 1;

                    if (fontMatches(tet, doc)) {
                        if (matchStart == -1) {
                            // start of new matching chunk
                            matchStart = nextCharPos - 1;
                            uly = tet.y + ASCENDER * tet.fontsize;
                            ulx = tet.x;
                        }
                    }
                    else {
                        if (matchStart != -1) {
                            /*
                             * End of matching chunk. The last character that is
                             * belonging to the chunk is the one preceding the
                             * next character. matchEnd is the index of the
                             * first character after the bookmark characters.
                             */
                            int matchEnd = nextCharPos - 1;

                            // remove trailing whitespace
                            while (matchEnd > matchStart
                                    && Character.isWhitespace(
                                        text.charAt(matchEnd - 1))) {
                                matchEnd -= 1;
                            }

                            String bookmarkText =
                                text.substring(matchStart, matchEnd);
                            out.println("Creating bookmark \""
                                    + bookmarkText + "\"");
                            p.create_bookmark(bookmarkText,
                                    "destination={type=fixed left=" + ulx
                                                    + " top=" + uly + "}");
                            matchStart = -1;
                            
                            bookmarkCount += 1;
                        }
                    }
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
            p.set_option("searchpath={"+ DOC_SEARCH_PATH + "}");

            if (p.begin_document(outfilename, "") == -1) {
                System.err.println("Error: " + p.get_errmsg());
                return;
            }

            /* add document info entries */
            p.set_info("Creator", "Create Bookmarks TET Cookbook Example");
            p.set_info("Author", "PDFlib GmbH");
            p.set_info("Title", infilename);
            p.set_info("Subject", "Create bookmarks for text with font \""
                    + FONT_NAME + "\", font size "
                    + FONT_SIZE + "pt");

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
            
            out.println("Created PDF output document \"" + outfilename
                    + "\" with " + bookmarkCount  + " content-based bookmarks.");
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
     *            the name of the file for which the bookmarked file will be
     *            generated
     * @param outfilename
     *            the name of the output file
     */
    private create_bookmarks(String infilename, String outfilename) {
        this.infilename = infilename;
        this.outfilename = outfilename;
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 2) {
            out.println("usage: create_bookmarks <infilename> <outfilename>");
            return;
        }

        create_bookmarks t = new create_bookmarks(args[0], args[1]);
        t.execute();
    }
}
