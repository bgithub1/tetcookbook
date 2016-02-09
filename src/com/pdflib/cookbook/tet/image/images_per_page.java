package com.pdflib.cookbook.tet.image;

import com.pdflib.TETException;
import com.pdflib.TET;

/**
 * PDF image extractor based on PDFlib TET
 *
 * <p>
 * Required software: TET 5
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: images_per_page.java,v 1.4 2015/12/03 09:19:50 stm Exp $
 */
public class images_per_page
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
     * Page-specific option list, e.g.
     * "imageanalysis={merge={gap=1} smallimages={maxwidth=20}}
     */
    static final String pageoptlist = "";

    public static void main (String argv[])
    {
        TET tet = null;
	int pageno = 0;
        
	try
        {
	    if (argv.length != 1)
            {
                throw new Exception(
		    "usage: images_per_page <infilename>");
            }
	    String outfilebase = argv[0];
	    if (outfilebase.substring(outfilebase.length() - 4).equalsIgnoreCase(".pdf")) {
		outfilebase = outfilebase.substring(0, outfilebase.length() - 4);
	    }

            tet = new TET();

            tet.set_option(globaloptlist);

            int doc = tet.open_document(argv[0], docoptlist);

            if (doc == -1)
            {
                throw new Exception("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }
            
            /* Get number of pages in the document */
            int n_pages = (int) tet.pcos_get_number(doc, "length:pages");

            /* Loop over pages and extract images  */
            for (pageno = 1; pageno <= n_pages; ++pageno)
            {
                int page;
		int imagecount = 0;
		
		page = tet.open_page(doc, pageno, pageoptlist);

                if (page == -1)
                {
                    print_tet_error(tet, pageno);
                    continue; /* try next page */
                }

                /*
                 * Retrieve all images on the page 
		 */
                while ((tet.get_image_info(page)) == 1)
                {
		    String imageoptlist;
		    int maskid;

		    imagecount++;

		    /* Report image details: pixel geometry, color space etc. */
		    report_image_info(tet, doc, tet.imageid);

		    /* Report placement geometry */
		    System.out.println("  placed on page " + pageno + 
		    " at position (" + String.format("%g", tet.x) + ", " + String.format("%g", tet.y) + ": " + 
			(int) tet.width + "x" +
			(int) tet.height + "pt, alpha=" + 
			tet.alpha + ", beta=" + 
			tet.beta + ")");
		    /* Write image data to file */
		    imageoptlist = "filename={" + outfilebase + "_p" + pageno + "_" + imagecount + "_I" + tet.imageid + "}";

		    if (tet.write_image_file(doc, tet.imageid, imageoptlist) == -1){
			System.out.println("\nError [" + tet.get_errnum()+ 
			" in " + tet.get_apiname() + "(): " +tet.get_errmsg());
			continue; /* try next image */
		    }

		    /* Check whether the image has a mask attached... */
		    maskid = (int) tet.pcos_get_number(doc, 
			    "images[" + tet.imageid + "]/maskid");

		    /* and retrieve it if present */
		    if (maskid != -1){
			System.out.print("  masked with ");
			report_image_info(tet, doc, maskid);

			imageoptlist = "filename={" + outfilebase + "_p" + pageno + "_" + imagecount + "_I" + tet.imageid + "mask_I" + maskid +"}";

			if (tet.write_image_file(doc, tet.imageid, imageoptlist) == -1){
			    System.out.println("\nError [" + tet.get_errnum() + 
				" in " + tet.get_apiname() + 
				"() for mask image: " + tet.get_errmsg());
			    continue; /* try next image */
			}
		    }

		    if (tet.get_errnum() != 0)
		    {
			print_tet_error(tet, pageno);
		    }
		}
                
		tet.close_page(page);
	    }
	    tet.close_document(doc);
	}
	catch (TETException e)
	{
	    if (pageno == 0) {
		System.err.println("Error " + e.get_errnum() + " in " + 
		    e.get_apiname() + "(): " + e.get_errmsg());
	    }
	    else {
		System.err.println("Error " + e.get_errnum() + " in " + 
		    e.get_apiname() + "() on page " + pageno + ": " + 
		    e.get_errmsg());
	    }
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
     * Report a TET error.
     * 
     * @param tet The TET object
     * @param pageno The page number on which the error occurred
     */
    private static void print_tet_error(TET tet, int pageno)
    {
        System.err.println("Error " + tet.get_errnum() + " in  "
                + tet.get_apiname() + " () on page " + pageno + ": "
                + tet.get_errmsg());
    }

    /* Print the following information for each image:
     * - pCOS id (required for indexing the images[] array)
     * - pixel size of the underlying PDF Image XObject
     * - number of components, bits per component, and colorspace
     * - mergetype if different from "normal", i.e. "artificial" (=merged)
     *   or "consumed"
     * - "stencilmask" property, i.e. /ImageMask in PDF
     */
    private static void report_image_info(TET tet, int doc, int imageid) throws com.pdflib.TETException {
	int width, height, bpc, cs, components, mergetype, stencilmask;
	String csname;

	width = (int) tet.pcos_get_number(doc, 
	    "images[" + imageid + "]/Width");
	height = (int) tet.pcos_get_number(doc, 
	    "images[" + imageid + "]/Height");
	bpc = (int) tet.pcos_get_number(doc, 
	    "images[" + imageid + "]/bpc");
	cs = (int) tet.pcos_get_number(doc, 
	    "images[" + imageid + "]/colorspaceid");
	components = (int) tet.pcos_get_number(doc, 
	    "colorspaces[" + cs + "]/components");

	csname = tet.pcos_get_string(doc, 
	    "colorspaces[" + cs + "]/name");

	System.out.print("image " + imageid + ": " + width + "x" + height +
	    " pixel, " + components + "x" + bpc + " bit " + csname);

	if (csname.equals("Indexed")) {
	    int basecs = 0;
	    String basecsname;
	    basecs = (int) tet.pcos_get_number(doc, 
		"colorspaces[" + cs + "]/baseid");
	    basecsname = tet.pcos_get_string(doc, 
		"colorspaces[" + basecs + "]/name");
	    System.out.print(" " + basecsname);
	}

	/* Check whether this image has been created by merging smaller images*/
	mergetype = (int) tet.pcos_get_number(doc, 
	    "images[" + imageid + "]/mergetype");
	if (mergetype == 1)
	    System.out.print(", mergetype=artificial");

	stencilmask = (int) tet.pcos_get_number(doc, 
	    "images[" + imageid + "]/stencilmask");
	if (stencilmask == 1)
	    System.out.print(", used as stencil mask");
	System.out.println("");
    }
}
