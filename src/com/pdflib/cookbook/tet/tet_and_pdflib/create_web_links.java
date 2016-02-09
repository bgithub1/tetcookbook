package com.pdflib.cookbook.tet.tet_and_pdflib;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pdflib.TET;
import com.pdflib.TETException;
import com.pdflib.pdflib;
import com.pdflib.PDFlibException;

/**
 * For each page in the document: process the page with TET, place it in a new
 * output document with PDFlib+PDI, and add Web links. The position and URL of
 * the generated links is based on the text contents. We look for variations of
 * the string "PDF/A" and for strings that look like domain names. This is
 * defined via the regular expression in variable "pattern".
 * <p>
 * The bounding box of the text (plus some margin) is used as annotation
 * rectangle for a Web link. The option "contentanalysis={nopunctuationbreaks}"
 * for TET.open_page() prevents the wordfinder from breaking URLs at punctuation
 * characters such as "/" and ".".
 * <p>
 * Required software: TET 3 and PDFlib+PDI 8
 * <p>
 * Required data: PDF document
 *
 * @version $Id: create_web_links.java,v 1.11 2015/12/03 13:53:03 stm Exp $
 */
class create_web_links {
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
    private static final String PAGE_OPTLIST =
        "granularity=word contentanalysis={nopunctuationbreaks}";

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
     * The regular expression that shall get annotated with a Web link. In the
     * example we search for "PDF/A", "PDF/A-1", "PDF/A-1a", "PDF/A-1b" and any
     * string that starts with "www." and looks like a domain name, with
     * potential trailing characters like punctuation. We only capture the
     * interesting string to overlay the link only over this part.
     */
    private static final Pattern SEARCH_PATTERN =
                Pattern.compile("(PDF/A(-1[ab]?)?|www(\\.\\w+){2,}).*");

    /**
     * The URL of the web link that shall be placed over the "PDFA..."
     * occurrences.
     */
    private static final String PDFA_URL = "http://www.pdfa.org";

    /**
     * Nudge factor for descender height of the Web links (relative to the font
     * size)
     */
    private static final double DESCENDER = 0.25;

    /**
     * Nudge factor for ascender height of the Web links (relative to the font
     * size)
     */
    private static final double ASCENDER = 0.85;

    /**
     * The format for printing the x and y coordinate values.
     */
    private NumberFormat coordFormat;

    /**
     * The number of links that was created in the output document.
     */
    private int linkCount = 0;;
    
    /**
     * Set this to true to get more verbose output about the creation of
     * the web links.
     */
    private static final boolean VERBOSE = false;

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
     * Process a page: Creste a new page in the output document, place the page
     * from the input document in the output document, and create web links for
     * all occurrences of the relevant text.
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
            for (String text = tet.get_text(page); text != null;
                    text = tet.get_text(page)) {
                /*
                 * Check whether this is text that we want to provide with
                 * a web link.
                 */
                Matcher matcher = SEARCH_PATTERN.matcher(text);

                if (matcher.matches()) {
                    /*
                     * Determine the geometry for the "interesting" part by
                     * looping over the character information. Calculate with
                     * a heuristic factor for ascender and descender to get
                     * the box height correctly.
                     */
                    String match = matcher.group(1);
                    int matchLength = match.length();

                    tet.get_char_info(page);
                    double llx = tet.x;
                    double lly = tet.y - DESCENDER * tet.fontsize;
                    double urx = tet.x + tet.width;
                    double ury = tet.y + ASCENDER * tet.fontsize;

                    for (int i = 1;
                            i < matchLength && tet.get_char_info(page) != -1;
                            i += 1) {
                        urx += tet.width;
                        if (tet.y + ASCENDER * tet.fontsize > ury) {
                            ury = tet.y + ASCENDER * tet.fontsize;
                        }
                    }

                    /*
                     * Construct the URL, depending on whether we found
                     * a domain name or a "PDF/A..." string.
                     */
                    String url = match.startsWith("www")
                        ? "http://" + match
                        : PDFA_URL;

                    String optlist = "url=" + url;
                    int action = p.create_action("URI", optlist);

                    /*
                     * Create a web link for the URL. "annotcolor" creates a
                     * blue border for each link.
                     */
                    optlist = "action={activate="
                        + action + "} annotcolor={rgb 0 0 1}";
                    p.create_annotation(llx, lly, urx, ury, "Link", optlist);

                    if (VERBOSE) {
                        out.println("found \"" + match + "\" at"
                                + " lly " + coordFormat.format(llx)
                                + " lly " + coordFormat.format(lly)
                                + " urx " + coordFormat.format(urx)
                                + " ury " + coordFormat.format(ury));
                    }
                    
                    linkCount += 1;
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
            p.set_info("Creator", "Create Weblinks TET Cookbook Example");
            p.set_info("Author", "PDFlib GmbH");
            p.set_info("Title", infilename);
            p.set_info("Subject", "Create weblinks for text matched by regex \""
                    + SEARCH_PATTERN.pattern() + "\"");

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
            
            out.println("Created PDF output document \"" + outfilename
                    + "\" with " + linkCount  + " content-based Web links.");

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
     *            the name of the file for which the template will be
     *            generated
     * @param outfilename
     *            the name of the output file
     */
    private create_web_links(String infilename, String outfilename) {
        this.infilename = infilename;
        this.outfilename = outfilename;

        coordFormat = NumberFormat.getInstance();
        coordFormat.setMinimumFractionDigits(0);
        coordFormat.setMaximumFractionDigits(2);
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 2) {
            out.println("usage: create_web_links <infilename> <outfilename>");
            return;
        }

        create_web_links t = new create_web_links(args[0], args[1]);
        t.execute();
    }
}
