package com.pdflib.cookbook.tet.tet_and_pdflib;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.pdflib.PDFlibException;
import com.pdflib.TET;
import com.pdflib.TETException;
import com.pdflib.pdflib;

/**
 * Split a document into smaller parts based on some page contents. Various
 * criteria for the split points could be useful. The splitting could for
 * example be done<br>
 * <br>
 * - after each empty page<br>
 * - when certain text appears on the page (e.g. "Address"). The text could be
 * visible on the page, or it could serve as a hidden marker (e.g. invisible
 * text or text outside the CropBox)<br>
 * <p>
 * The example below uses the latter approach. The input document
 * "invoices.pdf" contains a sequence of invoices. Each invoice has one or more
 * pages. The first page contains the recipient's address and the fixed text
 * "INVOICE" at known coordinates. Subsequent pages of the same invoice are
 * blank in these places.
 * <p>
 * The goal is to split the input document into multiple output documents based
 * on the recipient's country. A real-world benefit of this could be that the
 * postage is cheaper if letters are delivered sorted by country.
 * In the same spirit, the invoices could be sorted according zu ZIP code,
 * name of the addressee, etc.
 * <p>
 * Required software: TET 3 and PDFlib+PDI 8
 * <p>
 * Required data: PDF document
 *
 * @version $Id: burst.java,v 1.10 2015/12/03 13:52:20 stm Exp $
 */
class burst {
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
     * x-position of the lower left corner of the rectangle that contains
     * the text for detecting the first page of a sequence.
     */
    private static final double START_SEQ_TXT_LLX = 50;

    /**
     * y-position of the lower left corner of the rectangle that contains
     * the text for detecting the first page of a sequence.
     */
    private static final double START_SEQ_TXT_LLY = 535;

    /**
     * x-position of the upper right corner of the rectangle that contains
     * the text for detecting the first page of a sequence.
     */
    private static final double START_SEQ_TXT_URX = 105;

    /**
     * y-position of the upper right corner of the rectangle that contains
     * the text for detecting the first page of a sequence.
     */
    private static final double START_SEQ_TXT_URY = 550;

    /**
     * Text that must be found in the rectangle defined by START_SEQ_TXT_LLX,
     * START_SEQ_TXT_LLY, START_SEQ_TXT_URX, START_SEQ_TXT_URY in order to
     * identify a page as the start of a sequence.
     */
    private static final String START_SEQ_TXT = "INVOICE";

    /**
     * x-position of the lower left corner of the rectangle that contains
     * the text for the routing criterion.
     */
    private static final double CRITERION_TXT_LLX = 50;

    /**
     * y-position of the lower left corner of the rectangle that contains
     * the text for the routing criterion.
     */
    private static final double CRITERION_TXT_LLY = 612;

    /**
     * x-position of the upper right corner of the rectangle that contains
     * the text for the routing criterion.
     */
    private static final double CRITERION_TXT_URX = 175;

    /**
     * y-position of the upper right corner of the rectangle that contains
     * the text for the routing criterion.
     */
    private static final double CRITERION_TXT_URY = 624;

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
    private String outfileBasename;

    /**
     * For mapping country names to output files. The key is the country
     * name in lowercase, the value is an object that describes the output
     * document.
     */
    private Map<String, output_document> outputDocuments = new HashMap<String, output_document>();

    /**
     * The current pdflib object, used for all pages after the first one.
     */
    private output_document currentOutputDocument = null;

    /**
     * Description of an output document.
     */
    private class output_document {
        pdflib p;
        int pdiHandle;
        String filename;
    }

    /**
     * Import the current page from the PDI import document and place it in the
     * ouput document.
     *
     * @param doc
     *            The output document
     * @param pageno
     *            The current page number in the input document
     *
     * @throws PDFlibException
     *             an error occurred in the PDFlib API
     */
    private boolean importPdiPage(output_document doc, int pageno)
            throws PDFlibException {
        /*
         * The page size will be adjusted later to match the size of the
         * input pages
         */
        doc.p.begin_page_ext(10, 10, "");
        int pdiPage = doc.p.open_pdi_page(doc.pdiHandle, pageno, "");

        if (pdiPage == -1) {
            throw new PDFlibException("Error: " + doc.p.get_errmsg());
        }

        /* Place the input page and adjust the page size */
        doc.p.fit_pdi_page(pdiPage, 0, 0, "adjustpage");
        doc.p.close_pdi_page(pdiPage);
        doc.p.end_page_ext("");

        return true;
    }

    /**
     * This routine implements the detection of the first page of a sequence.
     *
     * @param tet
     *            The TET object for the input document
     * @param doc
     *            The TET handle for the current page
     * @param pageNumber
     *            The number of the current page
     *
     * @return true if this is the first page of a sequence, false otherwise
     *
     * @throws TETException
     *             An error occurred in the TET API
     */
    private boolean isFirstOfSequence(TET tet, int doc, int pageNumber)
            throws TETException {
        String includeBox = "includebox={{ "
            + START_SEQ_TXT_LLX + " "
            + START_SEQ_TXT_LLY + " "
            + START_SEQ_TXT_URX + " "
            + START_SEQ_TXT_URY + " }}";

        int page = tet.open_page(doc, pageNumber,
                PAGE_OPTLIST + " " + includeBox);

        String text = tet.get_text(page);
        boolean retval = text != null && text.equals(START_SEQ_TXT);
        tet.close_page(page);

        return retval;
    }

