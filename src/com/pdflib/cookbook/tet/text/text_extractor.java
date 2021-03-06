package com.pdflib.cookbook.tet.text;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.pdflib.TETException;
import com.pdflib.TET;

/**
 * PDF text extractor based on PDFlib TET
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: text_extractor.java,v 1.3 2014/05/26 13:02:11 rjs Exp $
 */
public class text_extractor {
	private final TET tet;
	
	
	
    public text_extractor() {
		super();
		try {
			this.tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

		} catch (TETException e) {
			throw new IllegalStateException(e);
		}
	}

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

    public List<String> getTextLines(String pdfPath){
        try {
			int doc = tet.open_document(pdfPath, DOC_OPTLIST);
            if (doc == -1) {
                throw new IllegalStateException("Error " + tet.get_errnum() + "in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }
            int n_pages = (int) tet.pcos_get_number(doc, "length:pages");
            List<String> ret = new ArrayList<String>();
            
            for (int pageno = 1; pageno <= n_pages; ++pageno) {
                String text;
                int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

                if (page < 0) {
                    print_tet_error(tet, pageno);
                    continue; /* try next page */
                }
                while ((text = tet.get_text(page)) != null) {
                	String[] lines = text.split("\\n");
                	ret.addAll(Arrays.asList(lines));
                }
            }
            return ret;
		} catch (TETException e) {
			throw new IllegalStateException(e);
		}

    }
    
    public static void main(String argv[]) throws UnsupportedEncodingException {
         System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        TET tet = null;

        try {
            if (argv.length != 1) {
                throw new Exception("usage: text_extractor <filename>");
            }

            tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

            int doc = tet.open_document(argv[0], DOC_OPTLIST);
            if (doc == -1) {
                throw new Exception("Error " + tet.get_errnum() + "in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
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
                 * Retrieve all text fragments; This is actually not required
                 * for granularity=page, but must be used for other
                 * granularities.
                 */
                while ((text = tet.get_text(page)) != null) {
                    /* loop over all characters */
                    while (tet.get_char_info(page) != -1) {
                        /*
                         * The following shows how to query the fontname; The
                         * position could be fetched from tet->x and tet->y. The
                         * fontname variable is commented out to prevent an
                         * unused variable warning.
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
        catch (TETException e) {
            System.err.println("TET exception occurred in extractor sample:");
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
     * Report a TET error.
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
