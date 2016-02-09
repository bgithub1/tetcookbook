package com.pdflib.cookbook.tet.image;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * For each image, fetch the width and height in pixels from images[] and the
 * width and height in point from the TET_image_info structure. Determine the
 * image resolution in dpi. As the meaning of the dpi information changes
 * if the image is rotated or skewed, a warning is printed in these cases.
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: determine_image_resolution.java,v 1.4 2014/05/26 13:02:11 rjs Exp $
 */
public class determine_image_resolution
{
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
     * Basic image extract options (more below)
     */
    static final String BASE_IMAGE_OPTLIST = "compression=auto format=auto";

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

        TET tet = null;

        try {
            if (argv.length != 1) {
                throw new Exception(
                        "usage: determine_image_resolution <filename>");
            }

            NumberFormat dpiFormat = NumberFormat.getInstance();
            dpiFormat.setMinimumFractionDigits(0);
            dpiFormat.setMaximumFractionDigits(2);

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
                int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

                if (page < 0) {
                    print_tet_error(tet, pageno);
                    continue; /* try next page */
                }

                /* Retrieve all images on the page */
                int imageno = 0;
                while (tet.get_image_info(page) == 1) {
                    /*
                     * Calculate the DPI values for x and y direction, and
                     * warn if the image is rotated or skewed.
                     */
                    String imagePath = "images[" + tet.imageid + "]";
                    int width = (int) tet.pcos_get_number(doc,
                                            imagePath + "/Width");
                    int height = (int) tet.pcos_get_number(doc,
                            imagePath + "/Height");
                    
                    double xDpi = 72 * width / tet.width;
                    double yDpi = 72 * height / tet.height;
                    
                    out.println("Page " + pageno + " image " + imageno + ": "
                            + "resolution (DPI): x="
                            + dpiFormat.format(xDpi) + " y="
                            + dpiFormat.format(yDpi));

                    if (tet.alpha != 0 || tet.beta != 0) {
                        out.println(
                            "  Warning: image is rotated and/or skewed (alpha="
                                + tet.alpha + ", beta=" + tet.beta + "), "
                                + "DPI value may be meaningless");
                    }

                    imageno++;
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
