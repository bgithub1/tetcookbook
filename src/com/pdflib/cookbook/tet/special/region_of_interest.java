package com.pdflib.cookbook.tet.special;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Restrict text extraction to a particular "region of interest", i.e. some area
 * on the page based on knowledge about the document layout. This can easily be
 * implemented with the "includebox" and "excludebox" options of
 * TET_open_page().
 * <p>
 * To determine the coordinates, it can be helpful to switch on the 
 * display of cursor coordinates in Acrobat and to select point as unit:
 * <p>
 * 1) Display cursor coordinates:<br>
 * <br>
 * - Acrobat 7/8: View, Navigation Panels, Info<br>
 * - Acrobat 9: View, Cursor Coordinates<br>
 * <p>
 * 2) Select points as unit:<br>
 * <br>
 * - Acrobat 7/8/9: Edit, Preferences, [General], Units&Guides, Page&Ruler,
 * Points<br>
 * - In Acrobat 7/8 you can also use Options, Points in the Info panel<br>
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: region_of_interest.java,v 1.7 2014/05/26 13:02:11 rjs Exp $
 */
class region_of_interest {
    /**
     * Global option list. The program expects the "resource" directory parallel
     * to the "java" directory.
     */
    private static final String GLOBAL_OPTLIST = "searchpath={../resource/cmap "
            + "../resource/glyphlist ../input}";

    /**
     * Document specific option list.
     */
    private static final String DOC_OPTLIST = "";

    /**
     * Page-specific option list. Here we define the region(s) of interest
     * via an "includebox" list of rectangles. In this case we define the
     * includebox so the footer line of the input document is not included.
     * <p>
     * As an alternative the footer line could be excluded with the
     * "excludebox" option. To try this with the input document, replace
     * the "includebox" option below with the following:
     * <p>
     * excludebox={{0 0 430 70}}
     */
    private static final String PAGE_OPTLIST =
        "granularity=page includebox={{30 70 430 670}}";
    
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
    private String filename;
    
    /**
     * Process a page from the input document.
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
    private static void process_page(TET tet, final int doc, int pageno)
            throws TETException {
        final int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

        if (page == -1) {
            System.err.println("Error " + tet.get_errnum() + " in "
                    + tet.get_apiname() + "(): " + tet.get_errmsg());
        }
        else {
            /* Retrieve all text fragments for the page */
            for (String text = tet.get_text(page); text != null; text = tet
                    .get_text(page)) {
                out.println(text);
            }

            if (tet.get_errnum() != 0) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }

            tet.close_page(page);
        }
    }

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
                return;
            }
    
            /*
             * Loop over pages in the document
             */
            final int n_pages = (int) tet.pcos_get_number(doc, "length:pages");
            for (pageno = 1; pageno <= n_pages; ++pageno) {
                process_page(tet, doc, pageno);
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
	    System.exit(1);
        }
        finally {
            tet.delete();
        }
    }
    
    /**
     * @param filename
     *            the name of the file for which the template will be
     *            generated
     */
    private region_of_interest(String filename) {
        this.filename = filename;
    }
    
    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 1) {
            out.println("usage: region_of_interest <infilename>");
            return;
        }

        region_of_interest t = new region_of_interest(args[0]);
        t.execute();
    }
}
