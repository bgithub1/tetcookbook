package com.pdflib.cookbook.tet.image;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Determine image orientation and mirroring
 * <p>
 * The program analyses the alpha and beta values that are returned by
 * get_image_info(). For images that are rotated by a multiple of 90 degrees
 * and/or mirrored, it prints instructions how to obtain an image from the
 * extracted image that is oriented in the same manner as it is displayed
 * within the PDF document.
 * <p>
 * Required software: TET 4
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: image_orientation.java,v 1.1 2015/12/11 15:15:31 stm Exp $
 */
public class image_orientation
{
    /**
     * Global option list
     */
    static final String globaloptlist = "searchpath={{../input}}";
    
    /**
     * Document-specific option list
     */
    static final String docoptlist = "";
    
    /**
     * Page-specific option list e.g.
     * "imageanalysis={merge={gap=1} smallimages={maxwidth=20}}"
     */
    static final String pageoptlist = "";
    
    /**
     * Epsilon to avoid exact comparison of angles.
     */
    static final double EPSILON = 0.1;

    public static void main (String argv[])
    {
        TET tet = null;
        
	try
        {
	    if (argv.length != 1)
            {
                throw new Exception("usage: image_orientation <filename>");
            }
            
            tet = new TET();

            tet.set_option(globaloptlist);

            int doc = tet.open_document(argv[0], docoptlist);

            if (doc == -1)
            {
                throw new Exception("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }
            
            System.out.println("If an image is transformed, the instructions tell how get the orientation in the PDF from the extracted image.");
            
            /* Get number of pages in the document */
            int n_pages = (int) tet.pcos_get_number(doc, "length:pages");

            /* Loop over pages to trigger image merging */
            for (int pageno = 1; pageno <= n_pages; ++pageno)
            {
                int page = tet.open_page(doc, pageno, pageoptlist);

                if (page != -1) {
                    report_image_orientations(tet, doc, pageno, page);
                    tet.close_page(page);
                }
                else {
                    print_tet_error(tet, pageno);
                }
            }

            tet.close_document(doc);
        }
	catch (TETException e)
	{
	    System.err.println(
		"TET exception occurred in image_resources sample:");
	    System.err.println("[" + e.get_errnum() + "] " + e.get_apiname() +
			    ": " + e.get_errmsg());
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
        }
        finally
        {
            if (tet != null) {
		tet.delete();
            }
        }
    }


    /**
     * Print the orientation information for all the images on a single page.
     * 
     * @param tet
     *            TET object
     * @param doc
     *            TET document handle
     * @param pageno
     *            Page number
     * @param pageid
     *            TET page handle
     * 
     * @throws com.pdflib.TETException
     */
    private static void report_image_orientations(TET tet, int doc, int pageno, int pageid)
	throws com.pdflib.TETException
 {
        if (tet.get_image_info(pageid) == 1) {
            System.out.println("Images on page " + pageno + ":");

            int image_count = 0;
            do {
                image_count += 1;
                
                System.out.println("\tImage " + image_count + ": ");
                System.out.println("\t\tx=" + tet.x + " y=" + tet.y + " width="
                    + tet.width + " height=" + tet.height);
                System.out
                    .println("\t\talpha=" + tet.alpha + " beta=" + tet.beta);
                
                if (Math.abs(tet.alpha) < EPSILON) {
                    if (Math.abs(tet.beta) < EPSILON) {
                        /* alpha == 0 && beta == 0 */
                        System.out.println("\t\tImage is upright.");
                    }
                    else if (Math.abs(tet.beta - 180) < EPSILON) {
                        /* alpha == 0 && beta == 180 */
                        System.out.println("\t\tImage is mirrored on x axis.");
                        System.out.println("\t\tMirror image on x axis.");
                    }
                    else {
                        System.out.println("\t\tImage skewed by odd angle.");
                    }
                }
                else if (Math.abs(tet.alpha - 180) < EPSILON) {
                    if (Math.abs(tet.beta) < EPSILON) {
                        /* alpha == 180 && beta == 0 */
                        System.out.println("\t\tImage is rotated.");
                        System.out.println("\t\tRotate image by 180 degrees.");
                    }
                    else if (Math.abs(tet.beta - 180) < EPSILON) {
                        /* alpha == 180 && beta == 180 */
                        System.out.println(
                            "\t\tImage is rotated and mirrored on x axis.");
                        System.out.println(
                            "\t\tMirror image on x axis and rotate by 180 degrees.");
                    }
                    else {
                        System.out.println("\t\tImage skewed by odd angle.");
                    }
                }
                else if (Math.abs(tet.alpha + 90) < EPSILON) {
                    if (Math.abs(tet.beta) < EPSILON) {
                        /* alpha == -90 && beta == 0 */
                        System.out.println("\t\tImage is rotated.");
                        System.out.println(
                            "\t\tRotate image clockwise by 90 degrees.");
                    }
                    else if (Math.abs(tet.beta - 180) < EPSILON) {
                        /* alpha == -90 && beta == 180 */
                        System.out.println(
                            "\t\tImage is rotated and mirrored on x axis.");
                        System.out.println(
                            "\t\tMirror image on x axis and rotate clockwise by 90 degrees.");
                    }
                    else {
                        System.out.println("\t\tImage skewed by odd angle");
                    }
                }
                else if (Math.abs(tet.alpha - 90) < EPSILON) {
                    if (Math.abs(tet.beta) < EPSILON) {
                        /* alpha == 90 && beta == 0 */
                        System.out.println("\t\tImage is rotated.");
                        System.out.println(
                            "\t\tRotate image counterclockwise by 90 degrees.");
                    }
                    else if (Math.abs(tet.beta - 180) < EPSILON) {
                        /* alpha == 90 && beta == 180 */
                        System.out.println(
                            "\t\tImage is rotated and mirrored on x axis.");
                        System.out.println(
                            "\t\tMirror image on x axis and rotate counterclockwise by 90 degrees.");
                    }
                    else {
                        System.out.println("\t\tImage skewed by odd angle");
                    }
                }
                else {
                    System.out.println("\t\tImage rotated by odd angle");
                }
            }
            while (tet.get_image_info(pageid) == 1);
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
    private static void print_tet_error(TET tet, int pageno)
    {
        System.err.println("Error " + tet.get_errnum() + " in  "
                + tet.get_apiname() + "() on page " + pageno + ": "
                + tet.get_errmsg());
    }
}
