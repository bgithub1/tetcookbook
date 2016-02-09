package com.pdflib.cookbook.tet.image;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Resource-based image extractor based on PDFlib TET
 * <p>
 * Required software: TET 5
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: image_resources.java,v 1.5 2015/12/15 08:59:08 stm Exp $
 */
public class image_resources
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

    public static void main (String argv[])
    {
        TET tet = null;
        
	try
        {
	    if (argv.length != 1)
            {
                throw new Exception("usage: image_resources <filename>");
            }

            String outfilebase = argv[0];
            if (outfilebase.substring(outfilebase.length()-4).equalsIgnoreCase(".pdf"))
                outfilebase = outfilebase.substring(0, outfilebase.length()-4);
            
            tet = new TET();

            tet.set_option(globaloptlist);

            int doc = tet.open_document(argv[0], docoptlist);

            if (doc == -1)
            {
                throw new Exception("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }
            
	    /* 
	     * Images will only be merged upon opening a page.
	     * In order to enumerate all merged image resources
	     * we open all pages before extracting the images.
	     */

            /* Get number of pages in the document */
            int n_pages = (int) tet.pcos_get_number(doc, "length:pages");

            /* Loop over pages to trigger image merging */
            for (int pageno = 1; pageno <= n_pages; ++pageno)
            {
                int page = tet.open_page(doc, pageno, pageoptlist);

                if (page == -1)
                {
                    print_tet_error(tet, pageno);
                    continue;		/* process  next page */
                }

                if (tet.get_errnum() != 0)
                {
                    print_tet_error(tet, pageno);
                }

                tet.close_page(page);
            }

	    /* Get the number of pages in the document  */
            int n_images = (int) tet.pcos_get_number(doc, "length:images");

            /* Loop over image resources */
	    for (int imageid = 0; imageid < n_images; imageid++)
	    {
		String imageoptlist;
		/* Skip images which hve been consumed by merging */
		int mergetype = (int) tet.pcos_get_number(doc, 
			    "images["+ imageid + "]/mergetype");

		/* skip images which have been consumed by merging */
		if (mergetype == 2)
		    continue;
		/* Skip small images (see "smallimages" option) */
		if (tet.pcos_get_number(doc, 
		    "images["+ imageid + "]/small") == 1) 
		    continue;

		/* Report image details: pixel geometry, color space etc. */
		report_image_info(tet, doc, imageid);

		/* Write image data to file */
		imageoptlist = "filename={" + outfilebase + "_I" + imageid + "}";

		if (tet.write_image_file(doc, imageid, imageoptlist) == -1){
		    System.out.println("\nError [" + tet.get_errnum()+
			" in " + tet.get_apiname() + "(): " +tet.get_errmsg());
		    continue; /* try next image */
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
     * Report image info.
     * 
     * Print the following information for each image:
     * 
     * - page and image number
     * - pCOS id (required for indexing the images[] array)
     * - physical size of the placed image on the page
     * - pixel size of the underlying PDF image
     * - number of components, bits per component,and colorspace
     * - mergetype if different from "normal", i.e. "artificial" (=merged)
     *   or "consumed"
     *   
     * @param tet The TET object
     * @param doc The document handle 
     * @param imageid The image ID
     */
    private static void report_image_info(TET tet, int doc, int imageid)
	throws com.pdflib.TETException
    {
	int width, height, bpc, cs, components, mergetype, stencilmask, maskid;
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

	System.out.print("image " + imageid);  
	System.out.print(": " + width + "x" + height + " pixel, ");

        csname = tet.pcos_get_string(doc,
            "colorspaces[" + cs + "]/name");

        System.out.print( components + "x" + bpc + " bit " + csname);

        if (csname.equals("Indexed")){
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

        /* Check whether the image has an attached mask */
	maskid = (int) tet.pcos_get_number(doc,
            "images[" + imageid + "]/maskid");
        if (maskid != -1)
            System.out.print(", masked with image " + maskid);

        System.out.println("");
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
                + tet.get_apiname() + "() on page " + pageno + ": "
                + tet.get_errmsg());
    }
}
