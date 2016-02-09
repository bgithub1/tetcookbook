package com.pdflib.cookbook.tet.special;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import com.pdflib.TETException;
import com.pdflib.TET;

/**
 * This topic is a generalized form of the simple text extractor. In addition to
 * the simple version this topic demonstrates how to process many PDF documents
 * in a loop. The interesting point here is error handling and try/catch clauses
 * for TET exceptions and other exceptions. The main goal is to make sure that
 * the loop over all input documents continues even if one or more damaged
 * documents are encountered.
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: multiple_documents.java,v 1.2 2014/05/26 13:02:11 rjs Exp $
 */
public class multiple_documents {
    /**
     * Global option list
     */
    static final String GLOBAL_OPTLIST = "searchpath={../resource/cmap "
            + "../resource/glyphlist ../input}";

    /**
     * Document-specific option list
     */
    static final String DOC_OPTLIST = "";

    /**
     * Page-specific option list
     */
    static final String PAGE_OPTLIST = "granularity=page";

    /**
     * Separator to emit after each chunk of text. This depends on the
     * applications needs; for granularity=word a space character may be useful.
     */
    static final String SEPARATOR = "\n";

    /**
     * Set inmemory to true to generate the image in memory.
     */
    static final boolean INMEMORY = false;

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

    public static void main(String argv[]) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (argv.length < 1) {
            System.err.println("usage: multiple_documents <filename> ...");
            System.exit(1);
        }
        
        TET tet = null;

        try {
            tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

            for (int i = 0; i < argv.length; i += 1) {
                try {
                    int doc = tet.open_document(argv[i], DOC_OPTLIST);
                    if (doc == -1) {
                        System.err.println("Unable to open document \""
                                + argv[i] + "\": error "
                                + tet.get_errnum() + " in "
                                + tet.get_apiname() + "(): " + tet.get_errmsg());
                        continue;
                    }

                    /* get number of pages in the document */
                    int n_pages = (int) tet.pcos_get_number(doc, "length:pages");
        
                    /* loop over pages */
                    for (int pageno = 1; pageno <= n_pages; ++pageno) {
                        String text;
                        int page = tet.open_page(doc, pageno, PAGE_OPTLIST);
        
                        if (page < 0) {
                            print_tet_error(tet, pageno);
                            continue; /* try next page */
                        }
        
                        /*
                         * Retrieve all text fragments; This is actually not
                         * required for granularity=page, but must be used for
                         * other granularities.
                         */
                        while ((text = tet.get_text(page)) != null) {
                            /* loop over all characters */
                            while (tet.get_char_info(page) != -1) {
                                /*
                                 * The following shows how to query the
                                 * fontname; The position could be fetched from
                                 * tet->x and tet->y. The fontname variable is
                                 * commented out to prevent an unused variable
                                 * warning.
                                 */
                                /* String fontname = */
                                tet.pcos_get_string(doc, "fonts[" + tet.fontid
                                        + "]/name");
                            }
        
                            /* print the retrieved text */
                            out.print(text);
        
                            /* print a separator between chunks of text */
                            out.print(SEPARATOR);
                        }
        
                        if (tet.get_errnum() != 0) {
                            print_tet_error(tet, pageno);
                        }
        
                        tet.close_page(page);
                    }
        
                    tet.close_document(doc);
                }
                catch (TETException ex) {
                    System.err.println("Error while processing document \""
                            + argv[i] + "\": error "
                            + ex.get_errnum() + " in "
                            + ex.get_apiname() + "(): " + ex.get_errmsg());
                    
                    /*
                     * Create a new TET object for processing the next document,
                     * as after a TETException has occurred the same TET object
                     * must no longer be used. Do not forget to set the global
                     * options again for the new TET object.
                     */
                    tet.delete();
                    tet = new TET();
                    tet.set_option(GLOBAL_OPTLIST);
                }
            }
        }
        catch (TETException e) {
            System.err.println("TET exception occurred in multiple_documents sample:");
            System.err.println("[" + e.get_errnum() + "] " + e.get_apiname()
                    + ": " + e.get_errmsg());
	    System.exit(1);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
	    System.exit(1);
        }
        finally {
            if (tet != null) {
                tet.delete();
            }
        }
    }

    /**
     * Report a TET error while processing a page.
     * 
     * @param tet
     *            The TET object
     * @param pageno
     *            The page number on which the error occurred
     */
    private static void print_tet_error(TET tet, int pageno) {
        System.err.println("Error " + tet.get_errnum() + " in  "
                + tet.get_apiname() + "() on page " + pageno + ": "
                + tet.get_errmsg());
    }
}
