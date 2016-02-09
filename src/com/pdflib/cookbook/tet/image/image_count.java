package com.pdflib.cookbook.tet.image;

import com.pdflib.TETException;
import com.pdflib.TET;

/**
 * Count images in a PDF according to various interpretations.
 * <p>
 * Required software: TET 4
 * <p>
 * Required data: PDF document
 *
 * @version $Id: image_count.java,v 1.2 2014/05/26 13:02:11 rjs Exp $
 */
public class image_count
{
    /**
     * Global option list
     */
    static final String globaloptlist = "searchpath={{../input}} ";
    
    /**
     * Document-specific option list
     */
    static final String docoptlist = "";
    
    /**
     * Page-specific option list
     */
    static final String pageoptlist = "";
    
    /**
     * Here you can insert basic image extract options (more below)
     */
    static final String baseimageoptlist = "";

    public static void main (String argv[])
    {
        TET tet = null;
        
	try
        {
	    /* image counts for normal, artificial, and consumed images */
	    int stats[] = {0, 0, 0};

	    if (argv.length != 1)
            {
                throw new Exception("usage: image_count <filename>");
            }

            tet = new TET();

            tet.set_option(globaloptlist);

            int doc = tet.open_document(argv[0], docoptlist);

            if (doc == -1)
            {
                throw new Exception("Error " + tet.get_errnum() + "in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }

            int n_images = (int) tet.pcos_get_number(doc, "length:images");
	    System.out.println("No of raw image resources before merging: "
	    	+ n_images);

            /* get number of pages in the document */
            int n_pages = (int) tet.pcos_get_number(doc, "length:pages");

            /* loop over pages in the document (triggers image merging) */
            int placed_images = 0;
            for (int pageno = 1; pageno <= n_pages; ++pageno)
            {
                int page;
		
		page = tet.open_page(doc, pageno, pageoptlist);

                if (page == -1)
                {
                    print_tet_error(tet, pageno);
                    continue;		/* try next page */
                }

                if (tet.get_errnum() != 0)
                {
                    print_tet_error(tet, pageno);
                }

                /* Retrieve all images on the page */
                while (tet.get_image_info(page) == 1)
                {
		    placed_images++;
		}
                tet.close_page(page);
            }

	    System.out.println("No of placed images: " + placed_images);

            n_images = (int) tet.pcos_get_number(doc, "length:images");
	    System.out.println("No of images after merging (all types): "
	    	+ n_images);

            /* loop over image resources in the document */
	    int image_resources = 0;
	    for (int imageid = 0; imageid < n_images; imageid++)
	    {
		int mergetype = (int) tet.pcos_get_number(doc, 
			    "images["+ imageid + "]/mergetype");

		stats[mergetype]++;

		if (mergetype == 0 || mergetype == 1)
		    image_resources++;
	    }

	    System.out.println("  normal images: "
	    	+ stats[0]);
	    System.out.println("  artificial (merged) images: "
	    	+ stats[1]);
	    System.out.println("  consumed images: "
	    	+ stats[2]);

	    System.out.println(
	    	"No of relevant (normal or artificial) image resources: "
	    	+ image_resources);

            tet.close_document(doc);
        }
	catch (TETException e)
	{
	    System.err.println(
		"TET exception occurred in image_resources sample:");
	    System.err.println("[" + e.get_errnum() + "] " + e.get_apiname() +
			    ": " + e.get_errmsg());
	    System.exit(1);
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
	    System.exit(1);
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
