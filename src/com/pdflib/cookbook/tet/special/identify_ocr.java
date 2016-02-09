package com.pdflib.cookbook.tet.special;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Classify the pages in a document according to the following criteria:
 * <p>
 * - No text and raster: The page contains neither text nor raster images, i.e.
 * it is empty or contains only vector graphics<br>
 * - Image only (raw scan): The page contains at least one image, but not any
 * text<br>
 * - Searchable image (also called image+hidden text): The page contains at
 * least one image plus text with textrendering=3 (invisible). No other text is
 * present on the page.<br>
 * - Visible text: The page contains text, and all text uses textrendering!=3<br>
 * - Mixed: all other cases, i.e. mixed textrendering modes<br>
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: identify_ocr.java,v 1.3 2014/05/26 13:02:11 rjs Exp $
 */
class identify_ocr {
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
     * Page-specific option list.
     */
    private static final String PAGE_OPTLIST = "granularity=glyph";
    
    /**
     * The name of the input file
     */
    private String filename;
    
    /**
     * Invisible text rendering mode.
     */
    private static final int INVISIBLE_TEXT_RENDERING = 3;
    
    /**
     * Process a page in the document, and print out the classification of the
     * page.
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
            // Is invisible text present?
            boolean hasInvisibleText = false;
            
            // Is normal text present?
            boolean hasNormalText = false;
            
            /* Retrieve all text fragments for the page */
            for (String text = tet.get_text(page); text != null; text = tet
                    .get_text(page)) {
                /* loop over all characters */
                while (tet.get_char_info(page) != -1) {
                    if (tet.textrendering == INVISIBLE_TEXT_RENDERING) {
                        hasInvisibleText = true;
                    }
                    else {
                        hasNormalText = true;
                    }
                }
            }
            
            /* Check whether there's at least one raster image on the page */
            boolean hasImage = false;
            if (tet.get_errnum() == 0) {
                hasImage = tet.get_image_info(page) == 1;
            }

            if (tet.get_errnum() == 0) {
                boolean hasText = hasInvisibleText || hasNormalText;
                
                System.out.print("Page " + pageno + ": ");
                if (hasText) {
                    if (hasImage && hasInvisibleText && !hasNormalText) {
                        System.out.print("Searchable image");
                    }
                    else if (hasNormalText && !hasInvisibleText) {
                        System.out.print("Visible text");
                    }
                    else {
                        System.out.print("Mixed");
                    }
                }
                else {
                    // no text at all
                    if (hasImage) {
                        System.out.print("Image only");
                    }
                    else {
                        System.out.print("No text or raster graphics");
                    }               
                }
            
                System.out.println();
            }
            else {
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
    
            System.out.println("Page classification for document \""
                    + filename + "\"");    
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
    private identify_ocr(String filename) {
        this.filename = filename;
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("usage: identify_ocr <infilename>");
            return;
        }

        identify_ocr t = new identify_ocr(args[0]);
        t.execute();
    }
}