    /**
     * Fetch the routing criterion from the area of interest.
     *
     * @param tet
     *            The TET object for the input document
     * @param doc
     *            The TET handle for the input document
     * @param pageNumber
     *            The number of the current page
     *
     * @return The String for looking up the output document
     *
     * @throws TETException
     *             An error occurred in the TET API
     */
    private String getRoutingCriterion(TET tet, int doc, int pageNumber)
            throws TETException {
        String includeBox = "includebox={{ " + CRITERION_TXT_LLX + " "
                + CRITERION_TXT_LLY + " " + CRITERION_TXT_URX + " "
                + CRITERION_TXT_URY + " }}";

        int page = tet.open_page(doc, pageNumber, PAGE_OPTLIST + " "
                + includeBox);

        String text = tet.get_text(page);
        tet.close_page(page);

        return text;
    }

    /**
     * Fetch the output document based on the criterion. Create a new output
     * document if none exists yet for the criterion.
     *
     * @param criterion
     *            Criterion for identifying the output document
     *
     * @return The output document for the criterion
     *
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */
    private output_document fetchOutputDocument(String criterion)
            throws PDFlibException {
        output_document retval = (output_document) outputDocuments
                .get(criterion);

        if (retval == null) {
            String outputFilename = outfileBasename + "_"
                    + criterion.toString().toLowerCase() + ".pdf";

            pdflib p = new pdflib();
            p.set_option("searchpath={" + DOC_SEARCH_PATH + "}");

            if (p.begin_document(outputFilename, "") == -1) {
                throw new PDFlibException("Error: " + p.get_errmsg());
            }

            /* add document info entries */
            p.set_info("Creator", "Burst TET Cookbook Example");
            p.set_info("Author", "PDFlib GmbH");
            p.set_info("Title", infilename);
            p.set_info("Subject", "Invoices for recipient country "
                    + criterion.toString());

            int pdiHandle = p.open_pdi_document(infilename, "");
            if (pdiHandle == -1) {
                throw new PDFlibException("Error: " + p.get_errmsg());
            }

            retval = new output_document();
            retval.p = p;
            retval.pdiHandle = pdiHandle;
            retval.filename = outputFilename;

            outputDocuments.put(criterion, retval);
        }

        return retval;
    }

    /**
     * Based on some criteria decide to which output document the current page
     * should go. First the function identifies whether the page is the start of
     * a new sequence or the continuation of a sequence. In the first case the
     * output document is looked up in the map of output documents, and created
     * if necessary. In the second case the page is simply routed to the current
     * document.
     *
     * @param tet
     *            The TET object for the input document
     * @param doc
     *            The TET handle for the input document
     * @param pageNumber
     *            The number of the current page
     *
     * @return The document to which the current page of the input document
     *         shall be routed to
     *
     * @throws TETException
     *             An error occurred in the TET API
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */
    private output_document routePage(TET tet, int doc, int pageNumber)
                throws TETException, PDFlibException {
        if (currentOutputDocument == null
                || isFirstOfSequence(tet, doc, pageNumber)) {
            String criterion = getRoutingCriterion(tet, doc, pageNumber);
            currentOutputDocument = fetchOutputDocument(criterion);
        }

        return currentOutputDocument;
    }

    /**
     * Process a page: Determine into which output document the current
     * page should be placed, and put it into the output document.
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
    private void process_page(TET tet, final int doc, int pageno)
            throws TETException, PDFlibException {
        final int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

        if (page == -1) {
            System.err.println("Error " + tet.get_errnum() + " in "
                    + tet.get_apiname() + "(): " + tet.get_errmsg());
        }
        else {
            /*
             * Decide about routing the input pages
             */
            output_document o = routePage(tet, doc, pageno);

            /*
             * Copy page from input document to output document.
             */
            importPdiPage(o, pageno);

            /*
             * Close page in the input document.
             */
            tet.close_page(page);
        }
    }

    private void execute() {
        TET tet = null;
        int pageno = 0;

        try {
            tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

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
                process_page(tet, doc, pageno);
            }

            /*
             * Close all output documents
             */
            Collection<output_document> values = outputDocuments.values();
            Iterator<output_document> i = values.iterator();
            while (i.hasNext()) {
                output_document o = (output_document) i.next();
                o.p.end_document("");
                o.p.close_pdi_document(o.pdiHandle);
                out.println("Closed output document \"" + o.filename + "\"");
            }

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
            Collection<output_document> values = outputDocuments.values();
            Iterator<output_document> i = values.iterator();
            while (i.hasNext()) {
                output_document o = (output_document) i.next();
                o.p.delete();
            }
        }
    }

    /**
     * @param infilename
     *            the name of the file for which the bookmarked file will be
     *            generated
     * @param outfilename
     *            the name of the output file
     */
    private burst(String infilename, String outfilename) {
        this.infilename = infilename;

        /*
         * As the input document will be split into multiple output documents,
         * strip a potential ".pdf" suffix from the name.
         */
        int basenameEnd = outfilename.toLowerCase().lastIndexOf(".pdf");
        this.outfileBasename = basenameEnd == -1
            ? outfilename
            : outfilename.substring(0, basenameEnd);
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 2) {
            out.println("usage: burst <infilename> <outfile basename>");
            return;
        }

        burst t = new burst(args[0], args[1]);
        t.execute();
    }
}
