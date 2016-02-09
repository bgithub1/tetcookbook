package com.pdflib.cookbook.tet.text;

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
 * @version $Id: TextExtractor.java,v 1.3 2014/05/26 13:02:11 rjs Exp $
 */
public class TextExtractor {
	private final TET tet;
	
	
	/**
	 * no arg constructor which instantiates an instance of TET
	 * Example:
	 *  public static void main(String argv[]) throws UnsupportedEncodingException {
	 *    TextExtractor te = new TextExtractor();
	 *    List<String> lines = te.getTextLines(argv[0]);
	 *    for(String s : lines){
	 *    	System.out.println(s);
	 *    }    	
	 *  }
	 *  
	 */
    public TextExtractor() {
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
    static final String GLOBAL_OPTLIST = "searchpath={./ ../resource/cmap "
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
